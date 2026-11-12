import { createHash, randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import { createTransactionApi } from "../src/actual/transaction-api.js";
import {
  createTransactionRevision,
  type TransactionCreateInput,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import {
  TransactionReconciler,
  type ActualBudgetSessionPort,
} from "../src/actual/transaction-reconciler.js";
import type {
  ActualTransactionStore,
  TransactionCreateValidationIssue,
} from "../src/actual/transaction-store.js";
import { buildApiApp } from "../src/app.js";
import { createPostgresDatabase, type Database } from "../src/database.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";
import type {
  JsonValue,
  WriteOperationInput,
} from "../src/write-journal/contracts.js";

const accessToken = "A".repeat(43);
const ownerDeviceId = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10";
const ownerDevice: DeviceRecord = {
  deviceId: ownerDeviceId,
  principalId: "uncertain-recovery-owner",
  name: "Uncertain recovery test device",
  budgetIds: [],
  createdAt: new Date("2026-11-01T00:00:00.000Z"),
  lastSeenAt: null,
  revokedAt: null,
};

let database: Database;
let adminPool: Pool;
let inspectionPool: Pool;
let isolatedSchema: string;

function createBaseSnapshot(
  id: string,
  overrides: Partial<TransactionSnapshot> = {},
): TransactionSnapshot {
  return {
    id,
    account: "checking-account",
    amount: -1500,
    date: "2026-11-01",
    payee: null,
    category: null,
    notes: null,
    cleared: false,
    reconciled: false,
    importedId: null,
    importedPayee: null,
    startingBalance: null,
    schedule: null,
    transferId: null,
    isParent: false,
    isChild: false,
    parentId: null,
    subtransactions: [],
    ...overrides,
  };
}

function actualRecordFromSnapshot(
  snapshot: TransactionSnapshot,
): Record<string, unknown> {
  return {
    id: snapshot.id,
    account: snapshot.account,
    amount: snapshot.amount,
    date: snapshot.date,
    payee: snapshot.payee,
    category: snapshot.category,
    notes: snapshot.notes,
    cleared: snapshot.cleared,
    reconciled: snapshot.reconciled,
    imported_id: snapshot.importedId,
    imported_payee: snapshot.importedPayee,
    starting_balance_flag: snapshot.startingBalance,
    schedule: snapshot.schedule,
    transfer_id: snapshot.transferId,
    is_parent: snapshot.isParent,
    is_child: snapshot.isChild,
    parent_id: snapshot.parentId,
    subtransactions: snapshot.subtransactions.map(actualRecordFromSnapshot),
  };
}

function createWriteInput(
  base: TransactionSnapshot,
  budgetId: string,
  patch: { [key: string]: JsonValue } = { amount: -1200 },
): WriteOperationInput {
  return {
    operationId: randomUUID(),
    principalId: ownerDevice.principalId,
    budgetId,
    idempotencyKey: randomUUID(),
    entityType: "transaction",
    action: "update",
    entityId: base.id,
    baseRevision: createTransactionRevision(base),
    baseSnapshot: base as unknown as JsonValue,
    patch,
  };
}

function createCreateInput(
  budgetId: string,
  transactionId: string,
  patch: TransactionCreateInput,
): WriteOperationInput {
  return {
    operationId: randomUUID(),
    principalId: ownerDevice.principalId,
    budgetId,
    idempotencyKey: randomUUID(),
    entityType: "transaction",
    action: "create",
    entityId: transactionId,
    baseRevision: null,
    baseSnapshot: {},
    patch: patch as unknown as { [key: string]: JsonValue },
  };
}

function createLegacySnapshotRevision(
  snapshot: Record<string, unknown>,
): string {
  return createHash("sha256").update(JSON.stringify(snapshot)).digest("hex");
}

function createActualRecordForCreate(
  transactionId: string,
  input: TransactionCreateInput,
  overrides: Partial<Record<string, unknown>> = {},
): Record<string, unknown> {
  return {
    id: `actual-${randomUUID()}`,
    account: input.account,
    amount: input.amount,
    date: input.date,
    payee: input.payee,
    category: input.category,
    notes: input.notes,
    cleared: input.cleared,
    reconciled: false,
    imported_id: transactionId,
    is_parent: false,
    is_child: false,
    parent_id: null,
    ...overrides,
  };
}

async function createRecoveryApp(
  input: WriteOperationInput,
  base: TransactionSnapshot,
  writes: { count: number },
  options: {
    initialActual?: Record<string, unknown>;
    updateValidationIssue?: TransactionCreateValidationIssue;
    readError?: Error;
  } = {},
) {
  let actual: Record<string, unknown> | undefined =
    options.initialActual ?? actualRecordFromSnapshot(base);
  const transactions: ActualTransactionStore = {
    async read(id) {
      if (options.readError) throw options.readError;
      if (id !== base.id && id !== input.entityId) return undefined;
      return actual ? { ...actual } : undefined;
    },
    async validateCreateInput() {
      return undefined;
    },
    async validateUpdateInput() {
      return options.updateValidationIssue;
    },
    async create(id, createInput) {
      writes.count += 1;
      actual = createActualRecordForCreate(id, createInput);
      return { ...actual };
    },
    async update(id, fields) {
      if (id !== base.id && id !== input.entityId) {
        throw new Error("Unexpected transaction ID");
      }
      if (!actual) throw new Error("Transaction is absent");
      writes.count += 1;
      actual = { ...actual, ...fields };
      return { ...actual };
    },
    async delete(id) {
      if (id !== base.id && id !== input.entityId) {
        throw new Error("Unexpected transaction ID");
      }
      actual = undefined;
      return undefined;
    },
  };
  const session: ActualBudgetSessionPort = {
    async runBudget<T>(_budgetId: string, work: () => Promise<T>) {
      return work();
    },
  };
  const reconciler = new TransactionReconciler(
    database,
    database.journal,
    session,
    transactions,
  );
  const transactionApi = createTransactionApi(
    session,
    transactions,
    reconciler,
    database.journal,
  );
  const deviceAuth = {
    async authenticate(token?: string) {
      return token === accessToken
        ? { ...ownerDevice, budgetIds: [input.budgetId] }
        : undefined;
    },
  } as unknown as DeviceAuthorization;

  const app = await buildApiApp(
    {
      async ping() {},
      async close() {},
    },
    { deviceAuth, transactionApi },
  );
  return { app, transactions };
}

async function expireClaimedLease(
  input: WriteOperationInput,
  beforeExpiry?: () => Promise<void>,
) {
  await database.journal.enqueue(input);
  expect((await database.journal.claim(input.operationId)).claimed).toBe(true);
  await beforeExpiry?.();
  await inspectionPool.query(
    `UPDATE tandem_operations
     SET lease_expires_at = clock_timestamp() - interval '1 second'
     WHERE operation_id = $1`,
    [input.operationId],
  );
  expect(await database.journal.expireLeases()).toBe(1);
  await expect(database.journal.get(input.operationId)).resolves.toMatchObject({
    status: "uncertain",
    errorCode: "claim-lease-expired",
  });
}

function statusRequest(operationId: string) {
  return {
    method: "GET" as const,
    url: `/api/v1/operations/${operationId}`,
    headers: { authorization: `Bearer ${accessToken}` },
  };
}

function discardRequest(operationId: string) {
  return {
    method: "POST" as const,
    url: `/api/v1/operations/${operationId}/discard`,
    headers: { authorization: `Bearer ${accessToken}` },
  };
}

function retryRequest(input: WriteOperationInput) {
  const transactionsUrl = `/api/v1/budgets/${input.budgetId}/transactions`;
  return {
    method: "POST" as const,
    url: `${transactionsUrl}/${input.entityId}/operations`,
    headers: {
      authorization: `Bearer ${accessToken}`,
      "idempotency-key": input.idempotencyKey,
    },
    payload: {
      operationId: input.operationId,
      action: "update",
      baseRevision: input.baseRevision,
      baseSnapshot: input.baseSnapshot,
      patch: input.patch,
    },
  };
}

function retryCreateRequest(input: WriteOperationInput) {
  const transactionsUrl = `/api/v1/budgets/${input.budgetId}/transactions`;
  return {
    method: "POST" as const,
    url: `${transactionsUrl}/${encodeURIComponent(input.entityId ?? "")}/operations`,
    headers: {
      authorization: `Bearer ${accessToken}`,
      "idempotency-key": input.idempotencyKey,
    },
    payload: {
      operationId: input.operationId,
      action: "create",
      patch: input.patch,
    },
  };
}

describe("uncertain transaction recovery through the API", () => {
  beforeAll(async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error("DATABASE_URL is required to test lease recovery");
    }
    isolatedSchema = `uncertain_recovery_${randomUUID().replaceAll("-", "")}`;
    adminPool = new Pool({ connectionString, max: 2 });
    await adminPool.query(`CREATE SCHEMA ${isolatedSchema}`);
    const searchPath = `-c search_path=${isolatedSchema}`;
    const isolatedConnection = new URL(connectionString);
    isolatedConnection.searchParams.set("options", searchPath);
    database = createPostgresDatabase(
      isolatedConnection.toString(),
      process.env.AUTH_TOKEN_ENCRYPTION_KEY ??
        "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c",
    );
    inspectionPool = new Pool({
      connectionString,
      max: 2,
      options: searchPath,
    });
    await database.ensureSchema();
  }, 30_000);

  afterAll(async () => {
    await inspectionPool?.end();
    await database?.close();
    if (isolatedSchema) {
      await adminPool?.query(`DROP SCHEMA ${isolatedSchema} CASCADE`);
    }
    await adminPool?.end();
  });

  it.each([
    {
      caseName: "readback finds the write applied",
      writeAppliedBeforeRecovery: true,
      checkedStatus: "committed",
    },
    {
      caseName: "readback finds the write was not applied",
      writeAppliedBeforeRecovery: false,
      checkedStatus: "queued",
    },
  ])(
    "recovers lease-expired updates when $caseName",
    async ({ writeAppliedBeforeRecovery, checkedStatus }) => {
      const base = createBaseSnapshot(`recovery-${randomUUID()}`);
      const budgetId = `recovery-budget-${randomUUID()}`;
      const input = createWriteInput(base, budgetId);
      const writes = { count: 0 };
      const { app, transactions } = await createRecoveryApp(
        input,
        base,
        writes,
      );

      try {
        await expireClaimedLease(
          input,
          writeAppliedBeforeRecovery
            ? async () => {
                await transactions.update(base.id, { amount: -1200 });
              }
            : undefined,
        );
        const status = await app.inject(statusRequest(input.operationId));

        expect(status.statusCode).toBe(200);
        expect(status.json().operation.status).toBe(checkedStatus);
        expect(writes.count).toBe(writeAppliedBeforeRecovery ? 1 : 0);
        if (checkedStatus === "committed") {
          expect(status.json().operation.actualResult).toMatchObject({
            transactionId: base.id,
            recoveredByReadback: true,
          });
        } else {
          expect(status.json().operation).toMatchObject({
            status: "queued",
            errorCode: "actual-readback-not-applied",
          });
        }
        const retry = await app.inject(retryRequest(input));

        expect(retry.statusCode).toBe(200);
        expect(retry.json().operation.status).toBe("committed");
        expect(writes.count).toBe(1);
        await expect(
          database.journal.get(input.operationId),
        ).resolves.toMatchObject({ status: "committed" });
      } finally {
        await app.close();
      }
    },
    30_000,
  );

  it("keeps a lease-expired update uncertain when read-back finds a third value", async () => {
    const base = createBaseSnapshot(`recovery-${randomUUID()}`);
    const budgetId = `recovery-budget-${randomUUID()}`;
    const input = createWriteInput(base, budgetId);
    const writes = { count: 0 };
    const { app, transactions } = await createRecoveryApp(input, base, writes);

    try {
      await expireClaimedLease(input, async () => {
        await transactions.update(base.id, { amount: -1300 });
      });

      const status = await app.inject(statusRequest(input.operationId));

      expect(status.statusCode).toBe(200);
      expect(status.json().operation).toMatchObject({
        status: "uncertain",
        errorCode: "claim-lease-expired",
      });
      expect(writes.count).toBe(1);
      await expect(transactions.read(base.id)).resolves.toMatchObject({
        amount: -1300,
      });

      const retry = await app.inject(retryRequest(input));

      expect(retry.statusCode).toBe(200);
      expect(retry.json().operation.status).toBe("uncertain");
      expect(writes.count).toBe(1);
      await expect(transactions.read(base.id)).resolves.toMatchObject({
        amount: -1300,
      });
    } finally {
      await app.close();
    }
  }, 30_000);

  it("removes an uncertain edit locally only after the server confirms discard", async () => {
    const base = createBaseSnapshot(`recovery-${randomUUID()}`);
    const budgetId = `recovery-budget-${randomUUID()}`;
    const input = createWriteInput(base, budgetId);
    const writes = { count: 0 };
    const { app, transactions } = await createRecoveryApp(input, base, writes);

    try {
      await expireClaimedLease(input, async () => {
        await transactions.update(base.id, { amount: -1300 });
      });

      const checked = await app.inject(statusRequest(input.operationId));
      expect(checked.statusCode).toBe(200);
      expect(checked.json().operation.status).toBe("uncertain");

      const discarded = await app.inject(discardRequest(input.operationId));
      expect(discarded.statusCode).toBe(200);
      expect(discarded.json().operation).toMatchObject({
        status: "rejected",
        errorCode: "discarded-by-user",
      });
      await expect(
        database.journal.get(input.operationId),
      ).resolves.toMatchObject({
        status: "rejected",
        errorCode: "discarded-by-user",
      });
      await expect(transactions.read(base.id)).resolves.toMatchObject({
        amount: -1300,
      });
      expect(writes.count).toBe(1);
    } finally {
      await app.close();
    }
  }, 30_000);

  it("does not return a settled status when the server cannot read Actual", async () => {
    const base = createBaseSnapshot(`recovery-${randomUUID()}`);
    const budgetId = `recovery-budget-${randomUUID()}`;
    const input = createWriteInput(base, budgetId);
    const writes = { count: 0 };
    const { app } = await createRecoveryApp(input, base, writes, {
      readError: new Error("Actual is temporarily unavailable"),
    });

    try {
      await expireClaimedLease(input);

      const checked = await app.inject(statusRequest(input.operationId));

      expect(checked.statusCode).toBe(502);
      expect(checked.body).not.toContain("Actual is temporarily unavailable");
      await expect(
        database.journal.get(input.operationId),
      ).resolves.toMatchObject({
        status: "uncertain",
        errorCode: "claim-lease-expired",
      });
    } finally {
      await app.close();
    }
  }, 30_000);

  it.each([
    ["account", { account: "other-account" }],
    ["date", { date: "2026-11-02" }],
    ["cleared", { cleared: true }],
    ["reconciled", { reconciled: true }],
  ] as const)(
    "rejects a split-child %s edit through the API without recording a conflict",
    async (_field, patch) => {
      const base = createBaseSnapshot(`split-child-${randomUUID()}`, {
        isChild: true,
        parentId: `split-parent-${randomUUID()}`,
      });
      const budgetId = `split-child-budget-${randomUUID()}`;
      const input = createWriteInput(base, budgetId, patch);
      const writes = { count: 0 };
      const { app, transactions } = await createRecoveryApp(
        input,
        base,
        writes,
      );

      try {
        const response = await app.inject(retryRequest(input));

        expect(response.statusCode).toBe(400);
        expect(response.json().error).toMatchObject({
          code: "split-child-field-not-editable",
        });
        expect(writes.count).toBe(0);
        await expect(
          database.journal.get(input.operationId),
        ).resolves.toMatchObject({
          status: "rejected",
          errorCode: "split-child-field-not-editable",
        });
        const status = await app.inject(statusRequest(input.operationId));
        expect(status.statusCode).toBe(200);
        expect(status.json()).toMatchObject({
          operation: { status: "rejected" },
          conflict: null,
        });
        await expect(transactions.read(base.id)).resolves.toMatchObject({
          is_child: true,
          parent_id: base.parentId,
        });
      } finally {
        await app.close();
      }
    },
  );

  it("accepts a previous transaction snapshot format through the API", async () => {
    const base = createBaseSnapshot(`legacy-snapshot-${randomUUID()}`);
    const budgetId = `legacy-snapshot-budget-${randomUUID()}`;
    const legacySnapshot = { ...base } as Record<string, unknown>;
    delete legacySnapshot.reconciled;
    const input = {
      ...createWriteInput(base, budgetId),
      baseRevision: createLegacySnapshotRevision(legacySnapshot),
      baseSnapshot: legacySnapshot as JsonValue,
    };
    const writes = { count: 0 };
    const { app, transactions } = await createRecoveryApp(input, base, writes);

    try {
      const response = await app.inject(retryRequest(input));

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        operation: { status: "committed", errorCode: null },
        conflict: null,
      });
      expect(writes.count).toBe(1);
      await expect(transactions.read(base.id)).resolves.toMatchObject({
        amount: -1200,
      });
      await expect(
        database.journal.get(input.operationId),
      ).resolves.toMatchObject({
        status: "committed",
        baseSnapshot: legacySnapshot,
      });
    } finally {
      await app.close();
    }
  });

  it.each([
    {
      actualAmount: -1500,
      expectedStatus: "committed",
      expectedConflictFields: null,
    },
    {
      actualAmount: -1300,
      expectedStatus: "conflict",
      expectedConflictFields: ["amount"],
    },
  ])(
    "settles a retried uncertain create as $expectedStatus when Actual has $actualAmount",
    async ({ actualAmount, expectedStatus, expectedConflictFields }) => {
      const transactionId = `tandem:${randomUUID()}`;
      const budgetId = `uncertain-create-budget-${randomUUID()}`;
      const createPatch: TransactionCreateInput = {
        account: "checking-account",
        amount: -1500,
        date: "2026-11-01",
        payee: "market-payee",
        category: "groceries-category",
        notes: "Household market",
        cleared: false,
      };
      const input = createCreateInput(budgetId, transactionId, createPatch);
      const actualRecord = createActualRecordForCreate(
        transactionId,
        createPatch,
        { amount: actualAmount },
      );
      const base = createBaseSnapshot(String(actualRecord.id), {
        account: createPatch.account,
        amount: actualAmount,
        date: createPatch.date,
        payee: createPatch.payee,
        category: createPatch.category,
        notes: createPatch.notes,
        cleared: createPatch.cleared,
        importedId: transactionId,
      });
      const writes = { count: 0 };
      const { app } = await createRecoveryApp(input, base, writes, {
        initialActual: actualRecord,
      });

      try {
        await database.journal.enqueue(input);
        expect((await database.journal.claim(input.operationId)).claimed).toBe(
          true,
        );
        await database.journal.markUncertain(
          input.operationId,
          "actual-outcome-uncertain",
          {
            kind: "transaction-create-returned",
          },
        );

        const response = await app.inject(retryCreateRequest(input));

        expect(response.statusCode).toBe(200);
        expect(response.json().operation.status).toBe(expectedStatus);
        expect(response.json().conflict?.conflictingFields ?? null).toEqual(
          expectedConflictFields,
        );
        expect(writes.count).toBe(0);
        await expect(
          database.journal.get(input.operationId),
        ).resolves.toMatchObject({
          status: expectedStatus,
        });
      } finally {
        await app.close();
      }
    },
  );

  it.each([
    {
      field: "category",
      patch: { category: "deleted-category" },
      validationIssue: {
        code: "category-not-found",
        message: "Category does not exist in this budget",
      },
    },
    {
      field: "payee",
      patch: { payee: "removed-payee" },
      validationIssue: {
        code: "payee-not-found",
        message: "Payee does not exist in this budget",
      },
    },
    {
      field: "account",
      patch: { account: "closed-account" },
      validationIssue: {
        code: "account-closed",
        message: "Choose an open account for this transaction",
      },
    },
  ] as const)(
    "rejects an update that refers to a removed $field before writing",
    async ({ patch, validationIssue }) => {
      const base = createBaseSnapshot(`removed-reference-${randomUUID()}`);
      const budgetId = `removed-reference-budget-${randomUUID()}`;
      const input = createWriteInput(base, budgetId, patch);
      const writes = { count: 0 };
      const { app, transactions } = await createRecoveryApp(
        input,
        base,
        writes,
        { updateValidationIssue: validationIssue },
      );

      try {
        const response = await app.inject(retryRequest(input));

        expect(response.statusCode).toBe(400);
        expect(response.json().error).toMatchObject({
          code: validationIssue.code,
        });
        expect(writes.count).toBe(0);
        await expect(
          database.journal.get(input.operationId),
        ).resolves.toMatchObject({
          status: "rejected",
          errorCode: validationIssue.code,
        });
        await expect(transactions.read(base.id)).resolves.toMatchObject({
          account: base.account,
          category: base.category,
          payee: base.payee,
        });
      } finally {
        await app.close();
      }
    },
  );
});

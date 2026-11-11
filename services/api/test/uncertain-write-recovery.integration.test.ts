import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import { createTransactionApi } from "../src/actual/transaction-api.js";
import {
  createTransactionRevision,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import {
  TransactionReconciler,
  type ActualBudgetSessionPort,
} from "../src/actual/transaction-reconciler.js";
import type { ActualTransactionStore } from "../src/actual/transaction-store.js";
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

function createBaseSnapshot(id: string): TransactionSnapshot {
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
  };
}

function createWriteInput(
  base: TransactionSnapshot,
  budgetId: string,
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
    patch: { amount: -1200 },
  };
}

async function createRecoveryApp(
  input: WriteOperationInput,
  base: TransactionSnapshot,
  writes: { count: number },
) {
  let actual: Record<string, unknown> = { ...base };
  const transactions: ActualTransactionStore = {
    async read(id) {
      return id === base.id ? { ...actual } : undefined;
    },
    async validateCreateInput() {
      throw new Error("This test does not validate creates");
    },
    async validateUpdateInput() {
      return undefined;
    },
    async create() {
      throw new Error("This test does not create transactions");
    },
    async update(id, fields) {
      if (id !== base.id) throw new Error("Unexpected transaction ID");
      writes.count += 1;
      actual = { ...actual, ...fields };
      return { ...actual };
    },
    async delete() {
      throw new Error("This test does not delete transactions");
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
    await database.migrate();
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
});

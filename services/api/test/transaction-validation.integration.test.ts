import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { Pool } from "pg";
import type { Pool as PoolType } from "pg";
import { buildApiApp } from "../src/app.js";
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
import { createPostgresDatabase, type Database } from "../src/database.js";

let adminPool: PoolType | undefined;
let database: Database | undefined;
let schemaName: string | undefined;

const baseSnapshot: TransactionSnapshot = {
  id: "synthetic-transaction",
  account: "synthetic-account",
  amount: -1500,
  date: "2026-11-01",
  payee: null,
  category: null,
  notes: null,
  cleared: null,
  reconciled: null,
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

describe("transaction request validation integration", () => {
  beforeAll(async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }

    schemaName = `transaction_validation_${randomUUID().replaceAll("-", "")}`;
    adminPool = new Pool({ connectionString, max: 2 });
    await adminPool.query(`CREATE SCHEMA ${schemaName}`);
    const isolatedConnection = new URL(connectionString);
    isolatedConnection.searchParams.set(
      "options",
      `-c search_path=${schemaName}`,
    );
    database = createPostgresDatabase(
      isolatedConnection.toString(),
      process.env.AUTH_TOKEN_ENCRYPTION_KEY ??
        "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c",
    );
    await database.ensureSchema();
  }, 30_000);

  afterAll(async () => {
    await database?.close();
    if (schemaName) {
      await adminPool?.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
    await adminPool?.end();
  });

  it("returns 400 for invalid journal input without attempting an Actual write", async () => {
    const currentTransaction = { ...baseSnapshot };
    let actualReads = 0;
    let createAttempts = 0;
    let referenceIssue:
      | {
          code:
            | "account-not-found"
            | "account-closed"
            | "payee-not-found"
            | "transfer-account-closed"
            | "transfer-account-same-as-source"
            | "category-not-found"
            | "category-hidden";
          message: string;
        }
      | undefined;
    let updateReferenceIssue:
      | {
          code:
            | "account-not-found"
            | "account-closed"
            | "payee-not-found"
            | "transfer-account-closed"
            | "transfer-account-same-as-source"
            | "category-not-found"
            | "category-hidden";
          message: string;
        }
      | undefined;
    let updateAttempts = 0;
    const transactions: ActualTransactionStore = {
      async read(id: string) {
        actualReads += 1;
        return id.startsWith("tandem:") ? undefined : currentTransaction;
      },
      async validateCreateInput() {
        return referenceIssue;
      },
      async validateUpdateInput() {
        return updateReferenceIssue;
      },
      async create() {
        createAttempts += 1;
        throw new Error("Invalid requests must not create in Actual");
      },
      async update() {
        updateAttempts += 1;
        throw new Error("Invalid requests must not update Actual");
      },
      async delete() {
        throw new Error("Invalid requests must not delete from Actual");
      },
    };
    const session: ActualBudgetSessionPort = {
      async runBudget<T>(_syncId: string, operation: () => Promise<T>) {
        return operation();
      },
    };
    const reconciler = new TransactionReconciler(
      database!,
      database!.journal,
      session,
      transactions,
    );
    const transactionApi = createTransactionApi(
      session,
      transactions,
      reconciler,
      database!.journal,
    );
    const signedInDevice = await database!.deviceAuth.createDevice({
      principalId: `validation-owner-${randomUUID()}`,
      budgetIds: ["synthetic-budget"],
      deviceName: "Validation test device",
    });

    const app = await buildApiApp(
      { ping: async () => undefined, close: async () => undefined },
      { deviceAuth: database!.deviceAuth, transactionApi },
    );
    const report = vi.spyOn(console, "error").mockImplementation(() => {});
    const cases = [
      { idempotencyKey: "x".repeat(300), patch: { notes: "valid note" } },
      { idempotencyKey: "   ", patch: { notes: "valid note" } },
      { idempotencyKey: "valid-key", patch: { notes: "invalid\u0000note" } },
    ];

    try {
      for (const testCase of cases) {
        const operationId = randomUUID();
        const response = await app.inject({
          method: "POST",
          url: "/api/v1/budgets/synthetic-budget/transactions/synthetic-transaction/operations",
          headers: {
            authorization: `Bearer ${signedInDevice.accessToken}`,
            "idempotency-key": testCase.idempotencyKey,
          },
          payload: {
            operationId,
            action: "update",
            baseRevision: createTransactionRevision(baseSnapshot),
            baseSnapshot,
            patch: testCase.patch,
          },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toMatchObject({
          error: { code: "invalid_request" },
        });
        await expect(
          database!.journal.get(operationId),
        ).resolves.toBeUndefined();
      }

      expect(actualReads).toBe(0);

      const referenceIssues = [
        {
          code: "account-not-found",
          message: "Account does not exist in this budget",
        },
        {
          code: "account-closed",
          message: "Choose an open account for this transaction",
        },
        {
          code: "payee-not-found",
          message: "Payee does not exist in this budget",
        },
        {
          code: "transfer-account-closed",
          message: "Choose an open destination account for this transfer",
        },
        {
          code: "transfer-account-same-as-source",
          message: "Choose a different destination account for this transfer",
        },
        {
          code: "category-not-found",
          message: "Category does not exist in this budget",
        },
        {
          code: "category-hidden",
          message: "Choose a visible category for this transaction",
        },
      ] as const;
      for (const [index, issue] of referenceIssues.entries()) {
        referenceIssue = issue;
        const operationId = randomUUID();
        const createRequest = {
          method: "POST" as const,
          url: `/api/v1/budgets/synthetic-budget/transactions/tandem:${randomUUID()}/operations`,
          headers: {
            authorization: `Bearer ${signedInDevice.accessToken}`,
            "idempotency-key": `invalid-reference-${index}`,
          },
          payload: {
            operationId,
            action: "create",
            patch: {
              account: "missing-account",
              amount: -100,
              date: "2026-11-01",
              payee: null,
              category: null,
              notes: "Must be rejected before Actual write",
              cleared: false,
            },
          },
        };
        const rejectedCreate = await app.inject(createRequest);
        expect(rejectedCreate.statusCode).toBe(400);
        expect(rejectedCreate.json()).toMatchObject({
          error: { code: issue.code, message: issue.message },
        });
        await expect(database!.journal.get(operationId)).resolves.toMatchObject(
          { status: "rejected", errorCode: issue.code },
        );
        const replayedRejection = await app.inject(createRequest);
        expect(replayedRejection.statusCode).toBe(400);
        expect(replayedRejection.json()).toMatchObject({
          error: { code: issue.code, message: issue.message },
        });
      }
      expect(createAttempts).toBe(0);

      const updateReferenceCases = [
        {
          issue: {
            code: "account-closed",
            message: "Choose an open account for this transaction",
          },
          patch: { account: "closed-account" },
        },
        {
          issue: {
            code: "payee-not-found",
            message: "Payee does not exist in this budget",
          },
          patch: { payee: "removed-payee" },
        },
        {
          issue: {
            code: "transfer-account-closed",
            message: "Choose an open destination account for this transfer",
          },
          patch: { payee: "closed-transfer-payee" },
        },
        {
          issue: {
            code: "transfer-account-same-as-source",
            message: "Choose a different destination account for this transfer",
          },
          patch: { account: "self-transfer-target" },
        },
        {
          issue: {
            code: "category-not-found",
            message: "Category does not exist in this budget",
          },
          patch: { category: "removed-category" },
        },
        {
          issue: {
            code: "category-hidden",
            message: "Choose a visible category for this transaction",
          },
          patch: { category: "hidden-category" },
        },
      ] as const;
      for (const [index, testCase] of updateReferenceCases.entries()) {
        updateReferenceIssue = testCase.issue;
        const operationId = randomUUID();
        const updateRequest = {
          method: "POST" as const,
          url: "/api/v1/budgets/synthetic-budget/transactions/synthetic-transaction/operations",
          headers: {
            authorization: `Bearer ${signedInDevice.accessToken}`,
            "idempotency-key": `invalid-update-reference-${index}`,
          },
          payload: {
            operationId,
            action: "update",
            baseRevision: createTransactionRevision(baseSnapshot),
            baseSnapshot,
            patch: testCase.patch,
          },
        };
        const rejectedUpdate = await app.inject(updateRequest);
        expect(rejectedUpdate.statusCode).toBe(400);
        expect(rejectedUpdate.json()).toMatchObject({
          error: testCase.issue,
        });
        await expect(database!.journal.get(operationId)).resolves.toMatchObject(
          { status: "rejected", errorCode: testCase.issue.code },
        );
      }
      expect(updateAttempts).toBe(0);
      expect(report).not.toHaveBeenCalled();
    } finally {
      report.mockRestore();
      await app.close();
    }
  });
});

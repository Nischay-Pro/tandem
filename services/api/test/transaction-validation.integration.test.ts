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
    database = createPostgresDatabase(isolatedConnection.toString());
    await database.migrate();
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
    const transactions: ActualTransactionStore = {
      async read() {
        actualReads += 1;
        return currentTransaction;
      },
      async create() {
        throw new Error("Invalid requests must not create in Actual");
      },
      async update() {
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
    const pairing = await database!.deviceAuth.createPairingCode({
      principalId: `validation-owner-${randomUUID()}`,
      budgetIds: ["synthetic-budget"],
    });
    const pairedDevice = await database!.deviceAuth.pair({
      code: pairing.code,
      deviceName: "Validation test device",
    });
    if (!pairedDevice) {
      throw new Error("Synthetic pairing code was not accepted");
    }

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
            authorization: `Bearer ${pairedDevice.accessToken}`,
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
      expect(report).not.toHaveBeenCalled();
    } finally {
      report.mockRestore();
      await app.close();
    }
  });
});

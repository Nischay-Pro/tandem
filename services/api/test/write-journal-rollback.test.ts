import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import { PostgresWriteJournal } from "../src/write-journal/postgres-write-journal.js";
import type { WriteOperationInput } from "../src/write-journal/contracts.js";

const input: WriteOperationInput = {
  operationId: randomUUID(),
  principalId: "synthetic-device",
  budgetId: "synthetic-budget",
  idempotencyKey: "synthetic-request",
  entityType: "transaction",
  action: "update",
  entityId: "synthetic-transaction",
  baseRevision: "synthetic-revision",
  baseSnapshot: { id: "synthetic-transaction", amount: 1500 },
  patch: { amount: 1200 },
};

describe("PostgreSQL journal rollback failures", () => {
  it.each([
    {
      label: "an Error",
      rollbackError: new Error("rollback connection failed"),
    },
    {
      label: "a non-Error value",
      rollbackError: "rollback connection failed",
    },
  ])(
    "preserves the primary database error and discards the client when rollback throws $label",
    async ({ rollbackError }) => {
      const primaryError = new Error("financial operation insert failed");
      const consoleError = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const query = vi.fn().mockImplementation(async (sql: string) => {
        if (sql === "BEGIN") {
          return { rows: [], rowCount: 0 };
        }
        if (sql === "ROLLBACK") {
          throw rollbackError;
        }
        throw primaryError;
      });
      const release = vi.fn();
      const pool = {
        connect: vi.fn().mockResolvedValue({ query, release }),
      } as unknown as Pool;

      await expect(new PostgresWriteJournal(pool).enqueue(input)).rejects.toBe(
        primaryError,
      );
      expect(query).toHaveBeenCalledWith("ROLLBACK");
      expect(release).toHaveBeenCalledWith(
        rollbackError instanceof Error ? rollbackError : true,
      );
      expect(consoleError).toHaveBeenCalledWith(
        "PostgreSQL transaction rollback failed; client discarded",
        {
          errorType:
            rollbackError instanceof Error ? rollbackError.name : "string",
        },
      );
      vi.restoreAllMocks();
    },
  );

  it("preserves the transaction error if cleanup logging throws", async () => {
    const primaryError = new Error("financial operation insert failed");
    const rollbackError = new Error("rollback connection failed");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql === "BEGIN") {
        return { rows: [], rowCount: 0 };
      }
      if (sql === "ROLLBACK") {
        throw rollbackError;
      }
      throw primaryError;
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;
    vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("logger unavailable");
    });

    await expect(new PostgresWriteJournal(pool).enqueue(input)).rejects.toBe(
      primaryError,
    );
    expect(release).toHaveBeenCalledWith(rollbackError);
    vi.restoreAllMocks();
  });
});

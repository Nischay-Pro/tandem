import { describe, expect, it, vi } from "vitest";
import type { Pool, PoolClient } from "pg";
import { ensureSchema } from "../src/schema/bootstrap.js";

function createPool(
  tableRows: Array<{ table_name: string; table_type: string }> = [],
  columnRows: Array<{
    table_name: string;
    column_name: string;
    udt_name: string;
  }> = [],
) {
  const query = vi.fn().mockImplementation(async (sql: string) => {
    if (sql.includes("information_schema.tables")) {
      return { rows: tableRows, rowCount: tableRows.length };
    }
    if (sql.includes("information_schema.columns")) {
      return { rows: columnRows, rowCount: columnRows.length };
    }
    return { rows: [], rowCount: 0 };
  });
  const release = vi.fn();
  const pool = {
    connect: vi.fn().mockResolvedValue({ query, release }),
  } as unknown as Pool;
  return { pool, query, release };
}

describe("0.1.0 schema bootstrap", () => {
  it("creates one idempotent schema without a migration registry or history table", async () => {
    const { pool, query } = createPool();

    await ensureSchema(pool);

    const statements = query.mock.calls.map(([sql]) => String(sql));
    expect(
      statements.filter((sql) => sql.includes("CREATE TABLE IF NOT EXISTS")),
    ).toHaveLength(4);
    expect(
      statements.filter((sql) => sql.includes("CREATE INDEX IF NOT EXISTS")),
    ).toHaveLength(3);
    expect(statements).toContain("BEGIN");
    expect(statements).toContain("COMMIT");
    expect(statements.join("\n")).not.toMatch(
      /tandem_migrations|DROP TABLE|ALTER TABLE/i,
    );
  });

  it("refuses a partial old schema before attempting to create or alter tables", async () => {
    const { pool, query, release } = createPool([
      { table_name: "tandem_devices", table_type: "BASE TABLE" },
    ]);

    await expect(ensureSchema(pool)).rejects.toThrow(
      /does not match this 0\.1\.0 schema.*No data was changed/i,
    );

    const statements = query.mock.calls.map(([sql]) => String(sql));
    expect(statements.some((sql) => sql.includes("CREATE TABLE"))).toBe(false);
    expect(statements).toContain("ROLLBACK");
    expect(release).toHaveBeenCalledWith(undefined);
  });

  it("does not treat a legacy migration history table as a fresh database", async () => {
    const { pool, query } = createPool([
      { table_name: "tandem_migrations", table_type: "BASE TABLE" },
    ]);

    await expect(ensureSchema(pool)).rejects.toThrow(
      /legacy migration history exists.*No data was changed/i,
    );
    expect(
      query.mock.calls.some(([sql]) => String(sql).includes("CREATE TABLE")),
    ).toBe(false);
  });

  it("rejects incompatible column types without running DDL", async () => {
    const requiredTables = [
      "tandem_devices",
      "tandem_actual_sessions",
      "tandem_operations",
      "tandem_operation_conflicts",
    ];
    const { pool, query } = createPool(
      requiredTables.map((table_name) => ({
        table_name,
        table_type: "BASE TABLE",
      })),
      [
        {
          table_name: "tandem_devices",
          column_name: "device_id",
          udt_name: "text",
        },
      ],
    );

    await expect(ensureSchema(pool)).rejects.toThrow(
      /tandem_devices\.device_id has type text; expected uuid/,
    );
    expect(
      query.mock.calls.some(([sql]) => String(sql).includes("CREATE TABLE")),
    ).toBe(false);
  });

  it("rejects a non-table object occupying a required table name", async () => {
    const tableNames = [
      "tandem_devices",
      "tandem_actual_sessions",
      "tandem_operations",
      "tandem_operation_conflicts",
    ];
    const { pool, query } = createPool(
      tableNames.map((table_name, index) => ({
        table_name,
        table_type: index === 0 ? "VIEW" : "BASE TABLE",
      })),
    );

    await expect(ensureSchema(pool)).rejects.toThrow(
      /non-table objects use Tandem table names/i,
    );
    expect(
      query.mock.calls.some(([sql]) => String(sql).includes("CREATE TABLE")),
    ).toBe(false);
  });

  it("rejects required columns that are missing from a complete table set", async () => {
    const tableNames = [
      "tandem_devices",
      "tandem_actual_sessions",
      "tandem_operations",
      "tandem_operation_conflicts",
    ];
    const { pool } = createPool(
      tableNames.map((table_name) => ({
        table_name,
        table_type: "BASE TABLE",
      })),
    );

    await expect(ensureSchema(pool)).rejects.toThrow(
      /tandem_devices.device_id is missing/,
    );
  });

  it("discards a connection when rollback cannot complete", async () => {
    const rollbackError = new Error("rollback failed");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql === "ROLLBACK") throw rollbackError;
      if (sql.includes("information_schema.tables")) {
        return {
          rows: [{ table_name: "tandem_devices", table_type: "BASE TABLE" }],
          rowCount: 1,
        };
      }
      return { rows: [], rowCount: 0 };
    });
    const release = vi.fn();
    const pool = {
      connect: vi
        .fn()
        .mockResolvedValue({ query, release } as Partial<PoolClient>),
    } as unknown as Pool;

    await expect(ensureSchema(pool)).rejects.toThrow(
      /does not match this 0\.1\.0 schema/i,
    );
    expect(release).toHaveBeenCalledWith(true);
  });

  it("releases the client without rollback when the transaction cannot start", async () => {
    const beginError = new Error("connection failed before transaction start");
    const query = vi.fn().mockRejectedValue(beginError);
    const release = vi.fn();
    const pool = {
      connect: vi.fn().mockResolvedValue({ query, release }),
    } as unknown as Pool;

    await expect(ensureSchema(pool)).rejects.toBe(beginError);
    expect(query).toHaveBeenCalledOnce();
    expect(release).toHaveBeenCalledWith(undefined);
  });
});

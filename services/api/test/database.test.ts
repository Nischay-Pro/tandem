import { beforeEach, describe, expect, it, vi } from "vitest";

const { pools, applyMigrations } = vi.hoisted(() => ({
  pools: [] as FakePool[],
  applyMigrations: vi.fn(),
}));

vi.mock("pg", () => ({
  Pool: class FakePool {
    readonly query = vi.fn(async () => ({ rows: [] }));
    readonly end = vi.fn(async () => undefined);
    readonly clientQuery = vi.fn(async (sql: string) =>
      sql.includes("pg_advisory_unlock")
        ? { rows: [{ unlocked: true }] }
        : { rows: [] },
    );
    readonly clientRelease = vi.fn();
    readonly connect = vi.fn(async () => ({
      query: this.clientQuery,
      release: this.clientRelease,
    }));

    constructor(readonly options: unknown) {
      pools.push(this);
    }
  },
}));

vi.mock("../src/schema/migrator.js", () => ({ applyMigrations }));

import { createPostgresDatabase } from "../src/database.js";
import { InMemoryBudgetProjectionStore } from "../src/budget-projection/in-memory-budget-projection-store.js";

describe("PostgreSQL database wiring", () => {
  beforeEach(() => {
    pools.length = 0;
    applyMigrations.mockReset();
  });

  it("shares readiness checks, migrates, locks, and closes both pools", async () => {
    const database = createPostgresDatabase("postgres://test/db", "test-key");
    const applicationPool = pools[0]!;
    const readinessPool = pools[1]!;

    expect(pools).toHaveLength(2);
    expect(applicationPool.options).toMatchObject({
      connectionString: "postgres://test/db",
      max: 10,
    });
    expect(readinessPool.options).toMatchObject({
      connectionString: "postgres://test/db",
      max: 1,
    });
    expect(database.budgetProjection).toBeInstanceOf(
      InMemoryBudgetProjectionStore,
    );

    await expect(
      Promise.all([database.ping(), database.ping()]),
    ).resolves.toEqual([undefined, undefined]);
    expect(readinessPool.connect).toHaveBeenCalledTimes(1);
    expect(readinessPool.clientQuery).toHaveBeenCalledTimes(4);
    await database.ping();
    expect(readinessPool.connect).toHaveBeenCalledTimes(2);
    expect(readinessPool.clientQuery).toHaveBeenCalledTimes(8);

    await database.migrate();
    expect(applyMigrations).toHaveBeenCalledWith(applicationPool);

    await expect(
      database.withBudgetLock("budget-one", async () => "locked"),
    ).resolves.toBe("locked");
    expect(applicationPool.connect).toHaveBeenCalledOnce();

    await database.close();
    expect(applicationPool.end).toHaveBeenCalledOnce();
    expect(readinessPool.end).toHaveBeenCalledOnce();
  });

  it("discards a readiness connection when its rollback fails", async () => {
    const database = createPostgresDatabase("postgres://test/db", "test-key");
    const readinessPool = pools[1]!;
    const fullDiskError = Object.assign(new Error("database is full"), {
      code: "53100",
    });
    const rollbackError = new Error("connection was lost during rollback");
    readinessPool.clientQuery.mockImplementation(async (sql: string) => {
      if (
        sql ===
        "INSERT INTO tandem_readiness_storage_probe (marker) VALUES (true)"
      ) {
        throw fullDiskError;
      }
      if (sql === "ROLLBACK") throw rollbackError;
      return { rows: [] };
    });

    await expect(database.ping()).rejects.toBe(fullDiskError);
    expect(readinessPool.clientRelease).toHaveBeenCalledWith(rollbackError);
    await database.close();
  });

  it("uses a safe release error when rollback throws a non-Error value", async () => {
    const database = createPostgresDatabase("postgres://test/db", "test-key");
    const readinessPool = pools[1]!;
    const fullDiskError = Object.assign(new Error("database is full"), {
      code: "53100",
    });
    readinessPool.clientQuery.mockImplementation(async (sql: string) => {
      if (
        sql ===
        "INSERT INTO tandem_readiness_storage_probe (marker) VALUES (true)"
      ) {
        throw fullDiskError;
      }
      if (sql === "ROLLBACK") throw { message: "connection was lost" };
      return { rows: [] };
    });

    await expect(database.ping()).rejects.toBe(fullDiskError);
    const releaseError = readinessPool.clientRelease.mock.calls[0]?.[0] as
      Error | undefined;
    expect(releaseError).toBeInstanceOf(Error);
    expect(releaseError?.message).toBe("Readiness probe rollback failed");
    await database.close();
  });
});

interface FakePool {
  readonly options: unknown;
  readonly query: ReturnType<typeof vi.fn>;
  readonly end: ReturnType<typeof vi.fn>;
  readonly connect: ReturnType<typeof vi.fn>;
  readonly clientQuery: ReturnType<typeof vi.fn>;
  readonly clientRelease: ReturnType<typeof vi.fn>;
}

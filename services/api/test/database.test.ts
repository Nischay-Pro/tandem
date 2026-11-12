import { beforeEach, describe, expect, it, vi } from "vitest";

const { pools, applyMigrations } = vi.hoisted(() => ({
  pools: [] as FakePool[],
  applyMigrations: vi.fn(),
}));

vi.mock("pg", () => ({
  Pool: class FakePool {
    readonly query = vi.fn(async () => ({ rows: [] }));
    readonly end = vi.fn(async () => undefined);
    readonly connect = vi.fn(async () => ({
      query: vi.fn(async (sql: string) =>
        sql.includes("pg_advisory_unlock")
          ? { rows: [{ unlocked: true }] }
          : { rows: [] },
      ),
      release: vi.fn(),
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
    expect(readinessPool.query).toHaveBeenCalledTimes(1);
    await database.ping();
    expect(readinessPool.query).toHaveBeenCalledTimes(2);

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
});

interface FakePool {
  readonly options: unknown;
  readonly query: ReturnType<typeof vi.fn>;
  readonly end: ReturnType<typeof vi.fn>;
  readonly connect: ReturnType<typeof vi.fn>;
}

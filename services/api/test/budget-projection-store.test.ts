import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import { createPostgresBudgetProjectionStore } from "../src/budget-projection/postgres-budget-projection-store.js";

describe("PostgreSQL budget projection store failure handling", () => {
  it("checks freshness with a positive interval and fails closed without state", async () => {
    const query = vi.fn(async () => ({ rows: [] }));
    const pool = { query } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);

    await expect(
      store.isSnapshotFresh("budget-1", "epoch-1", 15_000),
    ).resolves.toBe(false);
    expect(query).toHaveBeenCalledWith(
      expect.stringContaining("refreshed_at >= clock_timestamp()"),
      ["budget-1", "epoch-1", 15_000],
    );
    await expect(
      store.isSnapshotFresh("budget-1", "epoch-1", 0),
    ).rejects.toThrow("Snapshot freshness interval must be a positive integer");
    await expect(store.isSnapshotFresh("", "epoch-1", 15_000)).rejects.toThrow(
      "Budget ID is invalid",
    );
    await expect(
      store.isSnapshotFresh("budget-1", "bad epoch", 15_000),
    ).rejects.toThrow("Change feed epoch is invalid");
  });

  it("preserves the write error and discards a client when rollback fails", async () => {
    const primaryError = new Error("synthetic write failure");
    const rollbackError = new Error("synthetic rollback failure");
    const query = vi.fn(async (sql: string) => {
      if (sql.startsWith("INSERT INTO companion_budget_sync_state")) {
        throw primaryError;
      }
      if (sql === "ROLLBACK") throw rollbackError;
      return { rows: [] };
    });
    const release = vi.fn();
    const client = { query, release };
    const pool = {
      connect: vi.fn(async () => client),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(store.applySnapshot("budget-1", "epoch", [])).rejects.toBe(
      primaryError,
    );

    expect(release).toHaveBeenCalledWith(true);
    expect(consoleError).toHaveBeenCalledWith(
      "Budget projection rollback failed",
      { primaryError: "Error", rollbackError: "Error" },
    );
    vi.restoreAllMocks();
  });

  it("does not roll back when BEGIN fails for snapshot and feed reads", async () => {
    const beginError = new Error("synthetic begin failure");
    const query = vi.fn(async () => {
      throw beginError;
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);

    await expect(store.applySnapshot("budget-1", "epoch", [])).rejects.toBe(
      beginError,
    );
    await expect(store.readSnapshot("budget-1")).rejects.toBe(beginError);
    await expect(
      store.readChanges({
        budgetId: "budget-1",
        generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
        cursor: "0",
        limit: 10,
      }),
    ).rejects.toBe(beginError);

    expect(query).toHaveBeenCalledTimes(3);
    expect(query).toHaveBeenCalledWith("BEGIN");
    expect(query).toHaveBeenCalledWith(
      "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY",
    );
    expect(release).toHaveBeenNthCalledWith(1, false);
    expect(release).toHaveBeenNthCalledWith(2, false);
    expect(release).toHaveBeenNthCalledWith(3, false);
  });

  it("fails clearly if synchronization state is missing during refresh", async () => {
    const query = vi.fn(async (sql: string) => {
      if (sql.includes("FOR UPDATE")) return { rows: [] };
      return { rows: [] };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);

    await expect(store.applySnapshot("budget-1", "epoch", [])).rejects.toThrow(
      "Budget synchronization state was not created",
    );

    expect(query).toHaveBeenLastCalledWith("ROLLBACK");
    expect(release).toHaveBeenCalledWith(false);
  });

  it("fails clearly if synchronization state disappears during refresh", async () => {
    let lockedStateRead = false;
    const query = vi.fn(async (sql: string) => {
      if (sql.includes("FROM companion_budget_sync_state")) {
        if (sql.includes("FOR UPDATE")) {
          lockedStateRead = true;
          return {
            rows: [
              {
                budget_id: "budget-1",
                generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
                feed_epoch: "epoch",
                cursor: "0",
                refreshed_at: new Date(),
              },
            ],
          };
        }
        return { rows: [] };
      }
      if (sql.includes("FROM companion_budget_records")) {
        return { rows: [] };
      }
      return { rows: [] };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);

    await expect(store.applySnapshot("budget-1", "epoch", [])).rejects.toThrow(
      "Budget synchronization state disappeared during refresh",
    );

    expect(lockedStateRead).toBe(true);
    expect(query).toHaveBeenLastCalledWith("ROLLBACK");
    expect(release).toHaveBeenCalledWith(false);
  });

  it("rejects corrupt non-object JSON returned by PostgreSQL", async () => {
    const query = vi.fn(async (sql: string) => {
      if (sql === "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY") {
        return { rows: [] };
      }
      if (sql.includes("FROM companion_budget_sync_state")) {
        return {
          rows: [
            {
              budget_id: "budget-1",
              generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
              feed_epoch: "epoch",
              cursor: "1",
              refreshed_at: new Date(),
            },
          ],
        };
      }
      if (sql.includes("FROM companion_budget_records")) {
        return {
          rows: [
            {
              entity_type: "account",
              entity_id: "account-1",
              revision: "f".repeat(64),
              body: [],
              is_deleted: false,
            },
          ],
        };
      }
      if (sql === "ROLLBACK") {
        throw new Error("synthetic rollback failure");
      }
      return { rows: [] };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(store.readSnapshot("budget-1")).rejects.toThrow(
      "Budget projection contains invalid JSON data",
    );

    expect(query).toHaveBeenLastCalledWith("ROLLBACK");
    expect(release).toHaveBeenCalledWith(true);
    expect(consoleError).toHaveBeenCalledWith(
      "Budget projection rollback failed",
      { primaryError: "Error", rollbackError: "Error" },
    );
    vi.restoreAllMocks();
  });

  it("preserves a change-feed read error if rollback also fails", async () => {
    const primaryError = "synthetic feed read failure";
    const query = vi.fn(async (sql: string) => {
      if (sql === "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY") {
        return { rows: [] };
      }
      if (sql.includes("FROM companion_budget_sync_state")) {
        throw primaryError;
      }
      if (sql === "ROLLBACK") {
        throw "synthetic rollback failure";
      }
      return { rows: [] };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(
      store.readChanges({
        budgetId: "budget-1",
        generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
        cursor: "0",
        limit: 10,
      }),
    ).rejects.toBe(primaryError);

    expect(release).toHaveBeenCalledWith(true);
    expect(consoleError).toHaveBeenCalledWith(
      "Budget projection rollback failed",
      { primaryError: "string", rollbackError: "string" },
    );
    vi.restoreAllMocks();
  });

  it("keeps the cursor when a change page has no events", async () => {
    const query = vi.fn(async (sql: string) => {
      if (sql.includes("FROM companion_budget_sync_state")) {
        return {
          rows: [
            {
              budget_id: "budget-1",
              generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
              feed_epoch: "epoch",
              cursor: "0",
              refreshed_at: new Date(),
            },
          ],
        };
      }
      if (sql.includes("MIN(cursor)")) return { rows: [{ min_cursor: null }] };
      if (sql.includes("FROM companion_budget_changes")) {
        return { rows: [] };
      }
      return { rows: [] };
    });
    const release = vi.fn();
    const pool = {
      connect: vi.fn(async () => ({ query, release })),
    } as unknown as Pool;
    const store = createPostgresBudgetProjectionStore(pool);

    await expect(
      store.readChanges({
        budgetId: "budget-1",
        generation: "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd",
        cursor: "0",
        limit: 10,
      }),
    ).resolves.toMatchObject({
      nextCursor: "0",
      currentCursor: "0",
      changes: [],
      resetRequired: false,
    });
  });
});

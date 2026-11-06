import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import { applyMigrations } from "../src/schema/migrator.js";
import {
  BudgetProjectionInputError,
  type BudgetEntityType,
  type BudgetProjectionRecord,
} from "../src/budget-projection/contracts.js";
import {
  MAX_BUDGET_CHANGE_PAGE_SIZE,
  MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE,
  MAX_BUDGET_SNAPSHOT_PAGE_SIZE,
  MAX_RETAINED_BUDGET_CHANGES,
  createPostgresBudgetProjectionStore,
} from "../src/budget-projection/postgres-budget-projection-store.js";
import { createBudgetRecordRevision } from "../src/actual/budget-snapshot-reader.js";

const databaseUrl = process.env.DATABASE_URL;
const integrationEnabled = Boolean(databaseUrl);

let adminPool: Pool | undefined;
let pool: Pool | undefined;
let schema: string | undefined;

beforeAll(async () => {
  if (!databaseUrl) return;
  adminPool = new Pool({ connectionString: databaseUrl, max: 2 });
  schema = `budget_projection_${randomUUID().replaceAll("-", "")}`;
  await adminPool.query(`CREATE SCHEMA ${schema}`);
  const isolatedUrl = new URL(databaseUrl);
  isolatedUrl.searchParams.set("options", `-c search_path=${schema}`);
  pool = new Pool({ connectionString: isolatedUrl.toString(), max: 6 });
  await applyMigrations(pool);
}, 30_000);

afterAll(async () => {
  await pool?.end();
  if (schema) await adminPool?.query(`DROP SCHEMA ${schema} CASCADE`);
  await adminPool?.end();
});

function record(
  type: BudgetEntityType,
  id: string,
  body: Record<string, unknown>,
): BudgetProjectionRecord {
  const jsonBody = body as BudgetProjectionRecord["body"];
  return {
    type,
    id,
    revision: createBudgetRecordRevision(jsonBody),
    body: jsonBody,
  };
}

function getPool(): Pool {
  if (!pool) throw new Error("Isolated PostgreSQL pool was not created");
  return pool;
}

describe.skipIf(!integrationEnabled)(
  "PostgreSQL budget projection store",
  () => {
    it("materializes snapshots, emits idempotent changes, and persists across store instances", async () => {
      const firstStore = createPostgresBudgetProjectionStore(getPool());
      const initialRecords = [
        record("account", "account-1", {
          id: "account-1",
          balance_current: 100,
        }),
        record("category", "category-1", { id: "category-1", name: "Food" }),
        record("payee", "payee-1", { id: "payee-1", name: "Market" }),
      ];

      const initial = await firstStore.applySnapshot(
        "budget-1",
        "install-epoch-a",
        initialRecords,
      );
      expect(initial.cursor).toBe("3");
      expect(initial.records).toEqual(
        [...initialRecords].sort((left, right) =>
          left.type === right.type
            ? left.id.localeCompare(right.id)
            : left.type.localeCompare(right.type),
        ),
      );

      const repeated = await firstStore.applySnapshot(
        "budget-1",
        "install-epoch-a",
        initialRecords,
      );
      expect(repeated.generation).toBe(initial.generation);
      expect(repeated.cursor).toBe(initial.cursor);

      const changed = await firstStore.applySnapshot(
        "budget-1",
        "install-epoch-a",
        [
          record("account", "account-1", {
            id: "account-1",
            balance_current: 75,
          }),
          initialRecords[1]!,
          record("transaction", "transaction-1", {
            id: "transaction-1",
            amount: -25,
          }),
        ],
      );
      expect(changed.cursor).toBe("6");
      expect(changed.records.map(({ type, id }) => `${type}:${id}`)).toEqual([
        "account:account-1",
        "category:category-1",
        "transaction:transaction-1",
      ]);

      const page = await firstStore.readChanges({
        budgetId: "budget-1",
        generation: initial.generation,
        cursor: "3",
        limit: 2,
      });
      expect(page).toMatchObject({
        generation: initial.generation,
        currentCursor: "6",
        throughCursor: "6",
        nextCursor: "5",
        hasMore: true,
        resetRequired: false,
      });
      expect(
        page?.changes.map(({ operation, id }) => `${operation}:${id}`),
      ).toEqual(["upsert:account-1", "upsert:transaction-1"]);
      const secondPage = await firstStore.readChanges({
        budgetId: "budget-1",
        generation: initial.generation,
        cursor: page!.nextCursor,
        throughCursor: page!.throughCursor,
        limit: 2,
      });
      expect(secondPage).toMatchObject({
        nextCursor: "6",
        throughCursor: "6",
        hasMore: false,
        resetRequired: false,
      });
      expect(secondPage?.changes).toMatchObject([
        { operation: "delete", type: "payee", id: "payee-1", body: null },
      ]);

      const restartedStore = createPostgresBudgetProjectionStore(getPool());
      await expect(restartedStore.readSnapshot("budget-1")).resolves.toEqual(
        changed,
      );
    });

    it("reads keyset snapshot pages from one generation and requests a reset after changes", async () => {
      const store = createPostgresBudgetProjectionStore(getPool());
      const budgetId = "budget-snapshot-pages";
      const input = [
        record("account", "account-1", { id: "account-1" }),
        record("account", "account-2", { id: "account-2" }),
        record("category", "category-1", { id: "category-1" }),
        record("payee", "payee-1", { id: "payee-1" }),
        record("transaction", "txn-1", { id: "txn-1" }),
        record("transaction", "txn-2", { id: "txn-2" }),
        record("transaction", "txn-3", { id: "txn-3" }),
      ];
      const snapshot = await store.applySnapshot(budgetId, "epoch", input);
      const first = await store.readSnapshotPage({
        budgetId,
        generation: snapshot.generation,
        cursor: snapshot.cursor,
        limit: 3,
      });
      expect(first).toMatchObject({
        budgetId,
        generation: snapshot.generation,
        cursor: snapshot.cursor,
        hasMore: true,
        resetRequired: false,
        nextAfterType: first!.records.at(-1)!.type,
        nextAfterId: first!.records.at(-1)!.id,
      });
      const second = await store.readSnapshotPage({
        budgetId,
        generation: snapshot.generation,
        cursor: snapshot.cursor,
        afterType: first!.nextAfterType!,
        afterId: first!.nextAfterId!,
        limit: 3,
      });
      const third = await store.readSnapshotPage({
        budgetId,
        generation: snapshot.generation,
        cursor: snapshot.cursor,
        afterType: second!.nextAfterType!,
        afterId: second!.nextAfterId!,
        limit: 3,
      });
      expect(second?.hasMore).toBe(true);
      expect(third).toMatchObject({
        hasMore: false,
        resetRequired: false,
        records: [{ id: "txn-3" }],
      });
      expect(
        [...first!.records, ...second!.records, ...third!.records].map(
          ({ type, id }) => `${type}:${id}`,
        ),
      ).toEqual(snapshot.records.map(({ type, id }) => `${type}:${id}`));

      await store.applySnapshot(budgetId, "epoch", [
        ...input,
        record("transaction", "txn-4", { id: "txn-4" }),
      ]);
      await expect(
        store.readSnapshotPage({
          budgetId,
          generation: snapshot.generation,
          cursor: snapshot.cursor,
          afterType: first!.nextAfterType!,
          afterId: first!.nextAfterId!,
          limit: 3,
        }),
      ).resolves.toMatchObject({
        resetRequired: true,
        records: [],
        hasMore: false,
      });
      expect(MAX_BUDGET_SNAPSHOT_PAGE_SIZE).toBe(2_000);
    });

    it("uses the database refresh timestamp and epoch for snapshot freshness", async () => {
      const store = createPostgresBudgetProjectionStore(getPool());
      await expect(
        store.isSnapshotFresh("budget-freshness", "epoch-a", 15_000),
      ).resolves.toBe(false);

      await store.applySnapshot("budget-freshness", "epoch-a", []);
      await expect(
        store.isSnapshotFresh("budget-freshness", "epoch-a", 15_000),
      ).resolves.toBe(true);
      await expect(
        store.isSnapshotFresh("budget-freshness", "epoch-b", 15_000),
      ).resolves.toBe(false);

      await getPool().query(
        `UPDATE companion_budget_sync_state
         SET refreshed_at = clock_timestamp() - INTERVAL '16 seconds'
         WHERE budget_id = $1`,
        ["budget-freshness"],
      );
      await expect(
        store.isSnapshotFresh("budget-freshness", "epoch-a", 15_000),
      ).resolves.toBe(false);
    });

    it("applies a refresh using record metadata without rereading bodies or locking every row", async () => {
      const statements: string[] = [];
      const database = getPool();
      const trackingPool = {
        connect: async () =>
          new Proxy(await database.connect(), {
            get(target, property) {
              if (property === "query") {
                return (queryText: string, ...args: unknown[]) => {
                  statements.push(queryText.replace(/\s+/g, " ").trim());
                  return Reflect.apply(
                    Reflect.get(target, property, target),
                    target,
                    [queryText, ...args],
                  );
                };
              }
              const value = Reflect.get(target, property, target);
              return typeof value === "function" ? value.bind(target) : value;
            },
          }),
      } as unknown as Pool;
      const store = createPostgresBudgetProjectionStore(trackingPool);

      await expect(
        store.applySnapshotForRefresh!("budget-refresh-checkpoint", "epoch", [
          record("transaction", "transaction-1", { id: "transaction-1" }),
        ]),
      ).resolves.toMatchObject({
        budgetId: "budget-refresh-checkpoint",
        cursor: "1",
      });

      const currentRecordRead = statements.find((statement) =>
        statement.startsWith(
          "SELECT entity_type, entity_id, revision, is_deleted FROM companion_budget_records",
        ),
      );
      expect(currentRecordRead).toBe(
        "SELECT entity_type, entity_id, revision, is_deleted FROM companion_budget_records WHERE budget_id = $1",
      );
      expect(currentRecordRead).not.toMatch(/ORDER BY|FOR UPDATE/);
      expect(
        statements.some((statement) =>
          statement.startsWith(
            "SELECT entity_type, entity_id, revision, body, is_deleted FROM companion_budget_records",
          ),
        ),
      ).toBe(false);
    });

    it("writes large upsert and delete snapshots in bounded batches", async () => {
      const statementCounts = new Map<string, number>();
      const database = getPool();
      const trackingPool = {
        connect: async () =>
          new Proxy(await database.connect(), {
            get(target, property) {
              if (property === "query") {
                return (queryText: string, ...args: unknown[]) => {
                  const normalized = queryText.replace(/\s+/g, " ").trim();
                  const statement = normalized.startsWith(
                    "INSERT INTO companion_budget_records",
                  )
                    ? "record-upsert"
                    : normalized.startsWith(
                          "UPDATE companion_budget_records AS existing",
                        )
                      ? "record-delete"
                      : normalized.startsWith(
                            "INSERT INTO companion_budget_changes",
                          )
                        ? "change"
                        : undefined;
                  if (statement) {
                    statementCounts.set(
                      statement,
                      (statementCounts.get(statement) ?? 0) + 1,
                    );
                  }
                  return Reflect.apply(
                    Reflect.get(target, property, target),
                    target,
                    [queryText, ...args],
                  );
                };
              }
              const value = Reflect.get(target, property, target);
              return typeof value === "function" ? value.bind(target) : value;
            },
          }),
      } as unknown as Pool;
      const store = createPostgresBudgetProjectionStore(trackingPool);
      const records = Array.from(
        { length: MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE * 2 + 1 },
        (_, index) => {
          const id = `transaction-${index.toString().padStart(5, "0")}`;
          return record("transaction", id, { id, amount: -index });
        },
      );

      const initial = await store.applySnapshot(
        "budget-batched",
        "epoch",
        records,
      );
      expect(initial.cursor).toBe(records.length.toString());
      expect(initial.records).toHaveLength(records.length);
      expect(Object.fromEntries(statementCounts)).toEqual({
        "record-upsert": 3,
        change: 3,
      });

      statementCounts.clear();
      const nextRecords = records
        .slice(0, 500)
        .map((item, index) =>
          index === 0
            ? record("transaction", item.id, { id: item.id, amount: 1 })
            : item,
        );
      const updated = await store.applySnapshot(
        "budget-batched",
        "epoch",
        nextRecords,
      );

      expect(updated.cursor).toBe((records.length + 1 + 501).toString());
      expect(updated.records).toHaveLength(500);
      expect(Object.fromEntries(statementCounts)).toEqual({
        "record-upsert": 1,
        change: 3,
        "record-delete": 2,
      });
    });

    it("rotates generation and clears old events when the restore epoch changes", async () => {
      const store = createPostgresBudgetProjectionStore(getPool());
      const rows = [
        record("category", "category-epoch", { id: "category-epoch" }),
      ];
      const original = await store.applySnapshot(
        "budget-epoch",
        "restore-a",
        rows,
      );
      const restored = await store.applySnapshot(
        "budget-epoch",
        "restore-b",
        rows,
      );

      expect(restored.generation).not.toBe(original.generation);
      expect(restored.cursor).toBe("0");
      const oldClient = await store.readChanges({
        budgetId: "budget-epoch",
        generation: original.generation,
        cursor: original.cursor,
        limit: 10,
      });
      expect(oldClient).toMatchObject({
        generation: restored.generation,
        resetRequired: true,
        changes: [],
      });
    });

    it("requests a full snapshot for future cursors, pruned history, and unknown budgets", async () => {
      const store = createPostgresBudgetProjectionStore(getPool(), 2);
      const data = [
        record("account", "retained-1", { id: "retained-1" }),
        record("account", "retained-2", { id: "retained-2" }),
        record("account", "retained-3", { id: "retained-3" }),
      ];
      const snapshot = await store.applySnapshot(
        "budget-retention",
        "epoch",
        data,
      );
      const oldCursor = await store.readChanges({
        budgetId: "budget-retention",
        generation: snapshot.generation,
        cursor: "0",
        limit: 2,
      });
      expect(oldCursor?.resetRequired).toBe(true);
      const availableCursor = await store.readChanges({
        budgetId: "budget-retention",
        generation: snapshot.generation,
        cursor: "1",
        limit: 2,
      });
      expect(availableCursor?.resetRequired).toBe(false);
      const futureCursor = await store.readChanges({
        budgetId: "budget-retention",
        generation: snapshot.generation,
        cursor: "4",
        limit: 2,
      });
      expect(futureCursor?.resetRequired).toBe(true);
      await expect(
        store.readSnapshot("unknown-budget"),
      ).resolves.toBeUndefined();
      await expect(
        store.readChanges({
          budgetId: "unknown-budget",
          generation: snapshot.generation,
          cursor: "0",
          limit: 2,
        }),
      ).resolves.toBeUndefined();
      expect(MAX_RETAINED_BUDGET_CHANGES).toBe(100_000);
      expect(MAX_BUDGET_CHANGE_PAGE_SIZE).toBe(500);
    });

    it("purges tombstones only after their delete leaves retained change history", async () => {
      const store = createPostgresBudgetProjectionStore(getPool(), 2);
      const budgetId = `budget-tombstone-retention-${randomUUID()}`;
      const keeper = (amount: number) =>
        record("account", "retained-account", {
          id: "retained-account",
          balance_current: amount,
        });
      const deletedPayee = record("payee", "deleted-payee", {
        id: "deleted-payee",
        name: "Old merchant",
      });

      const initial = await store.applySnapshot(budgetId, "epoch-a", [
        keeper(100),
        deletedPayee,
      ]);
      await store.applySnapshot(budgetId, "epoch-a", [keeper(100)]);
      const retainedTombstone = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM companion_budget_records
         WHERE budget_id = $1 AND entity_type = 'payee' AND is_deleted = true`,
        [budgetId],
      );
      expect(retainedTombstone.rows[0]?.count).toBe("1");

      await store.applySnapshot(budgetId, "epoch-a", [keeper(200)]);
      const stillRetainedTombstone = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM companion_budget_records
         WHERE budget_id = $1 AND entity_type = 'payee' AND is_deleted = true`,
        [budgetId],
      );
      expect(stillRetainedTombstone.rows[0]?.count).toBe("1");

      await store.applySnapshot(budgetId, "epoch-a", [keeper(300)]);
      const purgedTombstone = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM companion_budget_records
         WHERE budget_id = $1 AND entity_type = 'payee' AND is_deleted = true`,
        [budgetId],
      );
      expect(purgedTombstone.rows[0]?.count).toBe("0");
      await expect(
        store.readChanges({
          budgetId,
          generation: initial.generation,
          cursor: "2",
          limit: 10,
        }),
      ).resolves.toMatchObject({ resetRequired: true, changes: [] });

      await store.applySnapshot(budgetId, "epoch-a", [
        keeper(300),
        deletedPayee,
      ]);
      await store.applySnapshot(budgetId, "epoch-a", [keeper(300)]);
      const beforeEpochReset = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM companion_budget_records
         WHERE budget_id = $1 AND is_deleted = true`,
        [budgetId],
      );
      expect(beforeEpochReset.rows[0]?.count).toBe("1");

      const reset = await store.applySnapshot(budgetId, "epoch-b", [
        keeper(300),
      ]);
      const afterEpochReset = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM companion_budget_records
         WHERE budget_id = $1 AND is_deleted = true`,
        [budgetId],
      );
      expect(reset.generation).not.toBe(initial.generation);
      expect(reset.cursor).toBe("0");
      expect(afterEpochReset.rows[0]?.count).toBe("0");
    });

    it("rolls back the complete snapshot when the change append fails", async () => {
      const scoped = getPool();
      await scoped.query(`
      CREATE FUNCTION reject_budget_projection_change() RETURNS trigger AS $$
      BEGIN
        IF NEW.entity_id = 'force-failure' THEN
          RAISE EXCEPTION 'synthetic projection failure';
        END IF;
        RETURN NEW;
      END;
      $$ LANGUAGE plpgsql
    `);
      await scoped.query(`
      CREATE TRIGGER reject_budget_projection_change
      BEFORE INSERT ON companion_budget_changes
      FOR EACH ROW EXECUTE FUNCTION reject_budget_projection_change()
    `);
      const store = createPostgresBudgetProjectionStore(scoped);

      await expect(
        store.applySnapshot("budget-rollback", "epoch", [
          record("transaction", "force-failure", { id: "force-failure" }),
        ]),
      ).rejects.toThrow("synthetic projection failure");
      await expect(
        store.readSnapshot("budget-rollback"),
      ).resolves.toBeUndefined();

      await scoped.query(
        "DROP TRIGGER reject_budget_projection_change ON companion_budget_changes",
      );
      await scoped.query("DROP FUNCTION reject_budget_projection_change()");
    });

    it("validates epochs, records, cursors, generations, limits, and budget identifiers", async () => {
      expect(() => createPostgresBudgetProjectionStore(getPool(), 0)).toThrow(
        "Budget change retention must be a positive integer",
      );
      const store = createPostgresBudgetProjectionStore(getPool());
      await expect(
        store.applySnapshot(" ", "epoch", []),
      ).rejects.toBeInstanceOf(BudgetProjectionInputError);
      await expect(
        store.applySnapshot("budget-input", "bad epoch!", []),
      ).rejects.toThrow("Change feed epoch is invalid");
      await expect(
        store.applySnapshot(
          "budget-input",
          "epoch",
          null as unknown as BudgetProjectionRecord[],
        ),
      ).rejects.toThrow("Budget records must be an array");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          null as unknown as BudgetProjectionRecord,
        ]),
      ).rejects.toThrow("Budget record must be an object");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          {
            ...record("account", "id", { id: "id" }),
            type: "invalid",
          } as unknown as BudgetProjectionRecord,
        ]),
      ).rejects.toThrow("Budget record type is invalid");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          { ...record("account", "id", { id: "id" }), id: "\u0000" },
        ]),
      ).rejects.toThrow("Budget entity ID is invalid");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          { ...record("account", "id", { id: "id" }), id: "\u007f" },
        ]),
      ).rejects.toThrow("Budget entity ID is invalid");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          { ...record("account", "id", { id: "id" }), revision: "invalid" },
        ]),
      ).rejects.toThrow("Budget record revision is invalid");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          {
            ...record("account", "id", { id: "id" }),
            body: [] as unknown as BudgetProjectionRecord["body"],
          },
        ]),
      ).rejects.toThrow("Budget record body must be an object");
      await expect(
        store.applySnapshot("budget-input", "epoch", [
          record("account", "same", { id: "same" }),
          record("account", "same", { id: "same" }),
        ]),
      ).rejects.toThrow("Budget snapshot contains duplicate IDs");

      const valid = await store.applySnapshot("budget-input", "epoch", [
        record("account", "valid", { id: "valid" }),
      ]);
      const invalidPage = {
        budgetId: "budget-input",
        generation: valid.generation,
        cursor: "0",
        limit: 1,
      };
      await expect(
        store.readChanges({ ...invalidPage, generation: "bad" }),
      ).rejects.toThrow("Change feed generation is invalid");
      await expect(
        store.readChanges({ ...invalidPage, cursor: "01" }),
      ).rejects.toThrow("cursor must be a decimal cursor");
      await expect(
        store.readChanges({ ...invalidPage, throughCursor: "-1" }),
      ).rejects.toThrow("through cursor must be a decimal cursor");
      await expect(
        store.readChanges({ ...invalidPage, cursor: "1", throughCursor: "0" }),
      ).rejects.toThrow(
        "throughCursor must be greater than or equal to cursor",
      );
      await expect(
        store.readChanges({ ...invalidPage, limit: 0 }),
      ).rejects.toThrow(
        `limit must be between 1 and ${MAX_BUDGET_CHANGE_PAGE_SIZE}`,
      );
      await expect(
        store.readChanges({
          ...invalidPage,
          limit: MAX_BUDGET_CHANGE_PAGE_SIZE + 1,
        }),
      ).rejects.toThrow(
        `limit must be between 1 and ${MAX_BUDGET_CHANGE_PAGE_SIZE}`,
      );
    });
  },
);

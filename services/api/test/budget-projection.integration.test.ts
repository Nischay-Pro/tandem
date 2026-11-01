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

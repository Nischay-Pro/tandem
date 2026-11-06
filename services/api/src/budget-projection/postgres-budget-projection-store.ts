import { createHash, randomUUID } from "node:crypto";
import type { Pool, PoolClient } from "pg";
import {
  BUDGET_ENTITY_TYPES,
  BudgetProjectionInputError,
  type BudgetEntityType,
  type BudgetProjectionChange,
  type BudgetProjectionChangePage,
  type BudgetProjectionRecord,
  type BudgetProjectionSnapshot,
  type BudgetProjectionStore,
} from "./contracts.js";

export const MAX_BUDGET_CHANGE_PAGE_SIZE = 500;
export const MAX_RETAINED_BUDGET_CHANGES = 100_000;

interface SyncStateRow {
  budget_id: string;
  generation: string;
  feed_epoch: string;
  cursor: string;
  refreshed_at: Date;
}

interface CurrentRecordRow {
  entity_type: BudgetEntityType;
  entity_id: string;
  revision: string;
  body: Record<string, unknown> | null;
  is_deleted: boolean;
}

interface ChangeRow {
  cursor: string;
  entity_type: BudgetEntityType;
  entity_id: string;
  operation: "upsert" | "delete";
  revision: string;
  body: Record<string, unknown> | null;
}

export class PostgresBudgetProjectionStore implements BudgetProjectionStore {
  constructor(
    private readonly pool: Pool,
    private readonly changeRetention = MAX_RETAINED_BUDGET_CHANGES,
  ) {
    if (!Number.isSafeInteger(changeRetention) || changeRetention < 1) {
      throw new Error("Budget change retention must be a positive integer");
    }
  }

  async isSnapshotFresh(
    budgetId: string,
    feedEpoch: string,
    maxAgeMs: number,
  ): Promise<boolean> {
    validateBudgetId(budgetId);
    validateFeedEpoch(feedEpoch);
    if (!Number.isSafeInteger(maxAgeMs) || maxAgeMs < 1) {
      throw new Error("Snapshot freshness interval must be a positive integer");
    }

    const result = await this.pool.query<{ fresh: boolean }>(
      `SELECT EXISTS (
         SELECT 1
         FROM companion_budget_sync_state
         WHERE budget_id = $1
           AND feed_epoch = $2
           AND refreshed_at >= clock_timestamp() - ($3::double precision * INTERVAL '1 millisecond')
       ) AS fresh`,
      [budgetId, feedEpoch, maxAgeMs],
    );
    return result.rows[0]?.fresh ?? false;
  }

  async applySnapshot(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
  ): Promise<BudgetProjectionSnapshot> {
    validateBudgetId(budgetId);
    validateFeedEpoch(feedEpoch);
    validateRecords(records);

    const client = await this.pool.connect();
    let transactionOpen = false;
    let destroyClient = false;
    let primaryError: unknown;
    let rollbackFailed = false;
    let rollbackError: unknown;
    try {
      await client.query("BEGIN");
      transactionOpen = true;
      await client.query(
        `INSERT INTO companion_budget_sync_state
          (budget_id, generation, feed_epoch, cursor)
         VALUES ($1, $2, $3, 0)
         ON CONFLICT (budget_id) DO NOTHING`,
        [budgetId, randomUUID(), feedEpoch],
      );
      const stateResult = await client.query<SyncStateRow>(
        `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
         FROM companion_budget_sync_state
         WHERE budget_id = $1
         FOR UPDATE`,
        [budgetId],
      );
      const state = stateResult.rows[0];
      if (!state) {
        throw new Error("Budget synchronization state was not created");
      }

      let generation = state.generation;
      let cursor = BigInt(state.cursor);
      if (state.feed_epoch !== feedEpoch) {
        generation = randomUUID();
        cursor = 0n;
        await client.query(
          `DELETE FROM companion_budget_changes WHERE budget_id = $1`,
          [budgetId],
        );
        await client.query(
          `UPDATE companion_budget_sync_state
           SET generation = $2, feed_epoch = $3, cursor = 0
           WHERE budget_id = $1`,
          [budgetId, generation, feedEpoch],
        );
      }

      const currentResult = await client.query<CurrentRecordRow>(
        `SELECT entity_type, entity_id, revision, body, is_deleted
         FROM companion_budget_records
         WHERE budget_id = $1
         ORDER BY entity_type, entity_id
         FOR UPDATE`,
        [budgetId],
      );
      const current = new Map<string, CurrentRecordRow>();
      for (const row of currentResult.rows) {
        current.set(recordKey(row.entity_type, row.entity_id), row);
      }
      const incomingKeys = new Set<string>();
      const sortedRecords = [...records].sort(compareRecords);

      for (const record of sortedRecords) {
        const key = recordKey(record.type, record.id);
        incomingKeys.add(key);
        const previous = current.get(key);
        if (
          previous &&
          !previous.is_deleted &&
          previous.revision === record.revision
        ) {
          continue;
        }

        cursor += 1n;
        await client.query(
          `INSERT INTO companion_budget_records
            (budget_id, entity_type, entity_id, revision, body, is_deleted, updated_cursor)
           VALUES ($1, $2, $3, $4, $5::jsonb, false, $6::bigint)
           ON CONFLICT (budget_id, entity_type, entity_id) DO UPDATE SET
             revision = EXCLUDED.revision,
             body = EXCLUDED.body,
             is_deleted = false,
             updated_cursor = EXCLUDED.updated_cursor,
             updated_at = clock_timestamp()`,
          [
            budgetId,
            record.type,
            record.id,
            record.revision,
            JSON.stringify(record.body),
            cursor.toString(),
          ],
        );
        await client.query(
          `INSERT INTO companion_budget_changes
            (budget_id, cursor, entity_type, entity_id, operation, revision, body)
           VALUES ($1, $2::bigint, $3, $4, 'upsert', $5, $6::jsonb)`,
          [
            budgetId,
            cursor.toString(),
            record.type,
            record.id,
            record.revision,
            JSON.stringify(record.body),
          ],
        );
      }

      for (const [key, previous] of current) {
        if (previous.is_deleted || incomingKeys.has(key)) {
          continue;
        }
        cursor += 1n;
        const revision = createDeletionRevision(
          previous.entity_type,
          previous.entity_id,
        );
        await client.query(
          `UPDATE companion_budget_records
           SET revision = $4, body = NULL, is_deleted = true,
               updated_cursor = $5::bigint, updated_at = clock_timestamp()
           WHERE budget_id = $1 AND entity_type = $2 AND entity_id = $3`,
          [
            budgetId,
            previous.entity_type,
            previous.entity_id,
            revision,
            cursor.toString(),
          ],
        );
        await client.query(
          `INSERT INTO companion_budget_changes
            (budget_id, cursor, entity_type, entity_id, operation, revision, body)
           VALUES ($1, $2::bigint, $3, $4, 'delete', $5, NULL)`,
          [
            budgetId,
            cursor.toString(),
            previous.entity_type,
            previous.entity_id,
            revision,
          ],
        );
      }

      await client.query(
        `UPDATE companion_budget_sync_state
         SET cursor = $2::bigint, refreshed_at = clock_timestamp()
         WHERE budget_id = $1`,
        [budgetId, cursor.toString()],
      );
      const oldestRetainedCursor = cursor - BigInt(this.changeRetention);
      if (oldestRetainedCursor > 0n) {
        await client.query(
          `DELETE FROM companion_budget_changes
           WHERE budget_id = $1 AND cursor <= $2::bigint`,
          [budgetId, oldestRetainedCursor.toString()],
        );
      }

      const snapshot = await readSnapshotOnClient(client, budgetId);
      if (!snapshot) {
        throw new Error(
          "Budget synchronization state disappeared during refresh",
        );
      }
      await client.query("COMMIT");
      transactionOpen = false;
      return snapshot;
    } catch (error) {
      primaryError = error;
      if (transactionOpen) {
        try {
          await client.query("ROLLBACK");
          transactionOpen = false;
        } catch (errorDuringRollback) {
          rollbackFailed = true;
          rollbackError = errorDuringRollback;
          destroyClient = true;
        }
      }
    } finally {
      client.release(destroyClient);
    }

    if (rollbackFailed) {
      reportRollbackFailure(primaryError, rollbackError);
    }
    throw primaryError;
  }

  async readSnapshot(
    budgetId: string,
  ): Promise<BudgetProjectionSnapshot | undefined> {
    validateBudgetId(budgetId);
    const client = await this.pool.connect();
    let transactionOpen = false;
    let destroyClient = false;
    try {
      await client.query("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY");
      transactionOpen = true;
      const snapshot = await readSnapshotOnClient(client, budgetId);
      await client.query("COMMIT");
      transactionOpen = false;
      return snapshot;
    } catch (error) {
      if (transactionOpen) {
        try {
          await client.query("ROLLBACK");
        } catch (rollbackError) {
          destroyClient = true;
          reportRollbackFailure(error, rollbackError);
        }
      }
      throw error;
    } finally {
      client.release(destroyClient);
    }
  }

  async readChanges(input: {
    budgetId: string;
    generation: string;
    cursor: string;
    throughCursor?: string;
    limit: number;
  }): Promise<BudgetProjectionChangePage | undefined> {
    validateBudgetId(input.budgetId);
    validateGeneration(input.generation);
    const cursor = parseCursor(input.cursor, "cursor");
    const through =
      input.throughCursor === undefined
        ? undefined
        : parseCursor(input.throughCursor, "through cursor");
    if (through !== undefined && through < cursor) {
      throw new BudgetProjectionInputError(
        "throughCursor must be greater than or equal to cursor",
      );
    }
    if (
      !Number.isSafeInteger(input.limit) ||
      input.limit < 1 ||
      input.limit > MAX_BUDGET_CHANGE_PAGE_SIZE
    ) {
      throw new BudgetProjectionInputError(
        `limit must be between 1 and ${MAX_BUDGET_CHANGE_PAGE_SIZE}`,
      );
    }

    const client = await this.pool.connect();
    let transactionOpen = false;
    let destroyClient = false;
    try {
      await client.query("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY");
      transactionOpen = true;
      const stateResult = await client.query<SyncStateRow>(
        `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
         FROM companion_budget_sync_state WHERE budget_id = $1`,
        [input.budgetId],
      );
      const state = stateResult.rows[0];
      if (!state) {
        await client.query("COMMIT");
        transactionOpen = false;
        return undefined;
      }
      const current = BigInt(state.cursor);
      const highWater = through ?? current;
      const resetRequired =
        input.generation !== state.generation ||
        cursor > current ||
        highWater > current;
      if (resetRequired) {
        await client.query("COMMIT");
        transactionOpen = false;
        return emptyResetPage(input.budgetId, state.generation, current);
      }

      const minimumResult = await client.query<{ min_cursor: string | null }>(
        `SELECT MIN(cursor)::text AS min_cursor
         FROM companion_budget_changes WHERE budget_id = $1`,
        [input.budgetId],
      );
      const minimum = minimumResult.rows[0]?.min_cursor;
      if (
        cursor < current &&
        (minimum === null ||
          minimum === undefined ||
          BigInt(minimum) > cursor + 1n)
      ) {
        await client.query("COMMIT");
        transactionOpen = false;
        return emptyResetPage(input.budgetId, state.generation, current);
      }

      const changeResult = await client.query<ChangeRow>(
        `SELECT cursor::text, entity_type, entity_id, operation, revision, body
         FROM companion_budget_changes
         WHERE budget_id = $1 AND cursor > $2::bigint AND cursor <= $3::bigint
         ORDER BY cursor ASC
         LIMIT $4`,
        [
          input.budgetId,
          cursor.toString(),
          highWater.toString(),
          input.limit + 1,
        ],
      );
      const hasMore = changeResult.rows.length > input.limit;
      const rows = hasMore
        ? changeResult.rows.slice(0, input.limit)
        : changeResult.rows;
      const changes = rows.map(toChange);
      const nextCursor = changes.at(-1)?.cursor ?? cursor.toString();
      await client.query("COMMIT");
      transactionOpen = false;
      return {
        budgetId: input.budgetId,
        generation: state.generation,
        currentCursor: current.toString(),
        throughCursor: highWater.toString(),
        nextCursor,
        hasMore,
        resetRequired: false,
        changes,
      };
    } catch (error) {
      if (transactionOpen) {
        try {
          await client.query("ROLLBACK");
        } catch (rollbackError) {
          destroyClient = true;
          reportRollbackFailure(error, rollbackError);
        }
      }
      throw error;
    } finally {
      client.release(destroyClient);
    }
  }
}

async function readSnapshotOnClient(
  client: PoolClient,
  budgetId: string,
): Promise<BudgetProjectionSnapshot | undefined> {
  const stateResult = await client.query<SyncStateRow>(
    `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
     FROM companion_budget_sync_state WHERE budget_id = $1`,
    [budgetId],
  );
  const state = stateResult.rows[0];
  if (!state) {
    return undefined;
  }
  const recordResult = await client.query<CurrentRecordRow>(
    `SELECT entity_type, entity_id, revision, body, is_deleted
     FROM companion_budget_records
     WHERE budget_id = $1 AND is_deleted = false
     ORDER BY entity_type, entity_id`,
    [budgetId],
  );
  return {
    budgetId,
    generation: state.generation,
    cursor: state.cursor,
    records: recordResult.rows.map((row) => ({
      type: row.entity_type,
      id: row.entity_id,
      revision: row.revision,
      body: requireJsonObject(row.body),
    })),
  };
}

function toChange(row: ChangeRow): BudgetProjectionChange {
  return {
    cursor: row.cursor,
    type: row.entity_type,
    id: row.entity_id,
    operation: row.operation,
    revision: row.revision,
    body: row.body === null ? null : requireJsonObject(row.body),
  };
}

function emptyResetPage(
  budgetId: string,
  generation: string,
  currentCursor: bigint,
): BudgetProjectionChangePage {
  const cursor = currentCursor.toString();
  return {
    budgetId,
    generation,
    currentCursor: cursor,
    throughCursor: cursor,
    nextCursor: cursor,
    hasMore: false,
    resetRequired: true,
    changes: [],
  };
}

function validateRecords(records: readonly BudgetProjectionRecord[]): void {
  if (!Array.isArray(records)) {
    throw new BudgetProjectionInputError("Budget records must be an array");
  }
  const allowedTypes = new Set<string>(BUDGET_ENTITY_TYPES);
  const keys = new Set<string>();
  for (const record of records) {
    if (!record || typeof record !== "object" || Array.isArray(record)) {
      throw new BudgetProjectionInputError("Budget record must be an object");
    }
    if (!allowedTypes.has(record.type)) {
      throw new BudgetProjectionInputError("Budget record type is invalid");
    }
    validateEntityId(record.id);
    if (!/^[a-f0-9]{64}$/.test(record.revision)) {
      throw new BudgetProjectionInputError("Budget record revision is invalid");
    }
    if (
      !record.body ||
      typeof record.body !== "object" ||
      Array.isArray(record.body)
    ) {
      throw new BudgetProjectionInputError(
        "Budget record body must be an object",
      );
    }
    const key = recordKey(record.type, record.id);
    if (keys.has(key)) {
      throw new BudgetProjectionInputError(
        "Budget snapshot contains duplicate IDs",
      );
    }
    keys.add(key);
  }
}

function validateBudgetId(budgetId: string): void {
  if (
    typeof budgetId !== "string" ||
    budgetId.trim().length === 0 ||
    budgetId.length > 500 ||
    containsControlCharacters(budgetId)
  ) {
    throw new BudgetProjectionInputError("Budget ID is invalid");
  }
}

function validateEntityId(id: string): void {
  if (
    typeof id !== "string" ||
    id.trim().length === 0 ||
    id.length > 500 ||
    containsControlCharacters(id)
  ) {
    throw new BudgetProjectionInputError("Budget entity ID is invalid");
  }
}

function containsControlCharacters(value: string): boolean {
  for (const character of value) {
    const code = character.charCodeAt(0);
    if (code <= 0x1f || code === 0x7f) {
      return true;
    }
  }
  return false;
}

function validateFeedEpoch(feedEpoch: string): void {
  if (
    typeof feedEpoch !== "string" ||
    !/^[A-Za-z0-9._:-]{1,100}$/.test(feedEpoch)
  ) {
    throw new BudgetProjectionInputError("Change feed epoch is invalid");
  }
}

function validateGeneration(generation: string): void {
  if (typeof generation !== "string" || !/^[0-9a-f-]{36}$/.test(generation)) {
    throw new BudgetProjectionInputError("Change feed generation is invalid");
  }
}

function parseCursor(value: string, description: string): bigint {
  if (typeof value !== "string" || !/^(0|[1-9][0-9]*)$/.test(value)) {
    throw new BudgetProjectionInputError(
      `${description} must be a decimal cursor`,
    );
  }
  return BigInt(value);
}

function compareRecords(
  left: BudgetProjectionRecord,
  right: BudgetProjectionRecord,
): number {
  if (left.type !== right.type) {
    return left.type.localeCompare(right.type);
  }
  return left.id.localeCompare(right.id);
}

function recordKey(type: string, id: string): string {
  return `${type}\u0000${id}`;
}

function createDeletionRevision(type: string, id: string): string {
  return createHash("sha256")
    .update(`deleted\u0000${type}\u0000${id}`)
    .digest("hex");
}

function requireJsonObject(value: Record<string, unknown> | null): {
  [key: string]: import("../write-journal/contracts.js").JsonValue;
} {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Budget projection contains invalid JSON data");
  }
  return value as {
    [key: string]: import("../write-journal/contracts.js").JsonValue;
  };
}

function reportRollbackFailure(
  primaryError: unknown,
  rollbackError: unknown,
): void {
  try {
    console.error("Budget projection rollback failed", {
      primaryError:
        primaryError instanceof Error ? primaryError.name : typeof primaryError,
      rollbackError:
        rollbackError instanceof Error
          ? rollbackError.name
          : typeof rollbackError,
    });
  } catch {
    // Preserve the projection error if diagnostic logging also fails.
  }
}

export function createPostgresBudgetProjectionStore(
  pool: Pool,
  changeRetention = MAX_RETAINED_BUDGET_CHANGES,
): BudgetProjectionStore {
  return new PostgresBudgetProjectionStore(pool, changeRetention);
}

import { createHash, randomUUID } from "node:crypto";
import { Buffer } from "node:buffer";
import type { Pool, PoolClient } from "pg";
import {
  BUDGET_ENTITY_TYPES,
  BudgetProjectionInputError,
  type BudgetEntityType,
  type BudgetProjectionChange,
  type BudgetProjectionChangePage,
  type BudgetProjectionCheckpoint,
  type BudgetProjectionRecord,
  type BudgetProjectionSnapshot,
  type BudgetProjectionSnapshotPage,
  type BudgetProjectionSnapshotPageRequest,
  type BudgetProjectionStore,
} from "./contracts.js";

export const MAX_BUDGET_CHANGE_PAGE_SIZE = 500;
export const MAX_BUDGET_SNAPSHOT_PAGE_SIZE = 2_000;
export const MAX_RETAINED_BUDGET_CHANGES = 100_000;
export const MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE = 500;

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

interface CurrentRecordStateRow {
  entity_type: BudgetEntityType;
  entity_id: string;
  revision: string;
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

interface PendingUpsert {
  record: BudgetProjectionRecord;
  cursor: bigint;
}

interface PendingDelete {
  entityType: BudgetEntityType;
  entityId: string;
  revision: string;
  cursor: bigint;
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
         FROM tandem_budget_sync_state
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
    return this.applySnapshotInternal(budgetId, feedEpoch, records, true);
  }

  async applySnapshotForRefresh(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
  ): Promise<BudgetProjectionCheckpoint> {
    return this.applySnapshotInternal(budgetId, feedEpoch, records, false);
  }

  private applySnapshotInternal(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
    includeSnapshotRecords: true,
  ): Promise<BudgetProjectionSnapshot>;
  private applySnapshotInternal(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
    includeSnapshotRecords: false,
  ): Promise<BudgetProjectionCheckpoint>;
  private async applySnapshotInternal(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
    includeSnapshotRecords: boolean,
  ): Promise<BudgetProjectionSnapshot | BudgetProjectionCheckpoint> {
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
        `INSERT INTO tandem_budget_sync_state
          (budget_id, generation, feed_epoch, cursor)
         VALUES ($1, $2, $3, 0)
         ON CONFLICT (budget_id) DO NOTHING`,
        [budgetId, randomUUID(), feedEpoch],
      );
      const stateResult = await client.query<SyncStateRow>(
        `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
         FROM tandem_budget_sync_state
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
          `DELETE FROM tandem_budget_changes WHERE budget_id = $1`,
          [budgetId],
        );
        await client.query(
          `DELETE FROM tandem_budget_records
           WHERE budget_id = $1 AND is_deleted = true`,
          [budgetId],
        );
        await client.query(
          `UPDATE tandem_budget_sync_state
           SET generation = $2, feed_epoch = $3, cursor = 0
           WHERE budget_id = $1`,
          [budgetId, generation, feedEpoch],
        );
      }

      const currentResult = await client.query<CurrentRecordStateRow>(
        `SELECT entity_type, entity_id, revision, is_deleted
         FROM tandem_budget_records
         WHERE budget_id = $1`,
        [budgetId],
      );
      const current = new Map<string, CurrentRecordStateRow>();
      currentResult.rows.sort(compareCurrentRecordRows);
      for (const row of currentResult.rows) {
        current.set(recordKey(row.entity_type, row.entity_id), row);
      }
      const incomingKeys = new Set<string>();
      const sortedRecords = [...records].sort(compareRecords);
      const upserts: PendingUpsert[] = [];

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
        upserts.push({ record, cursor });
      }

      const deletions: PendingDelete[] = [];
      for (const [key, previous] of current) {
        if (previous.is_deleted || incomingKeys.has(key)) {
          continue;
        }
        cursor += 1n;
        const revision = createDeletionRevision(
          previous.entity_type,
          previous.entity_id,
        );
        deletions.push({
          entityType: previous.entity_type,
          entityId: previous.entity_id,
          revision,
          cursor,
        });
      }

      await writeUpsertBatches(client, budgetId, upserts);
      await writeDeleteBatches(client, budgetId, deletions);

      await client.query(
        `UPDATE tandem_budget_sync_state
         SET cursor = $2::bigint, refreshed_at = clock_timestamp()
         WHERE budget_id = $1`,
        [budgetId, cursor.toString()],
      );
      const oldestRetainedCursor = cursor - BigInt(this.changeRetention);
      if (oldestRetainedCursor > 0n) {
        await client.query(
          `DELETE FROM tandem_budget_changes
           WHERE budget_id = $1 AND cursor <= $2::bigint`,
          [budgetId, oldestRetainedCursor.toString()],
        );
        await client.query(
          `DELETE FROM tandem_budget_records
           WHERE budget_id = $1 AND is_deleted = true
             AND updated_cursor <= $2::bigint`,
          [budgetId, oldestRetainedCursor.toString()],
        );
      }

      let result: BudgetProjectionSnapshot | BudgetProjectionCheckpoint;
      if (includeSnapshotRecords) {
        result = await readSnapshotRecordsOnClient(
          client,
          budgetId,
          generation,
          cursor.toString(),
        );
      } else {
        result = { budgetId, generation, cursor: cursor.toString() };
      }
      await client.query("COMMIT");
      transactionOpen = false;
      return result;
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

  async readSnapshotPage(
    input: BudgetProjectionSnapshotPageRequest,
  ): Promise<BudgetProjectionSnapshotPage | undefined> {
    validateBudgetId(input.budgetId);
    validateGeneration(input.generation);
    const cursor = parseCursor(input.cursor, "snapshot cursor");
    if (
      !Number.isSafeInteger(input.limit) ||
      input.limit < 1 ||
      input.limit > MAX_BUDGET_SNAPSHOT_PAGE_SIZE
    ) {
      throw new BudgetProjectionInputError(
        `limit must be between 1 and ${MAX_BUDGET_SNAPSHOT_PAGE_SIZE}`,
      );
    }
    if ((input.afterType === undefined) !== (input.afterId === undefined)) {
      throw new BudgetProjectionInputError(
        "afterType and afterId must be provided together",
      );
    }
    if (input.afterType !== undefined) {
      if (
        !(BUDGET_ENTITY_TYPES as readonly string[]).includes(input.afterType)
      ) {
        throw new BudgetProjectionInputError("afterType is invalid");
      }
      validateEntityId(input.afterId!);
    }

    const client = await this.pool.connect();
    let transactionOpen = false;
    let destroyClient = false;
    try {
      await client.query("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY");
      transactionOpen = true;
      const stateResult = await client.query<SyncStateRow>(
        `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
         FROM tandem_budget_sync_state WHERE budget_id = $1`,
        [input.budgetId],
      );
      const state = stateResult.rows[0];
      if (!state) {
        await client.query("COMMIT");
        transactionOpen = false;
        return undefined;
      }
      const currentCursor = BigInt(state.cursor);
      if (input.generation !== state.generation || cursor !== currentCursor) {
        await client.query("COMMIT");
        transactionOpen = false;
        return {
          budgetId: input.budgetId,
          generation: state.generation,
          cursor: currentCursor.toString(),
          records: [],
          hasMore: false,
          resetRequired: true,
          nextAfterType: null,
          nextAfterId: null,
        };
      }

      const recordResult =
        input.afterType === undefined
          ? await client.query<CurrentRecordRow>(
              `SELECT entity_type, entity_id, revision, body, is_deleted
               FROM tandem_budget_records
               WHERE budget_id = $1 AND is_deleted = false
               ORDER BY entity_type COLLATE "C", entity_id COLLATE "C"
               LIMIT $2`,
              [input.budgetId, input.limit + 1],
            )
          : await client.query<CurrentRecordRow>(
              `SELECT entity_type, entity_id, revision, body, is_deleted
               FROM tandem_budget_records
               WHERE budget_id = $1 AND is_deleted = false
                 AND (entity_type COLLATE "C", entity_id COLLATE "C") >
                     ($2::text COLLATE "C", $3::text COLLATE "C")
               ORDER BY entity_type COLLATE "C", entity_id COLLATE "C"
               LIMIT $4`,
              [input.budgetId, input.afterType, input.afterId, input.limit + 1],
            );
      const hasMore = recordResult.rows.length > input.limit;
      const rows = hasMore
        ? recordResult.rows.slice(0, input.limit)
        : recordResult.rows;
      const last = rows.at(-1);
      await client.query("COMMIT");
      transactionOpen = false;
      return {
        budgetId: input.budgetId,
        generation: state.generation,
        cursor: currentCursor.toString(),
        records: rows.map((row) => ({
          type: row.entity_type,
          id: row.entity_id,
          revision: row.revision,
          body: requireJsonObject(row.body),
        })),
        hasMore,
        resetRequired: false,
        nextAfterType: hasMore ? last!.entity_type : null,
        nextAfterId: hasMore ? last!.entity_id : null,
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
         FROM tandem_budget_sync_state WHERE budget_id = $1`,
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
         FROM tandem_budget_changes WHERE budget_id = $1`,
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
        `SELECT change.cursor::text, change.entity_type, change.entity_id,
                change.operation, change.revision,
                CASE
                  WHEN change.body IS NOT NULL THEN change.body
                  WHEN change.operation = 'upsert'
                       AND current.updated_cursor = change.cursor
                       AND current.revision = change.revision
                       AND current.is_deleted = false
                    THEN current.body
                  ELSE NULL
                END AS body
         FROM tandem_budget_changes AS change
         LEFT JOIN tandem_budget_records AS current
           ON current.budget_id = change.budget_id
          AND current.entity_type = change.entity_type
          AND current.entity_id = change.entity_id
         WHERE change.budget_id = $1
           AND change.cursor > $2::bigint
           AND change.cursor <= $3::bigint
         ORDER BY change.cursor ASC
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

async function writeUpsertBatches(
  client: PoolClient,
  budgetId: string,
  upserts: readonly PendingUpsert[],
): Promise<void> {
  for (
    let offset = 0;
    offset < upserts.length;
    offset += MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE
  ) {
    const batch = upserts.slice(
      offset,
      offset + MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE,
    );
    const types = batch.map(({ record }) => record.type);
    const ids = batch.map(({ record }) => record.id);
    const revisions = batch.map(({ record }) => record.revision);
    const bodies = batch.map(({ record }) => JSON.stringify(record.body));
    const cursors = batch.map(({ cursor }) => cursor.toString());

    await preserveSupersededBodies(client, budgetId, types, ids);
    await client.query(
      `INSERT INTO tandem_budget_records
        (budget_id, entity_type, entity_id, revision, body, is_deleted, updated_cursor)
       SELECT $1, incoming.entity_type, incoming.entity_id, incoming.revision,
              incoming.body::jsonb, false, incoming.updated_cursor
       FROM unnest(
         $2::text[], $3::text[], $4::text[], $5::text[], $6::bigint[]
       ) AS incoming(entity_type, entity_id, revision, body, updated_cursor)
       ON CONFLICT (budget_id, entity_type, entity_id) DO UPDATE SET
         revision = EXCLUDED.revision,
         body = EXCLUDED.body,
         is_deleted = false,
         updated_cursor = EXCLUDED.updated_cursor,
         updated_at = clock_timestamp()`,
      [budgetId, types, ids, revisions, bodies, cursors],
    );
    await client.query(
      `INSERT INTO tandem_budget_changes
        (budget_id, cursor, entity_type, entity_id, operation, revision, body)
       SELECT $1, incoming.cursor, incoming.entity_type, incoming.entity_id,
              'upsert', incoming.revision, NULL
       FROM unnest(
         $2::bigint[], $3::text[], $4::text[], $5::text[]
       ) AS incoming(cursor, entity_type, entity_id, revision)`,
      [budgetId, cursors, types, ids, revisions],
    );
  }
}

async function writeDeleteBatches(
  client: PoolClient,
  budgetId: string,
  deletions: readonly PendingDelete[],
): Promise<void> {
  for (
    let offset = 0;
    offset < deletions.length;
    offset += MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE
  ) {
    const batch = deletions.slice(
      offset,
      offset + MAX_BUDGET_PROJECTION_WRITE_BATCH_SIZE,
    );
    const types = batch.map(({ entityType }) => entityType);
    const ids = batch.map(({ entityId }) => entityId);
    const revisions = batch.map(({ revision }) => revision);
    const cursors = batch.map(({ cursor }) => cursor.toString());

    await preserveSupersededBodies(client, budgetId, types, ids);
    await client.query(
      `UPDATE tandem_budget_records AS existing
       SET revision = incoming.revision,
           body = NULL,
           is_deleted = true,
           updated_cursor = incoming.updated_cursor,
           updated_at = clock_timestamp()
       FROM unnest(
         $2::text[], $3::text[], $4::text[], $5::bigint[]
       ) AS incoming(entity_type, entity_id, revision, updated_cursor)
       WHERE existing.budget_id = $1
         AND existing.entity_type = incoming.entity_type
         AND existing.entity_id = incoming.entity_id`,
      [budgetId, types, ids, revisions, cursors],
    );
    await client.query(
      `INSERT INTO tandem_budget_changes
        (budget_id, cursor, entity_type, entity_id, operation, revision, body)
       SELECT $1, incoming.cursor, incoming.entity_type, incoming.entity_id,
              'delete', incoming.revision, NULL
       FROM unnest(
         $2::bigint[], $3::text[], $4::text[], $5::text[]
       ) AS incoming(cursor, entity_type, entity_id, revision)`,
      [budgetId, cursors, types, ids, revisions],
    );
  }
}

async function preserveSupersededBodies(
  client: PoolClient,
  budgetId: string,
  entityTypes: readonly BudgetEntityType[],
  entityIds: readonly string[],
): Promise<void> {
  await client.query(
    `UPDATE tandem_budget_changes AS change
     SET body = current.body
     FROM tandem_budget_records AS current,
          unnest($2::text[], $3::text[]) AS incoming(entity_type, entity_id)
     WHERE current.budget_id = $1
       AND current.entity_type = incoming.entity_type
       AND current.entity_id = incoming.entity_id
       AND current.is_deleted = false
       AND change.budget_id = current.budget_id
       AND change.cursor = current.updated_cursor
       AND change.entity_type = current.entity_type
       AND change.entity_id = current.entity_id
       AND change.operation = 'upsert'
       AND change.body IS NULL`,
    [budgetId, entityTypes, entityIds],
  );
}

async function readSnapshotOnClient(
  client: PoolClient,
  budgetId: string,
): Promise<BudgetProjectionSnapshot | undefined> {
  const stateResult = await client.query<SyncStateRow>(
    `SELECT budget_id, generation, feed_epoch, cursor::text, refreshed_at
     FROM tandem_budget_sync_state WHERE budget_id = $1`,
    [budgetId],
  );
  const state = stateResult.rows[0];
  if (!state) {
    return undefined;
  }
  return readSnapshotRecordsOnClient(
    client,
    budgetId,
    state.generation,
    state.cursor,
  );
}

async function readSnapshotRecordsOnClient(
  client: PoolClient,
  budgetId: string,
  generation: string,
  cursor: string,
): Promise<BudgetProjectionSnapshot> {
  const recordResult = await client.query<CurrentRecordRow>(
    `SELECT entity_type, entity_id, revision, body, is_deleted
     FROM tandem_budget_records
     WHERE budget_id = $1 AND is_deleted = false
     ORDER BY entity_type COLLATE "C", entity_id COLLATE "C"`,
    [budgetId],
  );
  return {
    budgetId,
    generation,
    cursor,
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
    return compareUtf8Binary(left.type, right.type);
  }
  return compareUtf8Binary(left.id, right.id);
}

function compareCurrentRecordRows(
  left: CurrentRecordStateRow,
  right: CurrentRecordStateRow,
): number {
  if (left.entity_type !== right.entity_type) {
    return compareUtf8Binary(left.entity_type, right.entity_type);
  }
  return compareUtf8Binary(left.entity_id, right.entity_id);
}

function compareUtf8Binary(left: string, right: string): number {
  return Buffer.compare(Buffer.from(left, "utf8"), Buffer.from(right, "utf8"));
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

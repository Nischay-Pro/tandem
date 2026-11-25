import type { Pool, PoolClient } from "pg";

/** A planning record exactly as the device sealed it; the service never sees its contents. */
export interface SealedRecord {
  recordId: string;
  recordType: string;
  schemaVersion: number;
  revision: number;
  deleted: boolean;
  iv: string;
  ciphertext: string;
  seq: number;
}

export interface RecordWrite {
  recordType: string;
  schemaVersion: number;
  revision: number;
  baseRevision: number | null;
  deleted: boolean;
  iv: string;
  ciphertext: string;
}

export type WriteResult =
  | { status: "stored"; seq: number }
  | { status: "conflict"; current: SealedRecord | undefined };

/** The budget's planning key, wrapped by a key only the budget's members can derive. */
export interface WrappedKey {
  keyId: string;
  wrappedKey: string;
  wrapSalt: string;
  keyCheck: string;
}

export interface ChangePage {
  records: SealedRecord[];
  cursor: number;
  hasMore: boolean;
  /** True when this budget no longer holds the write the device's cursor points at, as after restoring a backup. */
  restored: boolean;
}

export interface ExtensionStore {
  ping(): Promise<void>;
  changes(
    scope: string,
    after: number,
    limit: number,
    anchor?: string,
  ): Promise<ChangePage>;
  write(
    scope: string,
    recordId: string,
    write: RecordWrite,
  ): Promise<WriteResult>;
  key(scope: string): Promise<WrappedKey | undefined>;
  putKey(
    scope: string,
    key: WrappedKey,
    expectedKeyId: string | null,
  ): Promise<boolean>;
  close(): Promise<void>;
}

/** Accepts a write only as the next revision of what the server holds, so concurrent edits surface as conflicts. */
function acceptable(current: SealedRecord | undefined, write: RecordWrite) {
  const base = current?.revision ?? null;
  return write.baseRevision === base && write.revision === (base ?? 0) + 1;
}

export class InMemoryExtensionStore implements ExtensionStore {
  private readonly records = new Map<string, Map<string, SealedRecord>>();
  private readonly keys = new Map<string, WrappedKey>();
  private readonly writes = new Map<string, Map<number, string>>();
  private seq = 0;

  async ping() {}

  async changes(scope: string, after: number, limit: number, anchor?: string) {
    const scoped = [...(this.records.get(scope)?.values() ?? [])];
    const all = scoped
      .filter((record) => record.seq > after)
      .sort((a, b) => a.seq - b.seq);
    const records = all.slice(0, limit);
    const restored =
      after > 0 &&
      (anchor === undefined
        ? !scoped.some((record) => record.seq >= after)
        : this.writes.get(scope)?.get(after) !== anchor);
    return {
      records,
      cursor: records.at(-1)?.seq ?? after,
      hasMore: all.length > limit,
      restored,
    };
  }

  async write(scope: string, recordId: string, write: RecordWrite) {
    const scoped = this.records.get(scope) ?? new Map<string, SealedRecord>();
    this.records.set(scope, scoped);
    const current = scoped.get(recordId);
    if (!acceptable(current, write))
      return { status: "conflict" as const, current };
    const stored = { recordId, ...withoutBase(write), seq: ++this.seq };
    scoped.set(recordId, stored);
    const log = this.writes.get(scope) ?? new Map<number, string>();
    this.writes.set(scope, log.set(stored.seq, recordId));
    return { status: "stored" as const, seq: stored.seq };
  }

  async key(scope: string) {
    return this.keys.get(scope);
  }

  async putKey(scope: string, key: WrappedKey, expectedKeyId: string | null) {
    if ((this.keys.get(scope)?.keyId ?? null) !== expectedKeyId) return false;
    this.keys.set(scope, key);
    return true;
  }

  async close() {}
}

function withoutBase(write: RecordWrite) {
  return {
    recordType: write.recordType,
    schemaVersion: write.schemaVersion,
    revision: write.revision,
    deleted: write.deleted,
    iv: write.iv,
    ciphertext: write.ciphertext,
  };
}

const SCHEMA = `
CREATE SEQUENCE IF NOT EXISTS planning_record_seq;
CREATE TABLE IF NOT EXISTS planning_records (
  scope TEXT NOT NULL,
  record_id TEXT NOT NULL,
  record_type TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  revision BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL,
  iv TEXT NOT NULL,
  ciphertext TEXT NOT NULL,
  seq BIGINT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (scope, record_id)
);
CREATE INDEX IF NOT EXISTS planning_records_scope_seq ON planning_records (scope, seq);
CREATE TABLE IF NOT EXISTS planning_writes (
  scope TEXT NOT NULL,
  seq BIGINT NOT NULL,
  record_id TEXT NOT NULL,
  PRIMARY KEY (scope, seq)
);
INSERT INTO planning_writes (scope, seq, record_id)
  SELECT scope, seq, record_id FROM planning_records ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS planning_keys (
  scope TEXT PRIMARY KEY,
  key_id TEXT NOT NULL,
  wrapped_key TEXT NOT NULL,
  wrap_salt TEXT NOT NULL,
  key_check TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);`;

interface RecordRow {
  record_id: string;
  record_type: string;
  schema_version: number;
  revision: string;
  deleted: boolean;
  iv: string;
  ciphertext: string;
  seq: string;
}

function toRecord(row: RecordRow): SealedRecord {
  return {
    recordId: row.record_id,
    recordType: row.record_type,
    schemaVersion: row.schema_version,
    revision: Number(row.revision),
    deleted: row.deleted,
    iv: row.iv,
    ciphertext: row.ciphertext,
    seq: Number(row.seq),
  };
}

export class PostgresExtensionStore implements ExtensionStore {
  constructor(private readonly pool: Pool) {}

  async ensureSchema() {
    await this.pool.query(SCHEMA);
  }

  async ping() {
    await this.pool.query("SELECT 1");
  }

  async changes(scope: string, after: number, limit: number, anchor?: string) {
    const result = await this.pool.query<RecordRow>(
      "SELECT * FROM planning_records WHERE scope = $1 AND seq > $2 ORDER BY seq LIMIT $3",
      [scope, after, limit + 1],
    );
    const records = result.rows.slice(0, limit).map(toRecord);
    const known =
      after === 0 ||
      (
        await (anchor === undefined
          ? this.pool.query(
              "SELECT 1 FROM planning_records WHERE scope = $1 AND seq >= $2 LIMIT 1",
              [scope, after],
            )
          : this.pool.query(
              "SELECT 1 FROM planning_writes WHERE scope = $1 AND seq = $2 AND record_id = $3",
              [scope, after, anchor],
            ))
      ).rows.length > 0;
    return {
      records,
      cursor: records.at(-1)?.seq ?? after,
      hasMore: result.rows.length > limit,
      restored: !known,
    };
  }

  async write(scope: string, recordId: string, write: RecordWrite) {
    return this.transaction(async (client) => {
      await lockScope(client, `records|${scope}`);
      const existing = await client.query<RecordRow>(
        "SELECT * FROM planning_records WHERE scope = $1 AND record_id = $2 FOR UPDATE",
        [scope, recordId],
      );
      const current = existing.rows[0] && toRecord(existing.rows[0]);
      if (!acceptable(current, write))
        return { status: "conflict" as const, current };
      const stored = await client.query<{ seq: string }>(
        `INSERT INTO planning_records (scope, record_id, record_type, schema_version, revision, deleted, iv, ciphertext, seq)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, nextval('planning_record_seq'))
         ON CONFLICT (scope, record_id) DO UPDATE SET
           record_type = EXCLUDED.record_type, schema_version = EXCLUDED.schema_version, revision = EXCLUDED.revision,
           deleted = EXCLUDED.deleted, iv = EXCLUDED.iv, ciphertext = EXCLUDED.ciphertext, seq = EXCLUDED.seq, updated_at = now()
         RETURNING seq`,
        [
          scope,
          recordId,
          write.recordType,
          write.schemaVersion,
          write.revision,
          write.deleted,
          write.iv,
          write.ciphertext,
        ],
      );
      const seq = Number(stored.rows[0]!.seq);
      await client.query(
        `INSERT INTO planning_writes (scope, seq, record_id) VALUES ($1, $2, $3)
         ON CONFLICT (scope, seq) DO UPDATE SET record_id = EXCLUDED.record_id`,
        [scope, seq, recordId],
      );
      return { status: "stored" as const, seq };
    });
  }

  async key(scope: string) {
    const result = await this.pool.query<{
      key_id: string;
      wrapped_key: string;
      wrap_salt: string;
      key_check: string;
    }>(
      "SELECT key_id, wrapped_key, wrap_salt, key_check FROM planning_keys WHERE scope = $1",
      [scope],
    );
    const row = result.rows[0];
    return (
      row && {
        keyId: row.key_id,
        wrappedKey: row.wrapped_key,
        wrapSalt: row.wrap_salt,
        keyCheck: row.key_check,
      }
    );
  }

  async putKey(scope: string, key: WrappedKey, expectedKeyId: string | null) {
    return this.transaction(async (client) => {
      await lockScope(client, `keys|${scope}`);
      const existing = await client.query<{ key_id: string }>(
        "SELECT key_id FROM planning_keys WHERE scope = $1 FOR UPDATE",
        [scope],
      );
      if ((existing.rows[0]?.key_id ?? null) !== expectedKeyId) return false;
      await client.query(
        `INSERT INTO planning_keys (scope, key_id, wrapped_key, wrap_salt, key_check) VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (scope) DO UPDATE SET key_id = EXCLUDED.key_id, wrapped_key = EXCLUDED.wrapped_key,
           wrap_salt = EXCLUDED.wrap_salt, key_check = EXCLUDED.key_check, updated_at = now()`,
        [scope, key.keyId, key.wrappedKey, key.wrapSalt, key.keyCheck],
      );
      return true;
    });
  }

  async close() {
    await this.pool.end();
  }

  private async transaction<T>(
    work: (client: PoolClient) => Promise<T>,
  ): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const result = await work(client);
      await client.query("COMMIT");
      return result;
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }
}

/**
 * Serialises writes to one scope until the transaction ends. Row locks can't cover a record or key that doesn't
 * exist yet, and holding this while the sequence is drawn and committed keeps each scope's change feed in commit order.
 */
const lockScope = (client: PoolClient, key: string) =>
  client.query("SELECT pg_advisory_xact_lock(hashtext($1))", [key]);

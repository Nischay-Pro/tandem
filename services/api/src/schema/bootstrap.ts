import type { Pool, PoolClient } from "pg";
import { OperatorMessageError } from "../operator-message-error.js";

const SCHEMA_LOCK_ID = "19754";

const REQUIRED_TABLES = [
  "tandem_devices",
  "tandem_actual_sessions",
  "tandem_operations",
  "tandem_operation_conflicts",
] as const;

const REQUIRED_COLUMNS: Readonly<
  Record<string, Readonly<Record<string, string>>>
> = {
  tandem_devices: {
    device_id: "uuid",
    principal_id: "text",
    device_name: "text",
    credential_hash: "bpchar",
    budget_ids: "_text",
    created_at: "timestamptz",
    last_seen_at: "timestamptz",
    revoked_at: "timestamptz",
  },
  tandem_actual_sessions: {
    principal_id: "text",
    token_ciphertext: "bytea",
    token_nonce: "bytea",
    token_tag: "bytea",
    updated_at: "timestamptz",
  },
  tandem_operations: {
    operation_id: "uuid",
    principal_id: "text",
    budget_id: "text",
    idempotency_key: "text",
    request_hash: "bpchar",
    entity_type: "text",
    action: "text",
    entity_id: "text",
    base_revision: "text",
    base_snapshot: "jsonb",
    patch: "jsonb",
    status: "text",
    attempt_count: "int4",
    error_code: "text",
    actual_result: "jsonb",
    created_at: "timestamptz",
    updated_at: "timestamptz",
    started_at: "timestamptz",
    lease_expires_at: "timestamptz",
    completed_at: "timestamptz",
    payload_compacted_at: "timestamptz",
  },
  tandem_operation_conflicts: {
    conflict_id: "uuid",
    operation_id: "uuid",
    conflict_type: "text",
    base_snapshot: "jsonb",
    local_patch: "jsonb",
    remote_snapshot: "jsonb",
    conflicting_fields: "_text",
    resolution_state: "text",
    resolution: "jsonb",
    created_at: "timestamptz",
    resolved_at: "timestamptz",
  },
};

const REQUIRED_CONSTRAINTS: Readonly<
  Record<string, Readonly<Record<string, string>>>
> = {
  tandem_devices: {
    tandem_devices_pkey: "PRIMARY KEY",
    tandem_devices_credential_hash_key: "UNIQUE",
    tandem_devices_principal_id_check: "CHECK",
    tandem_devices_device_name_check: "CHECK",
    tandem_devices_credential_hash_check: "CHECK",
    tandem_devices_budget_ids_check: "CHECK",
  },
  tandem_actual_sessions: {
    tandem_actual_sessions_pkey: "PRIMARY KEY",
    tandem_actual_sessions_principal_id_check: "CHECK",
    tandem_actual_sessions_token_nonce_check: "CHECK",
    tandem_actual_sessions_token_tag_check: "CHECK",
  },
  tandem_operations: {
    tandem_operations_pkey: "PRIMARY KEY",
    tandem_operations_principal_id_budget_id_idempotency_key_key: "UNIQUE",
    tandem_operations_principal_id_check: "CHECK",
    tandem_operations_budget_id_check: "CHECK",
    tandem_operations_idempotency_key_check: "CHECK",
    tandem_operations_entity_type_check: "CHECK",
    tandem_operations_action_check: "CHECK",
    tandem_operations_patch_check: "CHECK",
    tandem_operations_status_check: "CHECK",
    tandem_operations_attempt_count_check: "CHECK",
  },
  tandem_operation_conflicts: {
    tandem_operation_conflicts_pkey: "PRIMARY KEY",
    tandem_operation_conflicts_operation_id_key: "UNIQUE",
    tandem_operation_conflicts_operation_id_fkey: "FOREIGN KEY",
    tandem_operation_conflicts_conflict_type_check: "CHECK",
    tandem_operation_conflicts_local_patch_check: "CHECK",
    tandem_operation_conflicts_resolution_state_check: "CHECK",
    tandem_operation_conflicts_check: "CHECK",
  },
};

/**
 * Creates the 0.1.0 schema on a fresh database and safely reuses the same schema
 * on later starts. This is deliberately not a versioned migration system: it
 * never drops, renames, or alters populated tables and does not create history.
 */
export async function ensureSchema(pool: Pool): Promise<void> {
  const client = await pool.connect();
  let transactionStarted = false;
  let destroyClient = false;
  try {
    await client.query("BEGIN");
    transactionStarted = true;
    await client.query("SELECT pg_advisory_xact_lock($1::bigint)", [
      SCHEMA_LOCK_ID,
    ]);
    await assertCompatibleExistingSchema(client);
    await createSchema(client);
    await client.query("COMMIT");
    transactionStarted = false;
  } catch (error) {
    if (transactionStarted) {
      try {
        await client.query("ROLLBACK");
      } catch {
        destroyClient = true;
      }
    }
    throw error;
  } finally {
    client.release(destroyClient ? true : undefined);
  }
}

async function assertCompatibleExistingSchema(
  client: PoolClient,
): Promise<void> {
  const existing = await client.query<{
    table_name: string;
    table_type: string;
  }>(
    `SELECT table_name, table_type
       FROM information_schema.tables
      WHERE table_schema = current_schema()
        AND table_name = ANY($1::text[])`,
    [[...REQUIRED_TABLES, "tandem_migrations"]],
  );
  const names = new Set(existing.rows.map((row) => row.table_name));
  const existingCoreTables = REQUIRED_TABLES.filter((name) => names.has(name));
  if (existingCoreTables.length === 0) {
    if (names.has("tandem_migrations")) {
      throw incompatibleSchema(
        "legacy migration history exists without the complete Tandem schema",
      );
    }
    return;
  }
  const missing = REQUIRED_TABLES.filter((name) => !names.has(name));
  const nonTables = existing.rows.filter(
    (row) => row.table_type !== "BASE TABLE",
  );
  if (missing.length > 0 || nonTables.length > 0) {
    throw incompatibleSchema(
      missing.length > 0
        ? `missing required tables: ${missing.join(", ")}`
        : `non-table objects use Tandem table names: ${nonTables.map((row) => row.table_name).join(", ")}`,
    );
  }

  const columns = await client.query<{
    table_name: string;
    column_name: string;
    udt_name: string;
  }>(
    `SELECT table_name, column_name, udt_name
       FROM information_schema.columns
      WHERE table_schema = current_schema()
        AND table_name = ANY($1::text[])`,
    [REQUIRED_TABLES],
  );
  const actual = new Map(
    columns.rows.map((column) => [
      `${column.table_name}.${column.column_name}`,
      column.udt_name,
    ]),
  );
  const incompatible: string[] = [];
  for (const [tableName, expected] of Object.entries(REQUIRED_COLUMNS)) {
    for (const [columnName, expectedType] of Object.entries(expected)) {
      const key = `${tableName}.${columnName}`;
      const actualType = actual.get(key);
      if (actualType !== expectedType) {
        incompatible.push(
          actualType === undefined
            ? `${key} is missing`
            : `${key} has type ${actualType}; expected ${expectedType}`,
        );
      }
    }
  }
  if (incompatible.length > 0) {
    throw incompatibleSchema(incompatible.join("; "));
  }

  const constraints = await client.query<{
    table_name: string;
    constraint_name: string;
    constraint_type: string;
  }>(
    `SELECT table_name, constraint_name, constraint_type
       FROM information_schema.table_constraints
      WHERE table_schema = current_schema()
        AND table_name = ANY($1::text[])`,
    [REQUIRED_TABLES],
  );
  const existingConstraints = new Map(
    constraints.rows.map((constraint) => [
      `${constraint.table_name}.${constraint.constraint_name}`,
      constraint.constraint_type,
    ]),
  );
  const missingConstraints: string[] = [];
  for (const [tableName, expected] of Object.entries(REQUIRED_CONSTRAINTS)) {
    for (const [constraintName, expectedType] of Object.entries(expected)) {
      const key = `${tableName}.${constraintName}`;
      if (existingConstraints.get(key) !== expectedType) {
        missingConstraints.push(key);
      }
    }
  }
  if (missingConstraints.length > 0) {
    throw incompatibleSchema(
      `missing or changed constraints: ${missingConstraints.join(", ")}`,
    );
  }
}

function incompatibleSchema(reason: string): OperatorMessageError {
  return new OperatorMessageError(
    `The Tandem database does not match this 0.1.0 schema (${reason}). No data was changed. Back up the database and use a compatible Tandem build.`,
  );
}

async function createSchema(client: PoolClient): Promise<void> {
  await client.query(`CREATE TABLE IF NOT EXISTS tandem_devices (
    device_id uuid PRIMARY KEY,
    principal_id text NOT NULL CHECK (length(principal_id) BETWEEN 1 AND 200),
    device_name text NOT NULL CHECK (length(device_name) BETWEEN 1 AND 100),
    credential_hash char(64) NOT NULL UNIQUE CHECK (credential_hash ~ '^[a-f0-9]{64}$'),
    budget_ids text[] NOT NULL CHECK (cardinality(budget_ids) BETWEEN 0 AND 100),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_seen_at timestamptz,
    revoked_at timestamptz
  )`);
  await client.query(`CREATE INDEX IF NOT EXISTS tandem_devices_principal_created
    ON tandem_devices (principal_id, created_at, device_id)`);
  await client.query(`CREATE TABLE IF NOT EXISTS tandem_actual_sessions (
    principal_id text PRIMARY KEY CHECK (length(principal_id) BETWEEN 1 AND 200),
    token_ciphertext bytea NOT NULL,
    token_nonce bytea NOT NULL CHECK (octet_length(token_nonce) = 12),
    token_tag bytea NOT NULL CHECK (octet_length(token_tag) = 16),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
  )`);
  await client.query(`CREATE TABLE IF NOT EXISTS tandem_operations (
    operation_id uuid PRIMARY KEY,
    principal_id text NOT NULL CHECK (length(principal_id) BETWEEN 1 AND 200),
    budget_id text NOT NULL CHECK (length(budget_id) BETWEEN 1 AND 500),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    request_hash char(64) NOT NULL,
    entity_type text NOT NULL CHECK (length(entity_type) BETWEEN 1 AND 100),
    action text NOT NULL CHECK (length(action) BETWEEN 1 AND 100),
    entity_id text,
    base_revision text,
    base_snapshot jsonb NOT NULL,
    patch jsonb NOT NULL CHECK (jsonb_typeof(patch) = 'object'),
    status text NOT NULL CHECK (status IN ('queued', 'applying', 'committed', 'conflict', 'uncertain', 'rejected')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    error_code text,
    actual_result jsonb,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    started_at timestamptz,
    lease_expires_at timestamptz,
    completed_at timestamptz,
    payload_compacted_at timestamptz,
    UNIQUE (principal_id, budget_id, idempotency_key)
  )`);
  await client.query(`CREATE INDEX IF NOT EXISTS tandem_operations_budget_status_created
    ON tandem_operations (budget_id, status, created_at)`);
  await client.query(`CREATE INDEX IF NOT EXISTS tandem_operations_payload_retention
    ON tandem_operations (completed_at, operation_id)
    WHERE status IN ('committed', 'rejected') AND payload_compacted_at IS NULL`);
  await client.query(`CREATE TABLE IF NOT EXISTS tandem_operation_conflicts (
    conflict_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL UNIQUE REFERENCES tandem_operations(operation_id),
    conflict_type text NOT NULL CHECK (length(conflict_type) BETWEEN 1 AND 100),
    base_snapshot jsonb NOT NULL,
    local_patch jsonb NOT NULL CHECK (jsonb_typeof(local_patch) = 'object'),
    remote_snapshot jsonb NOT NULL,
    conflicting_fields text[] NOT NULL,
    resolution_state text NOT NULL DEFAULT 'unresolved' CHECK (resolution_state IN ('unresolved', 'resolved')),
    resolution jsonb,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    CHECK ((resolution_state = 'unresolved' AND resolution IS NULL AND resolved_at IS NULL)
      OR (resolution_state = 'resolved' AND resolution IS NOT NULL AND resolved_at IS NOT NULL))
  )`);
}

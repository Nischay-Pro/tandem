import type { PoolClient } from "pg";

export async function up(client: PoolClient): Promise<void> {
  await client.query(`CREATE TABLE companion_devices (
    device_id uuid PRIMARY KEY,
    principal_id text NOT NULL CHECK (length(principal_id) BETWEEN 1 AND 200),
    device_name text NOT NULL CHECK (length(device_name) BETWEEN 1 AND 100),
    credential_hash char(64) NOT NULL UNIQUE CHECK (credential_hash ~ '^[a-f0-9]{64}$'),
    budget_ids text[] NOT NULL CHECK (cardinality(budget_ids) BETWEEN 0 AND 100),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_seen_at timestamptz,
    revoked_at timestamptz
  )`);
  await client.query(`CREATE INDEX companion_devices_principal_created
    ON companion_devices (principal_id, created_at, device_id)`);
  await client.query(`CREATE TABLE companion_actual_sessions (
    principal_id text PRIMARY KEY CHECK (length(principal_id) BETWEEN 1 AND 200),
    token_ciphertext bytea NOT NULL,
    token_nonce bytea NOT NULL CHECK (octet_length(token_nonce) = 12),
    token_tag bytea NOT NULL CHECK (octet_length(token_tag) = 16),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
  )`);
  await client.query(`CREATE TABLE companion_operations (
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
  await client.query(`CREATE INDEX companion_operations_budget_status_created
    ON companion_operations (budget_id, status, created_at)`);
  await client.query(`CREATE INDEX companion_operations_payload_retention
    ON companion_operations (completed_at, operation_id)
    WHERE status IN ('committed', 'rejected') AND payload_compacted_at IS NULL`);
  await client.query(`CREATE TABLE companion_operation_conflicts (
    conflict_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL UNIQUE REFERENCES companion_operations(operation_id),
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
  await client.query(`CREATE TABLE companion_budget_sync_state (
    budget_id text PRIMARY KEY CHECK (length(budget_id) BETWEEN 1 AND 500),
    generation uuid NOT NULL,
    feed_epoch text NOT NULL CHECK (length(feed_epoch) BETWEEN 1 AND 100),
    cursor bigint NOT NULL DEFAULT 0 CHECK (cursor >= 0),
    refreshed_at timestamptz NOT NULL DEFAULT clock_timestamp()
  )`);
  await client.query(`CREATE TABLE companion_budget_records (
    budget_id text NOT NULL REFERENCES companion_budget_sync_state(budget_id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN (
      'account', 'account-group', 'category', 'category-group',
      'payee', 'rule', 'schedule', 'transaction', 'budget-month'
    )),
    entity_id text NOT NULL CHECK (length(entity_id) BETWEEN 1 AND 500),
    revision char(64) NOT NULL CHECK (revision ~ '^[a-f0-9]{64}$'),
    body jsonb,
    is_deleted boolean NOT NULL DEFAULT false,
    updated_cursor bigint NOT NULL CHECK (updated_cursor >= 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (budget_id, entity_type, entity_id),
    CHECK ((is_deleted AND body IS NULL)
      OR (NOT is_deleted AND body IS NOT NULL AND jsonb_typeof(body) = 'object'))
  )`);
  await client.query(`CREATE TABLE companion_budget_changes (
    budget_id text NOT NULL REFERENCES companion_budget_sync_state(budget_id) ON DELETE CASCADE,
    cursor bigint NOT NULL CHECK (cursor > 0),
    entity_type text NOT NULL CHECK (entity_type IN (
      'account', 'account-group', 'category', 'category-group',
      'payee', 'rule', 'schedule', 'transaction', 'budget-month'
    )),
    entity_id text NOT NULL CHECK (length(entity_id) BETWEEN 1 AND 500),
    operation text NOT NULL CHECK (operation IN ('upsert', 'delete')),
    revision char(64) NOT NULL CHECK (revision ~ '^[a-f0-9]{64}$'),
    body jsonb,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (budget_id, cursor),
    CHECK ((operation = 'delete' AND body IS NULL)
      OR (operation = 'upsert'
        AND (body IS NULL OR jsonb_typeof(body) = 'object')))
  )`);
  await client.query(`CREATE INDEX companion_budget_records_snapshot_keyset
    ON companion_budget_records (
      budget_id,
      entity_type COLLATE "C",
      entity_id COLLATE "C"
    ) WHERE is_deleted = false`);
  await client.query(`CREATE INDEX companion_budget_record_tombstone_retention
    ON companion_budget_records (budget_id, updated_cursor)
    WHERE is_deleted = true`);
}

export async function down(client: PoolClient): Promise<void> {
  await client.query("DROP TABLE companion_budget_changes");
  await client.query("DROP TABLE companion_budget_records");
  await client.query("DROP TABLE companion_budget_sync_state");
  await client.query("DROP TABLE companion_operation_conflicts");
  await client.query("DROP TABLE companion_operations");
  await client.query("DROP TABLE companion_actual_sessions");
  await client.query("DROP TABLE companion_devices");
}

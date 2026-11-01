import type { PoolClient } from "pg";

export async function up(client: PoolClient): Promise<void> {
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
    UNIQUE (principal_id, budget_id, idempotency_key)
  )`);
  await client.query(`CREATE INDEX companion_operations_budget_status_created
    ON companion_operations (budget_id, status, created_at)`);
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
}

export async function down(client: PoolClient): Promise<void> {
  await client.query("DROP TABLE companion_operation_conflicts");
  await client.query("DROP TABLE companion_operations");
}

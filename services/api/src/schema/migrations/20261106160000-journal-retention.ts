import type { PoolClient } from "pg";

export async function up(client: PoolClient): Promise<void> {
  await client.query(
    "ALTER TABLE companion_operations ADD COLUMN payload_compacted_at timestamptz",
  );
  await client.query(`CREATE INDEX companion_operations_payload_retention
    ON companion_operations (completed_at, operation_id)
    WHERE status IN ('committed', 'rejected') AND payload_compacted_at IS NULL`);
  await client.query(`CREATE INDEX companion_budget_record_tombstone_retention
    ON companion_budget_records (budget_id, updated_cursor)
    WHERE is_deleted = true`);
}

export async function down(client: PoolClient): Promise<void> {
  await client.query("DROP INDEX companion_budget_record_tombstone_retention");
  await client.query("DROP INDEX companion_operations_payload_retention");
  await client.query(
    "ALTER TABLE companion_operations DROP COLUMN payload_compacted_at",
  );
}

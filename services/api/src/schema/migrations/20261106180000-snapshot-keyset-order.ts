import type { PoolClient } from "pg";

export async function up(client: PoolClient): Promise<void> {
  await client.query(`CREATE INDEX companion_budget_records_snapshot_keyset
    ON companion_budget_records (
      budget_id,
      entity_type COLLATE "C",
      entity_id COLLATE "C"
    )
    WHERE is_deleted = false`);
}

export async function down(client: PoolClient): Promise<void> {
  await client.query("DROP INDEX companion_budget_records_snapshot_keyset");
}

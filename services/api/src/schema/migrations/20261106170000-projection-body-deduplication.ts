import type { PoolClient } from "pg";

export async function up(client: PoolClient): Promise<void> {
  await client.query(
    "ALTER TABLE companion_budget_changes DROP CONSTRAINT companion_budget_changes_check",
  );
  await client.query(
    `ALTER TABLE companion_budget_changes
     ADD CONSTRAINT companion_budget_changes_body_check
     CHECK ((operation = 'delete' AND body IS NULL)
       OR (operation = 'upsert'
         AND (body IS NULL OR jsonb_typeof(body) = 'object')))
     NOT VALID`,
  );
  await client.query(
    `UPDATE companion_budget_changes AS change
     SET body = NULL
     FROM companion_budget_records AS current
     WHERE change.budget_id = current.budget_id
       AND change.cursor = current.updated_cursor
       AND change.entity_type = current.entity_type
       AND change.entity_id = current.entity_id
       AND change.operation = 'upsert'
       AND current.is_deleted = false`,
  );
  await client.query(
    "ALTER TABLE companion_budget_changes VALIDATE CONSTRAINT companion_budget_changes_body_check",
  );
}

export async function down(client: PoolClient): Promise<void> {
  await client.query(
    `UPDATE companion_budget_changes AS change
     SET body = current.body
     FROM companion_budget_records AS current
     WHERE change.budget_id = current.budget_id
       AND change.cursor = current.updated_cursor
       AND change.entity_type = current.entity_type
       AND change.entity_id = current.entity_id
       AND change.operation = 'upsert'
       AND current.is_deleted = false
       AND change.body IS NULL`,
  );
  await client.query(
    "ALTER TABLE companion_budget_changes DROP CONSTRAINT companion_budget_changes_body_check",
  );
  await client.query(
    `ALTER TABLE companion_budget_changes
     ADD CONSTRAINT companion_budget_changes_check
     CHECK ((operation = 'delete' AND body IS NULL)
       OR (operation = 'upsert' AND body IS NOT NULL
         AND jsonb_typeof(body) = 'object'))`,
  );
}

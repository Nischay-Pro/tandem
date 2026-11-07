import type { PoolClient } from "pg";

const previousEntityTypes = `
  'account', 'account-group', 'category', 'category-group',
  'payee', 'transaction', 'budget-month'
`;

const currentEntityTypes = `
  'account', 'account-group', 'category', 'category-group',
  'payee', 'rule', 'schedule', 'transaction', 'budget-month'
`;

async function replaceEntityTypeConstraints(
  client: PoolClient,
  entityTypes: string,
): Promise<void> {
  for (const table of [
    "companion_budget_records",
    "companion_budget_changes",
  ]) {
    await client.query(
      `ALTER TABLE ${table}
       DROP CONSTRAINT ${table}_entity_type_check,
       ADD CONSTRAINT ${table}_entity_type_check
       CHECK (entity_type IN (${entityTypes}))`,
    );
  }
}

export async function up(client: PoolClient): Promise<void> {
  await replaceEntityTypeConstraints(client, currentEntityTypes);
}

export async function down(client: PoolClient): Promise<void> {
  await client.query(
    `DELETE FROM companion_budget_changes
     WHERE entity_type IN ('rule', 'schedule')`,
  );
  await client.query(
    `DELETE FROM companion_budget_records
     WHERE entity_type IN ('rule', 'schedule')`,
  );
  await replaceEntityTypeConstraints(client, previousEntityTypes);
}

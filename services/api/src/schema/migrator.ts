import { readFile } from "node:fs/promises";
import type { Pool, PoolClient } from "pg";
import { down, up } from "./migrations/20261031120000-durable-write-journal.js";

export interface Migration {
  name: string;
  up(client: PoolClient): Promise<void>;
  down(client: PoolClient): Promise<void>;
}

export const MIGRATIONS: readonly Migration[] = [
  {
    name: "20261031120000-durable-write-journal",
    up,
    down,
  },
];

const MIGRATION_LOCK_ID = "19754";

export async function applyMigrations(
  pool: Pool,
  migrations: readonly Migration[] = MIGRATIONS,
  order?: readonly string[],
): Promise<void> {
  const migrationOrder = order ?? (await readMigrationOrder());
  validateMigrationOrder(migrations, migrationOrder);
  const client = await pool.connect();
  let lockAcquired = false;

  try {
    await client.query("SELECT pg_advisory_lock($1::bigint)", [
      MIGRATION_LOCK_ID,
    ]);
    lockAcquired = true;
    await client.query(`CREATE TABLE IF NOT EXISTS companion_migrations (
      position integer PRIMARY KEY CHECK (position > 0),
      name text NOT NULL UNIQUE,
      applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
    )`);

    const appliedResult = await client.query<{
      position: number;
      name: string;
    }>("SELECT position, name FROM companion_migrations ORDER BY position");
    const applied = appliedResult.rows;
    for (const [index, migration] of applied.entries()) {
      if (
        migration.position !== index + 1 ||
        migrationOrder[index] !== migration.name
      ) {
        throw new Error(
          `Corrupted migrations: expected migration ${migrationOrder[index] ?? "<end>"} at position ${index + 1}, found ${migration.name}`,
        );
      }
    }

    const migrationByName = new Map(
      migrations.map((migration) => [migration.name, migration]),
    );
    for (const name of migrationOrder.slice(applied.length)) {
      const migration = migrationByName.get(name)!;
      await applyOneMigration(client, migration, applied.length + 1);
      applied.push({ position: applied.length + 1, name });
    }
  } finally {
    try {
      if (lockAcquired) {
        await client.query("SELECT pg_advisory_unlock($1::bigint)", [
          MIGRATION_LOCK_ID,
        ]);
      }
    } finally {
      client.release();
    }
  }
}

export async function readMigrationOrder(): Promise<string[]> {
  const contents = await readFile(
    new URL("./migrations/ORDER", import.meta.url),
    "utf8",
  );
  return contents
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith("#"));
}

export function validateMigrationOrder(
  migrations: readonly Migration[],
  order: readonly string[],
): void {
  const names = migrations.map(({ name }) => name);
  if (new Set(names).size !== names.length) {
    throw new Error("Migration registry contains duplicate names");
  }
  if (new Set(order).size !== order.length) {
    throw new Error("Migration ORDER contains duplicate names");
  }
  if (order.length !== names.length) {
    throw new Error("Migration ORDER and registered migrations do not match");
  }
  for (const [index, name] of order.entries()) {
    const expectedName = names[index]!;
    if (expectedName !== name) {
      throw new Error(
        `Migration ORDER mismatch at position ${index + 1}: expected ${expectedName}, found ${name}`,
      );
    }
  }
}

async function applyOneMigration(
  client: PoolClient,
  migration: Migration,
  position: number,
): Promise<void> {
  await client.query("BEGIN");
  try {
    await migration.up(client);
    await client.query(
      "INSERT INTO companion_migrations (position, name) VALUES ($1, $2)",
      [position, migration.name],
    );
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}

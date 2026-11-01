import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import { applyMigrations, type Migration } from "../src/schema/migrator.js";

function createFailingMigrationPool(
  primaryError: unknown,
  unlockError: unknown,
): {
  pool: Pool;
  release: ReturnType<typeof vi.fn>;
} {
  const release = vi.fn();
  const client = {
    async query(sql: string) {
      if (sql.includes("pg_advisory_lock")) {
        return { rows: [{ pg_advisory_lock: null }], rowCount: 1 };
      }
      if (sql.includes("CREATE TABLE IF NOT EXISTS companion_migrations")) {
        throw primaryError;
      }
      if (sql.includes("pg_advisory_unlock")) {
        throw unlockError;
      }
      throw new Error(`Unexpected migration query: ${sql}`);
    },
    release,
  };
  const pool = {
    async connect() {
      return client;
    },
  } as unknown as Pool;
  return { pool, release };
}

const migration: Migration = {
  name: "20261031120000-synthetic",
  async up() {},
  async down() {},
};

describe("migration lock cleanup reporting", () => {
  it.each([
    {
      label: "Error instances",
      primaryError: new Error("migration failed"),
      unlockError: new Error("unlock failed"),
      expectedLog: { primaryError: "Error", releaseError: "Error" },
    },
    {
      label: "non-Error rejection values",
      primaryError: "migration failed",
      unlockError: "unlock failed",
      expectedLog: { primaryError: "string", releaseError: "string" },
    },
  ])("preserves the primary error for $label", async (scenario) => {
    const { pool, release } = createFailingMigrationPool(
      scenario.primaryError,
      scenario.unlockError,
    );
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(
      applyMigrations(pool, [migration], [migration.name]),
    ).rejects.toBe(scenario.primaryError);

    expect(release).toHaveBeenCalledWith(true);
    expect(consoleError).toHaveBeenCalledWith(
      "Migration lock cleanup also failed",
      scenario.expectedLog,
    );
    vi.restoreAllMocks();
  });
});

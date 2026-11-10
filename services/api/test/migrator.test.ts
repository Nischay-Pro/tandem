import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import {
  applyMigrations,
  MIGRATIONS,
  type Migration,
} from "../src/schema/migrator.js";

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

describe("0.1.0 database schema", () => {
  it("uses one consolidated initial migration", () => {
    expect(MIGRATIONS).toHaveLength(1);
    expect(MIGRATIONS[0]?.name).toBe("20261101000000-initial-schema");
  });
});

describe("migration lock cleanup reporting", () => {
  it("explains when the database has migrations newer than this API", async () => {
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_lock")) {
        return { rows: [], rowCount: 1 };
      }
      if (sql.includes("CREATE TABLE IF NOT EXISTS companion_migrations")) {
        return { rows: [], rowCount: 0 };
      }
      if (sql.includes("SELECT position, name FROM companion_migrations")) {
        return {
          rows: [
            { position: 1, name: migration.name },
            { position: 2, name: "20261031120001-newer-migration" },
          ],
          rowCount: 2,
        };
      }
      if (sql.includes("pg_advisory_unlock")) {
        return { rows: [{ unlocked: true }], rowCount: 1 };
      }
      throw new Error(`Unexpected migration query: ${sql}`);
    });
    const pool = {
      async connect() {
        return { query, release: vi.fn() };
      },
    } as unknown as Pool;

    await expect(
      applyMigrations(pool, [migration], [migration.name]),
    ).rejects.toThrow(/database has migrations newer than this API/i);
  });

  it.each([
    {
      label: "Error instances",
      primaryError: new Error("migration failed"),
      unlockError: new Error("unlock failed"),
      expectedLog: { primaryError: "Error", cleanupError: "Error" },
    },
    {
      label: "non-Error rejection values",
      primaryError: "migration failed",
      unlockError: "unlock failed",
      expectedLog: { primaryError: "string", cleanupError: "string" },
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
      "Migration cleanup also failed",
      scenario.expectedLog,
    );
    vi.restoreAllMocks();
  });

  it("preserves a migration error and discards a client when rollback fails", async () => {
    const migrationError = new Error("synthetic migration failure");
    const rollbackError = new Error("synthetic rollback failure");
    const query = vi.fn().mockImplementation(async (sql: string) => {
      if (sql.includes("pg_advisory_unlock")) {
        return { rows: [{ unlocked: true }], rowCount: 1 };
      }
      if (sql.includes("pg_advisory_lock")) {
        return { rows: [], rowCount: 1 };
      }
      if (sql.includes("CREATE TABLE IF NOT EXISTS companion_migrations")) {
        return { rows: [], rowCount: 0 };
      }
      if (sql.includes("SELECT position, name FROM companion_migrations")) {
        return { rows: [], rowCount: 0 };
      }
      if (sql === "BEGIN") {
        return { rows: [], rowCount: 0 };
      }
      if (sql === "ROLLBACK") {
        throw rollbackError;
      }
      throw new Error(`Unexpected migration query: ${sql}`);
    });
    const release = vi.fn();
    const pool = {
      async connect() {
        return { query, release };
      },
    } as unknown as Pool;
    const brokenMigration: Migration = {
      name: "20261031120001-synthetic-rollback-failure",
      async up() {
        throw migrationError;
      },
      async down() {},
    };
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await expect(
      applyMigrations(pool, [brokenMigration], [brokenMigration.name]),
    ).rejects.toBe(migrationError);
    expect(query).toHaveBeenCalledWith("ROLLBACK");
    expect(release).toHaveBeenCalledWith(true);
    expect(consoleError).toHaveBeenCalledWith("Migration cleanup also failed", {
      primaryError: "Error",
      cleanupError: "Error",
    });
    vi.restoreAllMocks();
  });

  it("keeps the migration error when cleanup logging fails", async () => {
    const migrationError = new Error("synthetic migration failure");
    const unlockError = new Error("synthetic unlock failure");
    const { pool } = createFailingMigrationPool(migrationError, unlockError);
    vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("logger unavailable");
    });

    await expect(
      applyMigrations(pool, [migration], [migration.name]),
    ).rejects.toBe(migrationError);
    vi.restoreAllMocks();
  });
});

import { describe, expect, it, vi } from "vitest";
import {
  PairingCliUsageError,
  parsePairingOptions,
  runPairingCommand,
  type PairingCliDatabase,
} from "../src/auth/pairing-cli.js";
import type { PairingCode } from "../src/auth/device-auth.js";

const pairingResult: PairingCode = {
  code: "A7D2-X9P4",
  expiresAt: new Date("2026-11-01T10:05:00.000Z"),
  principalId: "owner",
  budgetIds: ["budget-one"],
};
const testEnvironment = {
  DATABASE_URL: "postgres://local/test",
  PAIRING_CODE_HASH_KEY: "synthetic-pairing-code-hash-key-32-bytes",
};

function createDatabase(overrides: Partial<PairingCliDatabase> = {}) {
  const createPairingCode = vi.fn(async () => pairingResult);
  const migrate = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  const database: PairingCliDatabase = {
    migrate,
    close,
    deviceAuth: { createPairingCode } as never,
    ...overrides,
  };
  return { database, createPairingCode, migrate, close };
}

describe("pairing code command", () => {
  it("parses explicit budget scopes and the default owner principal", () => {
    expect(parsePairingOptions(["--budget-id", "budget-one"])).toEqual({
      principalId: "owner",
      budgetIds: ["budget-one"],
    });
    expect(
      parsePairingOptions([
        "--principal-id",
        "household",
        "--budget-id",
        "budget-one",
        "--budget-id",
        "budget-two",
      ]),
    ).toEqual({
      principalId: "household",
      budgetIds: ["budget-one", "budget-two"],
    });
    expect(
      parsePairingOptions(["--all-budgets", "--principal-id", "owner"]),
    ).toEqual({ principalId: "owner", budgetIds: ["*"] });
  });

  it.each([
    { args: [] },
    { args: ["--unknown", "value"] },
    { args: ["--budget-id"] },
    { args: ["--budget-id", "--all-budgets"] },
    { args: ["--principal-id"] },
    { args: ["--all-budgets", "--budget-id", "budget-one"] },
    { args: ["--all-budgets", "--all-budgets"] },
  ])("rejects invalid command arguments $args", ({ args }) => {
    expect(() => parsePairingOptions(args)).toThrow(PairingCliUsageError);
  });

  it("rejects unknown flags and option-shaped values", () => {
    expect(() => parsePairingOptions(["--unknown"])).toThrow(
      PairingCliUsageError,
    );
    expect(() =>
      parsePairingOptions([
        "--principal-id",
        "--all-budgets",
        "--budget-id",
        "budget-one",
      ]),
    ).toThrow(PairingCliUsageError);
    expect(() => parsePairingOptions(["--budget-id", "--all-budgets"])).toThrow(
      PairingCliUsageError,
    );
  });

  it("provides a stable usage error name and message", () => {
    expect(new PairingCliUsageError()).toMatchObject({
      name: "PairingCliUsageError",
      message:
        "Usage: pair --budget-id <sync-id> [--budget-id <sync-id> ...] [--principal-id <id>] | --all-budgets [--principal-id <id>]",
    });
  });

  it("migrates before issuing a code and always closes the database", async () => {
    const { database, createPairingCode, migrate, close } = createDatabase();
    const createDatabaseFactory = vi.fn(() => database);

    await expect(
      runPairingCommand(
        ["--budget-id", "budget-one"],
        testEnvironment,
        createDatabaseFactory,
      ),
    ).resolves.toEqual(pairingResult);
    expect(createDatabaseFactory).toHaveBeenCalledWith(
      "postgres://local/test",
      testEnvironment.PAIRING_CODE_HASH_KEY,
    );
    expect(migrate).toHaveBeenCalledOnce();
    expect(createPairingCode).toHaveBeenCalledWith({
      principalId: "owner",
      budgetIds: ["budget-one"],
    });
    expect(close).toHaveBeenCalledOnce();
  });

  it("preserves a migration error when database cleanup also fails", async () => {
    const migrationError = new Error("synthetic migration failure");
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const close = vi
      .fn<() => Promise<void>>()
      .mockRejectedValue(new Error("synthetic close failure"));
    const { database } = createDatabase({
      migrate: vi.fn<() => Promise<void>>().mockRejectedValue(migrationError),
      close,
    });

    await expect(
      runPairingCommand(
        ["--budget-id", "budget-one"],
        testEnvironment,
        () => database,
      ),
    ).rejects.toBe(migrationError);
    expect(close).toHaveBeenCalledOnce();
    expect(consoleError).toHaveBeenCalledWith(
      "Pairing command cleanup also failed",
      { primaryErrorType: "Error", cleanupErrorType: "Error" },
    );
    vi.restoreAllMocks();
  });

  it("does not let cleanup reporting replace the primary error", async () => {
    const migrationError = new Error("synthetic migration failure");
    vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("logger failure");
    });
    const { database } = createDatabase({
      migrate: vi.fn<() => Promise<void>>().mockRejectedValue(migrationError),
      close: vi
        .fn<() => Promise<void>>()
        .mockRejectedValue(new Error("synthetic close failure")),
    });

    await expect(
      runPairingCommand(
        ["--budget-id", "budget-one"],
        testEnvironment,
        () => database,
      ),
    ).rejects.toBe(migrationError);
    vi.restoreAllMocks();
  });

  it("reports non-Error migration and cleanup failures by type only", async () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const { database } = createDatabase({
      migrate: vi
        .fn<() => Promise<void>>()
        .mockRejectedValue("migration failed"),
      close: vi.fn<() => Promise<void>>().mockRejectedValue("close failed"),
    });

    await expect(
      runPairingCommand(
        ["--budget-id", "budget-one"],
        testEnvironment,
        () => database,
      ),
    ).rejects.toBe("migration failed");
    expect(consoleError).toHaveBeenCalledWith(
      "Pairing command cleanup also failed",
      { primaryErrorType: "string", cleanupErrorType: "string" },
    );
    vi.restoreAllMocks();
  });

  it("surfaces a close error after successful code creation", async () => {
    const closeError = new Error("synthetic close failure");
    const { database } = createDatabase({
      close: vi.fn<() => Promise<void>>().mockRejectedValue(closeError),
    });

    await expect(
      runPairingCommand(
        ["--budget-id", "budget-one"],
        testEnvironment,
        () => database,
      ),
    ).rejects.toBe(closeError);
  });
});

import type { Pool } from "pg";
import { describe, expect, it, vi } from "vitest";
import { PostgresActualSessionVault } from "../src/auth/actual-session-vault.js";

describe("PostgresActualSessionVault", () => {
  it("returns no credential when the vault has no entries", async () => {
    const query = vi.fn(async () => ({ rows: [] }));
    const vault = new PostgresActualSessionVault(
      { query } as unknown as Pool,
      "synthetic-session-token-encryption-key-32",
    );

    await expect(vault.readLatest()).resolves.toBeUndefined();
    await expect(vault.read("actual-user:missing")).resolves.toBeUndefined();
    expect(query).toHaveBeenCalledTimes(2);
  });
});

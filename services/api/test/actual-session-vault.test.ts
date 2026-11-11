import type { Pool } from "pg";
import { describe, expect, it, vi } from "vitest";
import {
  InvalidStoredActualSessionCredentialError,
  PostgresActualSessionVault,
} from "../src/auth/actual-session-vault.js";

const currentKey =
  "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c";
const previousKey =
  "4b20f1d87e39a6c0542fb718a6d3e90c1375b2e94f06ca85d1273e8b6c491a20";
const unrelatedKey =
  "7ae6f9330c158db42704b15e6a92f08d631c45bf0cda872e3b915d70268f4c1a";

interface StoredSession {
  principal_id: string;
  token_ciphertext: Buffer;
  token_nonce: Buffer;
  token_tag: Buffer;
}

function cloneStoredSession(row: StoredSession): StoredSession {
  return {
    principal_id: row.principal_id,
    token_ciphertext: Buffer.from(row.token_ciphertext),
    token_nonce: Buffer.from(row.token_nonce),
    token_tag: Buffer.from(row.token_tag),
  };
}

function createVaultPool() {
  const rows = new Map<string, StoredSession>();
  let transactionSnapshot: Map<string, StoredSession> | undefined;
  let failRollback = false;
  let failBegin = false;

  const query = async (sql: string, parameters: readonly unknown[] = []) => {
    const normalizedSql = sql.replace(/\s+/g, " ").trim().toUpperCase();

    if (normalizedSql === "BEGIN") {
      if (failBegin) throw new Error("synthetic begin failure");
      transactionSnapshot = new Map(
        [...rows].map(([id, row]) => [id, cloneStoredSession(row)]),
      );
      return { rows: [], rowCount: null };
    }
    if (normalizedSql === "COMMIT") {
      transactionSnapshot = undefined;
      return { rows: [], rowCount: null };
    }
    if (normalizedSql === "ROLLBACK") {
      if (failRollback) throw new Error("synthetic rollback failure");
      if (transactionSnapshot !== undefined) {
        rows.clear();
        for (const [id, row] of transactionSnapshot) {
          rows.set(id, cloneStoredSession(row));
        }
      }
      transactionSnapshot = undefined;
      return { rows: [], rowCount: null };
    }
    if (normalizedSql.startsWith("INSERT INTO TANDEM_ACTUAL_SESSIONS")) {
      const [principalId, ciphertext, nonce, tag] = parameters as [
        string,
        Buffer,
        Buffer,
        Buffer,
      ];
      rows.set(
        principalId,
        cloneStoredSession({
          principal_id: principalId,
          token_ciphertext: ciphertext,
          token_nonce: nonce,
          token_tag: tag,
        }),
      );
      return { rows: [], rowCount: 1 };
    }
    if (normalizedSql.startsWith("UPDATE TANDEM_ACTUAL_SESSIONS")) {
      const [principalId, ciphertext, nonce, tag] = parameters as [
        string,
        Buffer,
        Buffer,
        Buffer,
      ];
      if (!rows.has(principalId)) return { rows: [], rowCount: 0 };
      rows.set(
        principalId,
        cloneStoredSession({
          principal_id: principalId,
          token_ciphertext: ciphertext,
          token_nonce: nonce,
          token_tag: tag,
        }),
      );
      return { rows: [], rowCount: 1 };
    }
    if (
      normalizedSql.startsWith("SELECT") &&
      normalizedSql.includes("FOR UPDATE")
    ) {
      return {
        rows: [...rows.values()]
          .sort((left, right) =>
            left.principal_id.localeCompare(right.principal_id),
          )
          .map(cloneStoredSession),
        rowCount: rows.size,
      };
    }
    if (
      normalizedSql.startsWith("SELECT") &&
      normalizedSql.includes("WHERE PRINCIPAL_ID = $1")
    ) {
      const principalId = parameters[0] as string;
      const row = rows.get(principalId);
      return {
        rows: row === undefined ? [] : [cloneStoredSession(row)],
        rowCount: row === undefined ? 0 : 1,
      };
    }
    if (
      normalizedSql.startsWith("SELECT") &&
      normalizedSql.includes("ORDER BY UPDATED_AT")
    ) {
      const row = rows.values().next().value as StoredSession | undefined;
      return {
        rows: row === undefined ? [] : [cloneStoredSession(row)],
        rowCount: row === undefined ? 0 : 1,
      };
    }
    throw new Error(`Unexpected vault query: ${normalizedSql}`);
  };

  const client = {
    query: vi.fn(query),
    release: vi.fn(),
  };
  const pool = {
    query: vi.fn(query),
    connect: vi.fn(async () => client),
  } as unknown as Pool;
  return {
    pool,
    rows,
    client,
    failBegin: () => {
      failBegin = true;
    },
    failRollback: () => {
      failRollback = true;
    },
  };
}

describe("PostgresActualSessionVault", () => {
  it("returns no credential when the vault has no entries", async () => {
    const store = createVaultPool();
    const vault = new PostgresActualSessionVault(store.pool, currentKey);

    await expect(vault.readLatest()).resolves.toBeUndefined();
    await expect(vault.read("actual-user:missing")).resolves.toBeUndefined();
    expect(store.pool.query).toHaveBeenCalledTimes(2);
  });

  it("reports corrupted stored credentials without exposing token material", async () => {
    const store = createVaultPool();
    const credential = {
      principalId: "actual-user:corrupt",
      sessionToken: "private-session-token",
    };
    const vault = new PostgresActualSessionVault(store.pool, currentKey);
    await vault.save(credential);
    store.rows.get(credential.principalId)!.token_tag = Buffer.alloc(16, 0);

    await expect(vault.read(credential.principalId)).rejects.toMatchObject({
      name: InvalidStoredActualSessionCredentialError.name,
      message: "Stored Actual sign-in credentials could not be decrypted",
    });
  });

  it("rotates mixed previous-key and current-key tokens transactionally", async () => {
    const store = createVaultPool();
    const legacyCredential = {
      principalId: "actual-user:legacy",
      sessionToken: "legacy-session-token",
    };
    const currentCredential = {
      principalId: "actual-user:current",
      sessionToken: "current-session-token",
    };
    await new PostgresActualSessionVault(store.pool, previousKey).save(
      legacyCredential,
    );
    await new PostgresActualSessionVault(store.pool, currentKey).save(
      currentCredential,
    );
    await expect(
      new PostgresActualSessionVault(store.pool, previousKey).readLatest(),
    ).resolves.toEqual(legacyCredential);
    const legacyCiphertextBefore = Buffer.from(
      store.rows.get(legacyCredential.principalId)!.token_ciphertext,
    );
    const currentRowBefore = cloneStoredSession(
      store.rows.get(currentCredential.principalId)!,
    );

    const rotatingVault = new PostgresActualSessionVault(
      store.pool,
      currentKey,
      previousKey,
    );
    await rotatingVault.reencryptStoredTokensWithCurrentKey();

    const currentKeyVault = new PostgresActualSessionVault(
      store.pool,
      currentKey,
    );
    await expect(
      currentKeyVault.read(legacyCredential.principalId),
    ).resolves.toEqual(legacyCredential);
    await expect(
      currentKeyVault.read(currentCredential.principalId),
    ).resolves.toEqual(currentCredential);
    expect(
      store.rows.get(legacyCredential.principalId)?.token_ciphertext,
    ).not.toEqual(legacyCiphertextBefore);
    expect(store.rows.get(currentCredential.principalId)).toEqual(
      currentRowBefore,
    );
    expect(store.client.query).toHaveBeenCalledWith("COMMIT");
  });

  it("rolls back all rows when the previous key cannot decrypt one token", async () => {
    const store = createVaultPool();
    const legacyCredential = {
      principalId: "actual-user:a-legacy",
      sessionToken: "legacy-session-token",
    };
    const unrecognizedCredential = {
      principalId: "actual-user:z-unrecognized",
      sessionToken: "unrecognized-session-token",
    };
    await new PostgresActualSessionVault(store.pool, previousKey).save(
      legacyCredential,
    );
    await new PostgresActualSessionVault(store.pool, unrelatedKey).save(
      unrecognizedCredential,
    );
    const rowsBefore = new Map(
      [...store.rows].map(([id, row]) => [id, cloneStoredSession(row)]),
    );

    const rotatingVault = new PostgresActualSessionVault(
      store.pool,
      currentKey,
      previousKey,
    );
    await expect(
      rotatingVault.reencryptStoredTokensWithCurrentKey(),
    ).rejects.toThrow(
      "Could not rotate stored Actual sessions: a token cannot be decrypted with the current or previous AUTH_TOKEN_ENCRYPTION_KEY",
    );

    expect(store.rows).toEqual(rowsBefore);
    expect(store.client.query).toHaveBeenCalledWith("ROLLBACK");
    expect(store.client.query).not.toHaveBeenCalledWith("COMMIT");
  });

  it("destroys the database client when rollback itself fails", async () => {
    const store = createVaultPool();
    await new PostgresActualSessionVault(store.pool, previousKey).save({
      principalId: "actual-user:legacy",
      sessionToken: "legacy-session-token",
    });
    await new PostgresActualSessionVault(store.pool, unrelatedKey).save({
      principalId: "actual-user:unrecognized",
      sessionToken: "unrecognized-session-token",
    });
    store.failRollback();

    await expect(
      new PostgresActualSessionVault(
        store.pool,
        currentKey,
        previousKey,
      ).reencryptStoredTokensWithCurrentKey(),
    ).rejects.toThrow("Could not rotate stored Actual sessions");

    expect(store.client.query).toHaveBeenCalledWith("ROLLBACK");
    expect(store.client.release).toHaveBeenCalledWith(true);
  });

  it("does not roll back or destroy the client when beginning fails", async () => {
    const store = createVaultPool();
    store.failBegin();

    await expect(
      new PostgresActualSessionVault(
        store.pool,
        currentKey,
        previousKey,
      ).reencryptStoredTokensWithCurrentKey(),
    ).rejects.toThrow("synthetic begin failure");

    expect(store.client.query).toHaveBeenCalledWith("BEGIN");
    expect(store.client.query).not.toHaveBeenCalledWith("ROLLBACK");
    expect(store.client.release).toHaveBeenCalledOnce();
    expect(store.client.release).toHaveBeenCalledWith(undefined);
  });

  it("does no database work at startup when no previous key is configured", async () => {
    const store = createVaultPool();
    const vault = new PostgresActualSessionVault(store.pool, currentKey);

    await expect(
      vault.reencryptStoredTokensWithCurrentKey(),
    ).resolves.toBeUndefined();
    expect(store.pool.connect).not.toHaveBeenCalled();
    expect(store.pool.query).not.toHaveBeenCalled();
  });
});

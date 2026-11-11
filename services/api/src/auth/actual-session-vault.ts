import {
  createCipheriv,
  createDecipheriv,
  createHash,
  randomBytes,
} from "node:crypto";
import type { Pool } from "pg";

export interface ActualSessionCredential {
  principalId: string;
  sessionToken: string;
}

interface StoredActualSession {
  principal_id: string;
  token_ciphertext: Buffer;
  token_nonce: Buffer;
  token_tag: Buffer;
}

interface EncryptedSessionToken {
  ciphertext: Buffer;
  nonce: Buffer;
  tag: Buffer;
}

const CIPHERTEXT_ENVELOPE_MAGIC = Buffer.from("TANDEMS1", "ascii");
const KEY_ID_BYTES = 16;

export class InvalidStoredActualSessionCredentialError extends Error {
  constructor() {
    super("Stored Actual sign-in credentials could not be decrypted");
    this.name = "InvalidStoredActualSessionCredentialError";
  }
}

export class PostgresActualSessionVault {
  readonly #encryptionKey: Buffer;
  readonly #previousEncryptionKey: Buffer | undefined;

  constructor(
    private readonly pool: Pool,
    encryptionKey: string,
    previousEncryptionKey?: string,
  ) {
    this.#encryptionKey = deriveEncryptionKey(encryptionKey);
    this.#previousEncryptionKey =
      previousEncryptionKey === undefined
        ? undefined
        : deriveEncryptionKey(previousEncryptionKey);
  }

  async save(credential: ActualSessionCredential): Promise<void> {
    const encrypted = encryptSessionToken(
      credential.principalId,
      credential.sessionToken,
      this.#encryptionKey,
    );
    await this.pool.query(
      `INSERT INTO tandem_actual_sessions (
        principal_id, token_ciphertext, token_nonce, token_tag
      ) VALUES ($1, $2, $3, $4)
      ON CONFLICT (principal_id) DO UPDATE SET
        token_ciphertext = EXCLUDED.token_ciphertext,
        token_nonce = EXCLUDED.token_nonce,
        token_tag = EXCLUDED.token_tag,
        updated_at = clock_timestamp()`,
      [
        credential.principalId,
        encrypted.ciphertext,
        encrypted.nonce,
        encrypted.tag,
      ],
    );
  }

  async readLatest(): Promise<ActualSessionCredential | undefined> {
    const result = await this.pool.query<StoredActualSession>(
      `SELECT principal_id, token_ciphertext, token_nonce, token_tag
       FROM tandem_actual_sessions
       ORDER BY updated_at DESC, principal_id
       LIMIT 1`,
    );
    const row = result.rows[0];
    return row === undefined ? undefined : this.#decryptRow(row);
  }

  async read(
    principalId: string,
  ): Promise<ActualSessionCredential | undefined> {
    const result = await this.pool.query<StoredActualSession>(
      `SELECT principal_id, token_ciphertext, token_nonce, token_tag
       FROM tandem_actual_sessions
       WHERE principal_id = $1`,
      [principalId],
    );
    const row = result.rows[0];
    return row === undefined ? undefined : this.#decryptRow(row);
  }

  async reencryptStoredTokensWithCurrentKey(): Promise<void> {
    const previousKey = this.#previousEncryptionKey;
    if (previousKey === undefined) return;

    const client = await this.pool.connect();
    let transactionStarted = false;
    let destroyClient = false;
    try {
      await client.query("BEGIN");
      transactionStarted = true;
      const result = await client.query<StoredActualSession>(
        `SELECT principal_id, token_ciphertext, token_nonce, token_tag
         FROM tandem_actual_sessions
         ORDER BY principal_id
         FOR UPDATE`,
      );

      for (const row of result.rows) {
        if (canDecryptSessionToken(row, this.#encryptionKey)) continue;

        let sessionToken: string;
        try {
          sessionToken = decryptSessionToken(row, previousKey);
        } catch {
          throw new Error(
            "Could not rotate stored Actual sessions: a token cannot be decrypted with the current or previous AUTH_TOKEN_ENCRYPTION_KEY",
          );
        }

        const encrypted = encryptSessionToken(
          row.principal_id,
          sessionToken,
          this.#encryptionKey,
        );
        await client.query(
          `UPDATE tandem_actual_sessions
           SET token_ciphertext = $2,
               token_nonce = $3,
               token_tag = $4,
               updated_at = clock_timestamp()
           WHERE principal_id = $1`,
          [
            row.principal_id,
            encrypted.ciphertext,
            encrypted.nonce,
            encrypted.tag,
          ],
        );
      }

      await client.query("COMMIT");
      transactionStarted = false;
    } catch (error) {
      if (transactionStarted) {
        try {
          await client.query("ROLLBACK");
        } catch {
          destroyClient = true;
        }
      }
      throw error;
    } finally {
      client.release(destroyClient ? true : undefined);
    }
  }

  #decryptRow(row: StoredActualSession): ActualSessionCredential {
    let sessionToken: string;
    try {
      sessionToken = decryptSessionToken(row, this.#encryptionKey);
    } catch {
      throw new InvalidStoredActualSessionCredentialError();
    }
    return {
      principalId: row.principal_id,
      sessionToken,
    };
  }
}

function deriveEncryptionKey(value: string): Buffer {
  return createHash("sha256").update(value, "utf8").digest();
}

function encryptSessionToken(
  principalId: string,
  sessionToken: string,
  encryptionKey: Buffer,
): EncryptedSessionToken {
  const nonce = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", encryptionKey, nonce);
  const keyId = encryptionKeyId(encryptionKey);
  cipher.setAAD(sessionTokenAdditionalData(principalId, keyId));
  const ciphertext = Buffer.concat([
    cipher.update(sessionToken, "utf8"),
    cipher.final(),
  ]);
  return {
    ciphertext: Buffer.concat([CIPHERTEXT_ENVELOPE_MAGIC, keyId, ciphertext]),
    nonce,
    tag: cipher.getAuthTag(),
  };
}

function decryptSessionToken(
  row: StoredActualSession,
  encryptionKey: Buffer,
): string {
  const envelope = splitCiphertextEnvelope(row.token_ciphertext);
  if (
    envelope.keyId !== undefined &&
    !envelope.keyId.equals(encryptionKeyId(encryptionKey))
  ) {
    throw new Error("Stored session token uses a different encryption key");
  }
  const decipher = createDecipheriv(
    "aes-256-gcm",
    encryptionKey,
    row.token_nonce,
  );
  decipher.setAAD(sessionTokenAdditionalData(row.principal_id, envelope.keyId));
  decipher.setAuthTag(row.token_tag);
  return Buffer.concat([
    decipher.update(envelope.ciphertext),
    decipher.final(),
  ]).toString("utf8");
}

function sessionTokenAdditionalData(
  principalId: string,
  keyId?: Buffer,
): Buffer {
  const principal = Buffer.from(principalId, "utf8");
  return keyId === undefined
    ? principal
    : Buffer.concat([principal, CIPHERTEXT_ENVELOPE_MAGIC, keyId]);
}

function encryptionKeyId(encryptionKey: Buffer): Buffer {
  return createHash("sha256")
    .update(encryptionKey)
    .digest()
    .subarray(0, KEY_ID_BYTES);
}

function splitCiphertextEnvelope(ciphertext: Buffer): {
  keyId?: Buffer;
  ciphertext: Buffer;
} {
  if (
    !ciphertext
      .subarray(0, CIPHERTEXT_ENVELOPE_MAGIC.length)
      .equals(CIPHERTEXT_ENVELOPE_MAGIC)
  ) {
    // Rows written before TANDEMS1 stored raw AES-GCM ciphertext in this column.
    return { ciphertext };
  }
  const headerLength = CIPHERTEXT_ENVELOPE_MAGIC.length + KEY_ID_BYTES;
  if (ciphertext.length <= headerLength) {
    throw new Error("Stored session token envelope is incomplete");
  }
  return {
    keyId: ciphertext.subarray(CIPHERTEXT_ENVELOPE_MAGIC.length, headerLength),
    ciphertext: ciphertext.subarray(headerLength),
  };
}

function canDecryptSessionToken(
  row: StoredActualSession,
  encryptionKey: Buffer,
): boolean {
  try {
    decryptSessionToken(row, encryptionKey);
    return true;
  } catch {
    return false;
  }
}

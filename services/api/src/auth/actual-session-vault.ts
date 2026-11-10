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

export class PostgresActualSessionVault {
  readonly #encryptionKey: Buffer;

  constructor(
    private readonly pool: Pool,
    encryptionKey: string,
  ) {
    this.#encryptionKey = createHash("sha256")
      .update(encryptionKey, "utf8")
      .digest();
  }

  async save(credential: ActualSessionCredential): Promise<void> {
    const nonce = randomBytes(12);
    const cipher = createCipheriv("aes-256-gcm", this.#encryptionKey, nonce);
    cipher.setAAD(Buffer.from(credential.principalId, "utf8"));
    const ciphertext = Buffer.concat([
      cipher.update(credential.sessionToken, "utf8"),
      cipher.final(),
    ]);
    await this.pool.query(
      `INSERT INTO companion_actual_sessions (
        principal_id, token_ciphertext, token_nonce, token_tag
      ) VALUES ($1, $2, $3, $4)
      ON CONFLICT (principal_id) DO UPDATE SET
        token_ciphertext = EXCLUDED.token_ciphertext,
        token_nonce = EXCLUDED.token_nonce,
        token_tag = EXCLUDED.token_tag,
        updated_at = clock_timestamp()`,
      [credential.principalId, ciphertext, nonce, cipher.getAuthTag()],
    );
  }

  async readLatest(): Promise<ActualSessionCredential | undefined> {
    const result = await this.pool.query<{
      principal_id: string;
      token_ciphertext: Buffer;
      token_nonce: Buffer;
      token_tag: Buffer;
    }>(
      `SELECT principal_id, token_ciphertext, token_nonce, token_tag
       FROM companion_actual_sessions
       ORDER BY updated_at DESC, principal_id
       LIMIT 1`,
    );
    const row = result.rows[0];
    if (row === undefined) return undefined;
    const decipher = createDecipheriv(
      "aes-256-gcm",
      this.#encryptionKey,
      row.token_nonce,
    );
    decipher.setAAD(Buffer.from(row.principal_id, "utf8"));
    decipher.setAuthTag(row.token_tag);
    const sessionToken = Buffer.concat([
      decipher.update(row.token_ciphertext),
      decipher.final(),
    ]).toString("utf8");
    return { principalId: row.principal_id, sessionToken };
  }

  async read(
    principalId: string,
  ): Promise<ActualSessionCredential | undefined> {
    const result = await this.pool.query<{
      principal_id: string;
      token_ciphertext: Buffer;
      token_nonce: Buffer;
      token_tag: Buffer;
    }>(
      `SELECT principal_id, token_ciphertext, token_nonce, token_tag
       FROM companion_actual_sessions
       WHERE principal_id = $1`,
      [principalId],
    );
    const row = result.rows[0];
    if (row === undefined) return undefined;
    const decipher = createDecipheriv(
      "aes-256-gcm",
      this.#encryptionKey,
      row.token_nonce,
    );
    decipher.setAAD(Buffer.from(row.principal_id, "utf8"));
    decipher.setAuthTag(row.token_tag);
    const sessionToken = Buffer.concat([
      decipher.update(row.token_ciphertext),
      decipher.final(),
    ]).toString("utf8");
    return { principalId: row.principal_id, sessionToken };
  }
}

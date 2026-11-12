import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import type { Pool as PoolType } from "pg";
import { DeviceAuthorization, hashSecret } from "../src/auth/device-auth.js";
import { PostgresActualSessionVault } from "../src/auth/actual-session-vault.js";
import { PostgresDeviceAuthStore } from "../src/auth/postgres-device-auth-store.js";
import { ensureSchema } from "../src/schema/bootstrap.js";

let pool: PoolType | undefined;
let adminPool: PoolType | undefined;
let schemaName: string | undefined;
let authorization: DeviceAuthorization;
const authTokenEncryptionKey =
  "5323b1ab617c73bf862318e07e388d79d4f902170d607b5be38e6a6f66e2eb3c";

describe("PostgreSQL authentication integration", () => {
  beforeAll(async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }
    schemaName = `device_auth_${randomUUID().replaceAll("-", "")}`;
    adminPool = new Pool({ connectionString, max: 2 });
    await adminPool.query(`CREATE SCHEMA ${schemaName}`);
    pool = new Pool({
      connectionString,
      max: 4,
      options: `-c search_path=${schemaName}`,
    });
    await ensureSchema(pool);
    authorization = new DeviceAuthorization(new PostgresDeviceAuthStore(pool));
  }, 30_000);

  afterAll(async () => {
    await pool?.end();
    if (schemaName) {
      await adminPool?.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
    await adminPool?.end();
  });

  it("persists only a device-token hash and supports revocation", async () => {
    const device = await authorization.createDevice({
      principalId: "actual-server:synthetic",
      budgetIds: ["*"],
      deviceName: "Synthetic Android",
    });

    expect(device.accessToken).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(await authorization.authenticate(device.accessToken)).toMatchObject({
      deviceId: device.deviceId,
      principalId: "actual-server:synthetic",
      name: "Synthetic Android",
      budgetIds: ["*"],
    });
    const deviceRow = await pool!.query<{
      credential_hash: string;
      device_name: string;
    }>(
      "SELECT credential_hash, device_name FROM tandem_devices WHERE device_id = $1",
      [device.deviceId],
    );
    expect(deviceRow.rows[0]).toEqual({
      credential_hash: hashSecret(device.accessToken),
      device_name: "Synthetic Android",
    });
    expect(deviceRow.rows[0]?.credential_hash).not.toBe(device.accessToken);

    expect(
      await authorization.revokeDeviceForDevice(device, device.deviceId),
    ).toBe(true);
    expect(
      await authorization.authenticate(device.accessToken),
    ).toBeUndefined();
  });

  it("encrypts the Actual session token in PostgreSQL and can restore it", async () => {
    const vault = new PostgresActualSessionVault(pool!, authTokenEncryptionKey);
    const credential = {
      principalId: "actual-server:synthetic",
      sessionToken: "synthetic-actual-session-token-never-sent-to-mobile",
    };
    await vault.save(credential);

    const stored = await pool!.query<{ token_ciphertext: Buffer }>(
      "SELECT token_ciphertext FROM tandem_actual_sessions WHERE principal_id = $1",
      [credential.principalId],
    );
    expect(stored.rows[0]?.token_ciphertext.toString("utf8")).not.toContain(
      credential.sessionToken,
    );
    await expect(vault.readLatest()).resolves.toEqual(credential);
    await expect(
      new PostgresActualSessionVault(
        pool!,
        "a-different-encryption-key-that-is-long-enough",
      ).readLatest(),
    ).rejects.toThrow();
  });

  it("creates the schema without a migration-history table", async () => {
    const tables = await pool!.query<{
      devices: string | null;
      actual_sessions: string | null;
    }>(`SELECT
      to_regclass('tandem_devices') AS devices,
      to_regclass('tandem_actual_sessions') AS actual_sessions`);
    expect(tables.rows[0]).toEqual({
      devices: "tandem_devices",
      actual_sessions: "tandem_actual_sessions",
    });
    const history = await pool!.query<{ table_name: string | null }>(
      "SELECT to_regclass('tandem_migrations')::text AS table_name",
    );
    expect(history.rows[0]?.table_name).toBeNull();
  });
});

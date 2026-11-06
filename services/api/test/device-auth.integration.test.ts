import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import type { Pool as PoolType } from "pg";
import { buildApiApp } from "../src/app.js";
import {
  DeviceAuthorization,
  hashPairingCode,
  hashSecret,
} from "../src/auth/device-auth.js";
import { PostgresDeviceAuthStore } from "../src/auth/postgres-device-auth-store.js";
import { MIGRATIONS, applyMigrations } from "../src/schema/migrator.js";

let pool: PoolType | undefined;
let adminPool: PoolType | undefined;
let schemaName: string | undefined;
let authorization: DeviceAuthorization;
const pairingCodeHashKey = "synthetic-pairing-code-hash-key-32-bytes";

describe("PostgreSQL device authorization integration", () => {
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
    await applyMigrations(pool, MIGRATIONS);
    authorization = new DeviceAuthorization(
      new PostgresDeviceAuthStore(pool),
      undefined,
      undefined,
      undefined,
      { pairingCodeHashKey },
    );
  }, 30_000);

  afterAll(async () => {
    await pool?.end();
    if (schemaName) {
      await adminPool?.query(`DROP SCHEMA ${schemaName} CASCADE`);
    }
    await adminPool?.end();
  });

  it("atomically exchanges a pairing code once and stores only the token hash", async () => {
    const pairing = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds: ["synthetic-budget-1", "synthetic-budget-2"],
    });
    const results = await Promise.all([
      authorization.pair({ code: pairing.code, deviceName: "Android" }),
      authorization.pair({ code: pairing.code, deviceName: "iPhone" }),
    ]);
    const paired = results.filter((result) => result !== undefined);

    expect(paired).toHaveLength(1);
    const device = paired[0];
    if (!device) {
      throw new Error("A valid pairing code did not create a device");
    }
    expect(device).toMatchObject({
      principalId: "owner",
      budgetIds: ["synthetic-budget-1", "synthetic-budget-2"],
    });
    expect(device.accessToken).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(device.accessToken).not.toBe(pairing.code);

    const deviceRows = await pool!.query<{
      credential_hash: string;
      device_name: string;
    }>(
      "SELECT credential_hash, device_name FROM companion_devices WHERE device_id = $1",
      [device.deviceId],
    );
    expect(deviceRows.rows[0]).toEqual({
      credential_hash: hashSecret(device.accessToken),
      device_name: device.name,
    });
    expect(deviceRows.rows[0]?.credential_hash).not.toBe(device.accessToken);

    const pairingRows = await pool!.query<{
      code_hash: string;
      consumed_at: Date | null;
    }>(
      "SELECT code_hash, consumed_at FROM companion_pairing_codes WHERE code_hash = $1",
      [hashPairingCode(pairing.code, pairingCodeHashKey)],
    );
    expect(pairingRows.rows[0]).toMatchObject({
      code_hash: hashPairingCode(pairing.code, pairingCodeHashKey),
    });
    expect(pairingRows.rows[0]?.consumed_at).toBeInstanceOf(Date);
  });

  it("authenticates, lists, scopes, and revokes devices", async () => {
    const principalId = `owner-${randomUUID()}`;
    const pairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["synthetic-budget"],
    });
    const device = await authorization.pair({
      code: pairing.code,
      deviceName: "Synthetic Android",
    });
    if (!device) {
      throw new Error("A valid pairing code did not create a device");
    }

    const authenticated = await authorization.authenticate(device.accessToken);
    expect(authenticated).toMatchObject({
      deviceId: device.deviceId,
      principalId,
      name: "Synthetic Android",
      budgetIds: ["synthetic-budget"],
    });
    expect(authenticated?.lastSeenAt).toBeInstanceOf(Date);
    expect(
      DeviceAuthorization.canAccessBudget(authenticated!, "synthetic-budget"),
    ).toBe(true);
    expect(
      DeviceAuthorization.canAccessBudget(authenticated!, "other-budget"),
    ).toBe(false);

    const listed = await authorization.listDevicesForDevice(authenticated!);
    expect(listed).toHaveLength(1);
    expect(listed[0]).toMatchObject({
      deviceId: device.deviceId,
      revokedAt: null,
    });
    expect(
      await authorization.revokeDeviceForDevice(
        { ...authenticated!, principalId: "other-principal" },
        device.deviceId,
      ),
    ).toBe(false);
    expect(
      await authorization.revokeDeviceForDevice(
        authenticated!,
        device.deviceId,
      ),
    ).toBe(true);
    expect(
      await authorization.revokeDeviceForDevice(
        authenticated!,
        device.deviceId,
      ),
    ).toBe(false);
    await expect(
      authorization.authenticate(device.accessToken),
    ).resolves.toBeUndefined();
    expect(
      (await authorization.listDevicesForDevice(authenticated!))[0]?.revokedAt,
    ).toBeInstanceOf(Date);
  });

  it("expires inactive device credentials after 90 days and refreshes active ones", async () => {
    const principalId = `idle-owner-${randomUUID()}`;
    const pairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["synthetic-budget"],
    });
    const device = await authorization.pair({
      code: pairing.code,
      deviceName: "Idle phone",
    });
    if (!device) {
      throw new Error("A valid pairing code did not create a device");
    }

    await pool!.query(
      `UPDATE companion_devices
      SET last_seen_at = clock_timestamp() - interval '89 days 23 hours'
      WHERE device_id = $1`,
      [device.deviceId],
    );
    const refreshed = await authorization.authenticate(device.accessToken);
    expect(refreshed?.lastSeenAt?.getTime()).toBeGreaterThan(
      Date.now() - 60_000,
    );

    await pool!.query(
      `UPDATE companion_devices
      SET last_seen_at = clock_timestamp() - interval '91 days'
      WHERE device_id = $1`,
      [device.deviceId],
    );
    await expect(
      authorization.authenticate(device.accessToken),
    ).resolves.toBeUndefined();
  });

  it("writes last-seen activity at most once per minute", async () => {
    const principalId = `activity-owner-${randomUUID()}`;
    const pairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["synthetic-budget"],
    });
    const device = await authorization.pair({
      code: pairing.code,
      deviceName: "Active phone",
    });
    if (!device) {
      throw new Error("A valid pairing code did not create a device");
    }

    await expect(
      authorization.authenticate(device.accessToken),
    ).resolves.toMatchObject({
      deviceId: device.deviceId,
      lastSeenAt: expect.any(Date),
    });
    const firstTouch = await pool!.query<{
      last_seen_at: Date;
      row_version: string;
    }>(
      "SELECT last_seen_at, xmin::text AS row_version FROM companion_devices WHERE device_id = $1",
      [device.deviceId],
    );
    const initialLastSeen = firstTouch.rows[0]?.last_seen_at;
    if (!initialLastSeen)
      throw new Error("Initial last-seen time was not stored");

    await expect(
      authorization.authenticate(device.accessToken),
    ).resolves.toMatchObject({
      deviceId: device.deviceId,
      lastSeenAt: initialLastSeen,
    });
    const unchanged = await pool!.query<{
      last_seen_at: Date;
      row_version: string;
    }>(
      "SELECT last_seen_at, xmin::text AS row_version FROM companion_devices WHERE device_id = $1",
      [device.deviceId],
    );
    expect(unchanged.rows[0]?.last_seen_at).toEqual(initialLastSeen);
    expect(unchanged.rows[0]?.row_version).toBe(
      firstTouch.rows[0]?.row_version,
    );

    await pool!.query(
      `UPDATE companion_devices
      SET last_seen_at = clock_timestamp() - interval '2 minutes'
      WHERE device_id = $1`,
      [device.deviceId],
    );
    const refreshed = await Promise.all(
      Array.from({ length: 8 }, () =>
        authorization.authenticate(device.accessToken),
      ),
    );
    expect(refreshed).toHaveLength(8);
    expect(
      refreshed.every((entry) => entry?.deviceId === device.deviceId),
    ).toBe(true);
    expect(
      Math.max(...refreshed.map((entry) => entry?.lastSeenAt?.getTime() ?? 0)),
    ).toBeGreaterThan(initialLastSeen.getTime());
  });

  it("pairs, authenticates, and revokes through Fastify and PostgreSQL", async () => {
    const principalId = `owner-${randomUUID()}`;
    const pairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["synthetic-budget"],
    });
    const app = await buildApiApp(
      { ping: async () => undefined, close: async () => undefined },
      { deviceAuth: authorization },
    );

    try {
      const pairedResponse = await app.inject({
        method: "POST",
        url: "/api/v1/auth/pair",
        payload: { code: pairing.code, deviceName: "Integration phone" },
      });
      expect(pairedResponse.statusCode).toBe(201);
      const paired = pairedResponse.json() as {
        deviceId: string;
        accessToken: string;
        principalId: string;
      };
      expect(paired.principalId).toBe(principalId);

      const identity = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
        headers: { authorization: `Bearer ${paired.accessToken}` },
      });
      expect(identity.statusCode).toBe(200);
      expect(identity.json()).toMatchObject({
        deviceId: paired.deviceId,
        principalId,
        budgetIds: ["synthetic-budget"],
      });

      const revoked = await app.inject({
        method: "DELETE",
        url: `/api/v1/devices/${paired.deviceId}`,
        headers: { authorization: `Bearer ${paired.accessToken}` },
      });
      expect(revoked.statusCode).toBe(204);

      const afterRevocation = await app.inject({
        method: "GET",
        url: "/api/v1/auth/me",
        headers: { authorization: `Bearer ${paired.accessToken}` },
      });
      expect(afterRevocation.statusCode).toBe(401);
    } finally {
      await app.close();
    }
  });

  it("limits device management to self unless a device has wildcard budget scope", async () => {
    const principalId = `scope-owner-${randomUUID()}`;
    const limitedPairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["synthetic-budget-A"],
    });
    const fullPairing = await authorization.createPairingCode({
      principalId,
      budgetIds: ["*"],
    });
    const limitedDevice = await authorization.pair({
      code: limitedPairing.code,
      deviceName: "Limited phone",
    });
    const fullDevice = await authorization.pair({
      code: fullPairing.code,
      deviceName: "Owner phone",
    });
    if (!limitedDevice || !fullDevice) {
      throw new Error("Synthetic device pairing did not succeed");
    }

    const app = await buildApiApp(
      { ping: async () => undefined, close: async () => undefined },
      { deviceAuth: authorization },
    );
    try {
      const limitedList = await app.inject({
        method: "GET",
        url: "/api/v1/devices",
        headers: { authorization: `Bearer ${limitedDevice.accessToken}` },
      });
      expect(limitedList.statusCode).toBe(200);
      expect(limitedList.json().devices).toHaveLength(1);
      expect(limitedList.json().devices[0].deviceId).toBe(
        limitedDevice.deviceId,
      );

      const forbiddenRevoke = await app.inject({
        method: "DELETE",
        url: `/api/v1/devices/${fullDevice.deviceId}`,
        headers: { authorization: `Bearer ${limitedDevice.accessToken}` },
      });
      expect(forbiddenRevoke.statusCode).toBe(404);
      expect(
        await authorization.authenticate(fullDevice.accessToken),
      ).toBeDefined();

      const fullList = await app.inject({
        method: "GET",
        url: "/api/v1/devices",
        headers: { authorization: `Bearer ${fullDevice.accessToken}` },
      });
      expect(fullList.statusCode).toBe(200);
      expect(fullList.json().devices).toHaveLength(2);

      const authorizedRevoke = await app.inject({
        method: "DELETE",
        url: `/api/v1/devices/${limitedDevice.deviceId}`,
        headers: { authorization: `Bearer ${fullDevice.accessToken}` },
      });
      expect(authorizedRevoke.statusCode).toBe(204);
      expect(
        await authorization.authenticate(limitedDevice.accessToken),
      ).toBeUndefined();
    } finally {
      await app.close();
    }
  });

  it("pairs a device name with 60 non-BMP characters through Fastify and PostgreSQL", async () => {
    const pairing = await authorization.createPairingCode({
      principalId: `emoji-owner-${randomUUID()}`,
      budgetIds: ["synthetic-budget"],
    });
    const app = await buildApiApp(
      { ping: async () => undefined, close: async () => undefined },
      { deviceAuth: authorization },
    );
    const deviceName = "😀".repeat(60);

    try {
      const response = await app.inject({
        method: "POST",
        url: "/api/v1/auth/pair",
        payload: { code: pairing.code, deviceName },
      });
      expect(response.statusCode).toBe(201);
      expect(response.json()).toMatchObject({ name: deviceName });
      const stored = await pool!.query<{ device_name: string }>(
        "SELECT device_name FROM companion_devices WHERE device_id = $1",
        [(response.json() as { deviceId: string }).deviceId],
      );
      expect(stored.rows[0]?.device_name).toBe(deviceName);
    } finally {
      await app.close();
    }
  });

  it("rejects expired pairing codes and purges them when a new code is issued", async () => {
    const expired = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds: ["synthetic-budget"],
    });
    await pool!.query(
      `UPDATE companion_pairing_codes
      SET created_at = clock_timestamp() - interval '2 days',
        expires_at = clock_timestamp() - interval '1 day'
      WHERE code_hash = $1`,
      [hashPairingCode(expired.code, pairingCodeHashKey)],
    );

    await expect(
      authorization.pair({ code: expired.code, deviceName: "Expired phone" }),
    ).resolves.toBeUndefined();
    const replacement = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds: ["synthetic-budget"],
    });

    const expiredRows = await pool!.query(
      "SELECT code_hash FROM companion_pairing_codes WHERE code_hash = $1",
      [hashPairingCode(expired.code, pairingCodeHashKey)],
    );
    expect(expiredRows.rowCount).toBe(0);
    expect(replacement.code).not.toBe(expired.code);
  });
});

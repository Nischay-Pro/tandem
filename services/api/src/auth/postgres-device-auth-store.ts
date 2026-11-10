import type { Pool } from "pg";
import { DEVICE_CREDENTIAL_IDLE_TTL_DAYS } from "./device-auth.js";
import type { DeviceAuthStore, DeviceRecord } from "./device-auth.js";

interface DeviceRow {
  device_id: string;
  principal_id: string;
  device_name: string;
  budget_ids: string[];
  created_at: Date;
  last_seen_at: Date | null;
  revoked_at: Date | null;
  should_refresh_last_seen?: boolean;
}

export class PostgresDeviceAuthStore implements DeviceAuthStore {
  constructor(private readonly pool: Pool) {}

  async createDevice(input: {
    principalId: string;
    budgetIds: string[];
    deviceId: string;
    deviceName: string;
    credentialHash: string;
  }): Promise<DeviceRecord> {
    const result = await this.pool.query<DeviceRow>(
      `INSERT INTO tandem_devices (
        device_id, principal_id, device_name, credential_hash, budget_ids
      ) VALUES ($1, $2, $3, $4, $5)
      RETURNING device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at`,
      [
        input.deviceId,
        input.principalId,
        input.deviceName,
        input.credentialHash,
        input.budgetIds,
      ],
    );
    const row = result.rows[0];
    if (row === undefined) throw new Error("Device sign-in did not persist");
    return mapDeviceRow(row);
  }

  async authenticate(
    credentialHash: string,
  ): Promise<DeviceRecord | undefined> {
    const result = await this.pool.query<DeviceRow>(
      `SELECT device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at,
        (last_seen_at IS NULL OR last_seen_at <=
          clock_timestamp() - interval '1 minute') AS should_refresh_last_seen
      FROM tandem_devices
      WHERE credential_hash = $1 AND revoked_at IS NULL
        AND COALESCE(last_seen_at, created_at) >
          clock_timestamp() - ($2::int * interval '1 day')`,
      [credentialHash, DEVICE_CREDENTIAL_IDLE_TTL_DAYS],
    );
    const row = result.rows[0];
    if (row === undefined) return undefined;
    if (!row.should_refresh_last_seen) return mapDeviceRow(row);

    const refreshed = await this.pool.query<DeviceRow>(
      `UPDATE tandem_devices
      SET last_seen_at = clock_timestamp()
      WHERE device_id = $1 AND credential_hash = $2 AND revoked_at IS NULL
        AND COALESCE(last_seen_at, created_at) >
          clock_timestamp() - ($3::int * interval '1 day')
        AND (last_seen_at IS NULL OR last_seen_at <=
          clock_timestamp() - interval '1 minute')
      RETURNING device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at`,
      [row.device_id, credentialHash, DEVICE_CREDENTIAL_IDLE_TTL_DAYS],
    );
    const refreshedRow = refreshed.rows[0];
    if (refreshedRow !== undefined) return mapDeviceRow(refreshedRow);

    // Another request may have refreshed this row while this one waited. Read
    // it again so that concurrent requests do not spuriously reject the token.
    const current = await this.pool.query<DeviceRow>(
      `SELECT device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at
      FROM tandem_devices
      WHERE device_id = $1 AND credential_hash = $2 AND revoked_at IS NULL
        AND COALESCE(last_seen_at, created_at) >
          clock_timestamp() - ($3::int * interval '1 day')`,
      [row.device_id, credentialHash, DEVICE_CREDENTIAL_IDLE_TTL_DAYS],
    );
    const currentRow = current.rows[0];
    return currentRow === undefined ? undefined : mapDeviceRow(currentRow);
  }

  async listDevices(principalId: string): Promise<DeviceRecord[]> {
    const result = await this.pool.query<DeviceRow>(
      `SELECT device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at
      FROM tandem_devices
      WHERE principal_id = $1
      ORDER BY created_at, device_id`,
      [principalId],
    );
    return result.rows.map(mapDeviceRow);
  }

  async revokeDevice(principalId: string, deviceId: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE tandem_devices
      SET revoked_at = clock_timestamp()
      WHERE principal_id = $1 AND device_id = $2 AND revoked_at IS NULL
      RETURNING device_id`,
      [principalId, deviceId],
    );
    return result.rowCount === 1;
  }
}

function mapDeviceRow(row: DeviceRow): DeviceRecord {
  return {
    deviceId: row.device_id,
    principalId: row.principal_id,
    name: row.device_name,
    budgetIds: row.budget_ids,
    createdAt: row.created_at,
    lastSeenAt: row.last_seen_at,
    revokedAt: row.revoked_at,
  };
}

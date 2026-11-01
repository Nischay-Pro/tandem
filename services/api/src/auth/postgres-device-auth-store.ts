import type { Pool } from "pg";
import type {
  DeviceAuthStore,
  DeviceRecord,
  PairingCodeRecord,
} from "./device-auth.js";

interface DeviceRow {
  device_id: string;
  principal_id: string;
  device_name: string;
  budget_ids: string[];
  created_at: Date;
  last_seen_at: Date | null;
  revoked_at: Date | null;
}

export class PostgresDeviceAuthStore implements DeviceAuthStore {
  constructor(private readonly pool: Pool) {}

  async createPairingCode(record: PairingCodeRecord): Promise<void> {
    await this.pool.query(
      `WITH purged AS (
        DELETE FROM companion_pairing_codes
        WHERE expires_at <= clock_timestamp()
          OR consumed_at < clock_timestamp() - interval '1 day'
      )
      INSERT INTO companion_pairing_codes (
        code_hash, principal_id, budget_ids, expires_at
      ) VALUES ($1, $2, $3, $4)`,
      [record.codeHash, record.principalId, record.budgetIds, record.expiresAt],
    );
  }

  async consumePairingCode(input: {
    codeHash: string;
    deviceId: string;
    deviceName: string;
    credentialHash: string;
  }): Promise<DeviceRecord | undefined> {
    const result = await this.pool.query<DeviceRow>(
      `WITH consumed AS (
        UPDATE companion_pairing_codes
        SET consumed_at = clock_timestamp()
        WHERE code_hash = $1
          AND consumed_at IS NULL
          AND expires_at > clock_timestamp()
        RETURNING principal_id, budget_ids
      )
      INSERT INTO companion_devices (
        device_id, principal_id, device_name, credential_hash, budget_ids
      )
      SELECT $2, principal_id, $3, $4, budget_ids FROM consumed
      RETURNING device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at`,
      [input.codeHash, input.deviceId, input.deviceName, input.credentialHash],
    );
    const row = result.rows[0];
    return row === undefined ? undefined : mapDeviceRow(row);
  }

  async authenticate(
    credentialHash: string,
  ): Promise<DeviceRecord | undefined> {
    const result = await this.pool.query<DeviceRow>(
      `UPDATE companion_devices
      SET last_seen_at = clock_timestamp()
      WHERE credential_hash = $1 AND revoked_at IS NULL
      RETURNING device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at`,
      [credentialHash],
    );
    const row = result.rows[0];
    return row === undefined ? undefined : mapDeviceRow(row);
  }

  async listDevices(principalId: string): Promise<DeviceRecord[]> {
    const result = await this.pool.query<DeviceRow>(
      `SELECT device_id, principal_id, device_name, budget_ids,
        created_at, last_seen_at, revoked_at
      FROM companion_devices
      WHERE principal_id = $1
      ORDER BY created_at, device_id`,
      [principalId],
    );
    return result.rows.map(mapDeviceRow);
  }

  async revokeDevice(principalId: string, deviceId: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE companion_devices
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

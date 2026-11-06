import type { Pool } from "pg";
import { describe, expect, it, vi } from "vitest";
import { PostgresDeviceAuthStore } from "../src/auth/postgres-device-auth-store.js";

describe("PostgresDeviceAuthStore", () => {
  it("rejects a token revoked while its activity timestamp is refreshed", async () => {
    const query = vi
      .fn()
      .mockResolvedValueOnce({
        rows: [
          {
            device_id: "device-1",
            principal_id: "owner",
            device_name: "Phone",
            budget_ids: ["budget-1"],
            created_at: new Date("2026-01-01T00:00:00.000Z"),
            last_seen_at: null,
            revoked_at: null,
            should_refresh_last_seen: true,
          },
        ],
      })
      .mockResolvedValueOnce({ rows: [] })
      .mockResolvedValueOnce({ rows: [] });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(
      store.authenticate("credential-hash"),
    ).resolves.toBeUndefined();
    expect(query).toHaveBeenCalledTimes(3);
  });
});

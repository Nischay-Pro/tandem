import type { Pool } from "pg";
import { describe, expect, it, vi } from "vitest";
import { PostgresDeviceAuthStore } from "../src/auth/postgres-device-auth-store.js";

describe("PostgresDeviceAuthStore", () => {
  it("fails if the database does not return the created device", async () => {
    const query = vi.fn().mockResolvedValue({ rows: [] });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(
      store.createDevice({
        principalId: "owner",
        budgetIds: ["budget-1"],
        deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
        deviceName: "Phone",
        credentialHash: "synthetic-hash",
      }),
    ).rejects.toThrow("Device sign-in did not persist");
  });

  it("maps device rows when listing devices", async () => {
    const query = vi.fn().mockResolvedValue({
      rows: [
        {
          device_id: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
          principal_id: "owner",
          device_name: "Phone",
          budget_ids: ["budget-1"],
          created_at: new Date("2026-11-01T00:00:00.000Z"),
          last_seen_at: null,
          revoked_at: null,
        },
      ],
    });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(store.listDevices("owner")).resolves.toEqual([
      {
        deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
        principalId: "owner",
        name: "Phone",
        budgetIds: ["budget-1"],
        createdAt: new Date("2026-11-01T00:00:00.000Z"),
        lastSeenAt: null,
        revokedAt: null,
      },
    ]);
  });

  it("returns false when no active device was revoked", async () => {
    const query = vi.fn().mockResolvedValue({ rowCount: 0 });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(
      store.revokeDevice("owner", "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10"),
    ).resolves.toBe(false);
  });

  it("uses the existing activity timestamp when it is still fresh", async () => {
    const query = vi.fn().mockResolvedValue({
      rows: [
        {
          device_id: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
          principal_id: "owner",
          device_name: "Phone",
          budget_ids: ["budget-1"],
          created_at: new Date("2026-11-01T00:00:00.000Z"),
          last_seen_at: new Date("2026-11-10T00:00:00.000Z"),
          revoked_at: null,
          should_refresh_last_seen: false,
        },
      ],
    });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(store.authenticate("credential-hash")).resolves.toMatchObject({
      name: "Phone",
      lastSeenAt: new Date("2026-11-10T00:00:00.000Z"),
    });
    expect(query).toHaveBeenCalledOnce();
  });

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

  it("accepts a token after another request refreshed its activity timestamp", async () => {
    const refreshedAt = new Date("2026-11-10T12:00:00.000Z");
    const query = vi
      .fn()
      .mockResolvedValueOnce({
        rows: [
          {
            device_id: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
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
      .mockResolvedValueOnce({
        rows: [
          {
            device_id: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
            principal_id: "owner",
            device_name: "Phone",
            budget_ids: ["budget-1"],
            created_at: new Date("2026-01-01T00:00:00.000Z"),
            last_seen_at: refreshedAt,
            revoked_at: null,
          },
        ],
      });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    await expect(store.authenticate("credential-hash")).resolves.toMatchObject({
      deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
      lastSeenAt: refreshedAt,
    });
    expect(query).toHaveBeenCalledTimes(3);
  });
});

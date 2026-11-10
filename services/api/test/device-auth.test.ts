import { describe, expect, it, vi } from "vitest";
import {
  DeviceAuthorization,
  DeviceAuthInputError,
  hashSecret,
  type DeviceAuthStore,
  type DeviceRecord,
} from "../src/auth/device-auth.js";

const validUuid = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10";

function createStore(overrides: Partial<DeviceAuthStore> = {}) {
  const devices: DeviceRecord[] = [];
  const store: DeviceAuthStore = {
    async createDevice(input) {
      const device: DeviceRecord = {
        deviceId: input.deviceId,
        principalId: input.principalId,
        name: input.deviceName,
        budgetIds: input.budgetIds,
        createdAt: new Date("2026-11-01T00:00:00.000Z"),
        lastSeenAt: null,
        revokedAt: null,
      };
      devices.push(device);
      return device;
    },
    async authenticate() {
      return devices[0];
    },
    async listDevices() {
      return devices;
    },
    async revokeDevice() {
      return true;
    },
    ...overrides,
  };
  return { store, devices };
}

describe("device authorization", () => {
  it("keeps a stable name on authorization input errors", () => {
    expect(new DeviceAuthInputError("synthetic invalid input").name).toBe(
      "DeviceAuthInputError",
    );
  });

  it("issues a random bearer credential and stores only its hash", async () => {
    const { store } = createStore();
    const storeCreate = vi.spyOn(store, "createDevice");
    const authorization = new DeviceAuthorization(
      store,
      () => Buffer.alloc(32, 7),
      () => validUuid,
    );

    const device = await authorization.createDevice({
      principalId: "actual-server:synthetic",
      budgetIds: ["*"],
      deviceName: "Phone",
    });

    expect(device).toMatchObject({
      deviceId: validUuid,
      principalId: "actual-server:synthetic",
      name: "Phone",
      budgetIds: ["*"],
    });
    expect(device.accessToken).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(storeCreate).toHaveBeenCalledWith({
      principalId: "actual-server:synthetic",
      budgetIds: ["*"],
      deviceId: validUuid,
      deviceName: "Phone",
      credentialHash: hashSecret(device.accessToken),
    });
    expect(JSON.stringify(storeCreate.mock.calls)).not.toContain(
      device.accessToken,
    );
  });

  it("rejects invalid principal, budget, and device fields before storage", async () => {
    const { store } = createStore();
    const create = vi.spyOn(store, "createDevice");
    const authorization = new DeviceAuthorization(store);

    await expect(
      authorization.createDevice({
        principalId: " ",
        budgetIds: ["budget"],
        deviceName: "Phone",
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);
    await expect(
      authorization.createDevice({
        principalId: "owner",
        budgetIds: [""],
        deviceName: "Phone",
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);
    await expect(
      authorization.createDevice({
        principalId: "owner",
        budgetIds: ["*", "budget"],
        deviceName: "Phone",
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);
    await expect(
      authorization.createDevice({
        principalId: "owner",
        budgetIds: ["budget"],
        deviceName: "\n",
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);
    expect(create).not.toHaveBeenCalled();
  });

  it("accepts only well-formed credentials and resolves the hashed token", async () => {
    const { store } = createStore();
    const authenticate = vi.spyOn(store, "authenticate");
    const authorization = new DeviceAuthorization(store);

    await expect(
      authorization.authenticate(undefined),
    ).resolves.toBeUndefined();
    await expect(authorization.authenticate("short")).resolves.toBeUndefined();
    expect(authenticate).not.toHaveBeenCalled();

    const token = "A".repeat(43);
    await authorization.authenticate(token);
    expect(authenticate).toHaveBeenCalledWith(hashSecret(token));
  });

  it("applies the server-side budget scope to device listing and authorization", async () => {
    const owner = {
      deviceId: validUuid,
      principalId: "actual-server:synthetic",
      name: "Phone",
      budgetIds: ["budget-one", "budget-two"],
      createdAt: new Date("2026-11-01T00:00:00.000Z"),
      lastSeenAt: null,
      revokedAt: null,
    } satisfies DeviceRecord;
    const second = {
      ...owner,
      deviceId: "d2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
      name: "Tablet",
    };
    const { store } = createStore({
      async listDevices() {
        return [owner, second];
      },
    });
    const authorization = new DeviceAuthorization(store);

    await expect(authorization.listDevicesForDevice(owner)).resolves.toEqual([
      owner,
    ]);
    await expect(
      authorization.listDevicesForDevice({ ...owner, budgetIds: ["*"] }),
    ).resolves.toEqual([owner, second]);
    expect(DeviceAuthorization.canAccessBudget(owner, "budget-one")).toBe(true);
    expect(DeviceAuthorization.canAccessBudget(owner, "other")).toBe(false);
  });
});

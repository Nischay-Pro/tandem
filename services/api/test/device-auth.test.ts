import { describe, expect, it, vi } from "vitest";
import {
  DeviceAuthorization,
  DeviceAuthInputError,
  PAIRING_CODE_TTL_MS,
  hashPairingCode,
  hashSecret,
  type DeviceAuthStore,
  type DeviceRecord,
  type PairingCodeRecord,
} from "../src/auth/device-auth.js";

const validUuid = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10";
const validToken = "A".repeat(43);

function createDevice(overrides: Partial<DeviceRecord> = {}): DeviceRecord {
  return {
    deviceId: validUuid,
    principalId: "owner",
    name: "Phone",
    budgetIds: ["budget-one"],
    createdAt: new Date("2026-11-01T00:00:00.000Z"),
    lastSeenAt: null,
    revokedAt: null,
    ...overrides,
  };
}

function createStore(overrides: Partial<DeviceAuthStore> = {}) {
  const pairingCodes: PairingCodeRecord[] = [];
  const devices: DeviceRecord[] = [];
  const store: DeviceAuthStore = {
    async createPairingCode(record) {
      pairingCodes.push(record);
    },
    async consumePairingCode() {
      const device = createDevice();
      devices.push(device);
      return device;
    },
    async authenticate() {
      return createDevice();
    },
    async listDevices() {
      return devices;
    },
    async revokeDevice() {
      return true;
    },
    ...overrides,
  };
  return { store, pairingCodes, devices };
}

describe("device authorization", () => {
  it("keeps a stable name on authorization input errors", () => {
    expect(new DeviceAuthInputError("synthetic invalid input").name).toBe(
      "DeviceAuthInputError",
    );
  });

  it("counts Unicode code points at the principal, budget, and device-name limits", async () => {
    const { store, pairingCodes } = createStore({
      consumePairingCode: vi.fn(async ({ deviceName }) =>
        createDevice({ name: deviceName }),
      ),
    });
    const authorization = new DeviceAuthorization(store);
    const principalId = "😀".repeat(200);
    const budgetId = "💰".repeat(500);

    const pairing = await authorization.createPairingCode({
      principalId,
      budgetIds: [budgetId],
    });
    expect(pairing.principalId).toBe(principalId);
    expect(pairing.budgetIds).toEqual([budgetId]);
    await expect(
      authorization.createPairingCode({
        principalId: `${principalId}😀`,
        budgetIds: [budgetId],
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);
    await expect(
      authorization.createPairingCode({
        principalId,
        budgetIds: [`${budgetId}💰`],
      }),
    ).rejects.toBeInstanceOf(DeviceAuthInputError);

    const paired = await authorization.pair({
      code: pairing.code,
      deviceName: "📱".repeat(100),
    });
    expect(paired?.name).toBe("📱".repeat(100));
    expect(pairingCodes[0]?.principalId).toBe(principalId);
    expect(pairingCodes[0]?.budgetIds).toEqual([budgetId]);
  });

  it("creates short-lived pairing codes and persists only their hashes", async () => {
    const { store, pairingCodes } = createStore();
    const now = new Date("2026-11-01T10:00:00.000Z");
    const pairingCodeHashKey = "synthetic-server-key-for-test";
    const authorization = new DeviceAuthorization(
      store,
      () => now,
      undefined,
      undefined,
      { pairingCodeHashKey },
    );
    expect(PAIRING_CODE_TTL_MS).toBe(300_000);

    const pairing = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds: ["budget-one", "budget-two"],
    });

    expect(pairing.code).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}$/);
    expect(pairing.expiresAt.getTime()).toBe(
      now.getTime() + PAIRING_CODE_TTL_MS,
    );
    expect(pairing).toMatchObject({
      principalId: "owner",
      budgetIds: ["budget-one", "budget-two"],
    });
    expect(pairingCodes).toEqual([
      {
        codeHash: hashPairingCode(pairing.code, pairingCodeHashKey),
        principalId: "owner",
        budgetIds: ["budget-one", "budget-two"],
        expiresAt: pairing.expiresAt,
      },
    ]);
    expect(pairingCodes[0]?.codeHash).not.toBe(pairing.code);
  });

  it("exchanges a valid one-time code for an opaque device token", async () => {
    const device = createDevice({ name: "Tablet" });
    const consumePairingCode = vi.fn(async () => device);
    let secretNumber = 0;
    const { store } = createStore({ consumePairingCode });
    const authorization = new DeviceAuthorization(
      store,
      () => new Date("2026-11-01T10:00:00.000Z"),
      () => Buffer.alloc(32, ++secretNumber),
      () => validUuid,
    );
    const pairing = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds: ["budget-one"],
    });

    const paired = await authorization.pair({
      code: pairing.code.toLowerCase().replace("-", ""),
      deviceName: "  Tablet  ",
    });

    expect(paired).toEqual({
      ...device,
      accessToken: Buffer.alloc(32, 1).toString("base64url"),
    });
    expect(consumePairingCode).toHaveBeenCalledWith({
      codeHash: hashSecret(pairing.code),
      deviceId: validUuid,
      deviceName: "Tablet",
      credentialHash: hashSecret(paired!.accessToken),
    });
    expect(paired?.accessToken).not.toBe(pairing.code);
  });

  it("does not consume malformed or already unavailable pairing codes", async () => {
    const consumePairingCode = vi.fn(async () => undefined);
    const { store } = createStore({ consumePairingCode });
    const authorization = new DeviceAuthorization(store);

    for (const code of ["short", "ABCD--EFGH", `x${validToken}`]) {
      await expect(
        authorization.pair({ code, deviceName: "Phone" }),
      ).resolves.toBeUndefined();
    }
    await expect(
      authorization.pair({ code: "4DF7-J1M9", deviceName: "Phone" }),
    ).resolves.toBeUndefined();
    expect(consumePairingCode).toHaveBeenCalledOnce();
  });

  it("hashes valid bearer tokens and rejects malformed credentials", async () => {
    const device = createDevice();
    const authenticate = vi.fn(async () => device);
    const { store } = createStore({ authenticate });
    const authorization = new DeviceAuthorization(store);

    await expect(
      authorization.authenticate(undefined),
    ).resolves.toBeUndefined();
    await expect(
      authorization.authenticate("not-a-token"),
    ).resolves.toBeUndefined();
    await expect(
      authorization.authenticate(`x${validToken}`),
    ).resolves.toBeUndefined();
    await expect(
      authorization.authenticate(`${validToken}x`),
    ).resolves.toBeUndefined();
    await expect(authorization.authenticate(validToken)).resolves.toBe(device);
    expect(authenticate).toHaveBeenCalledOnce();
    expect(authenticate).toHaveBeenCalledWith(hashSecret(validToken));
  });

  it("limits device listing and revocation to the caller's access scope", async () => {
    const limitedDevice = createDevice();
    const otherDevice = createDevice({
      deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c11",
      name: "Owner phone",
      budgetIds: ["*"],
    });
    const listDevices = vi.fn(async () => [limitedDevice, otherDevice]);
    const revokeDevice = vi.fn(async () => true);
    const { store } = createStore({ listDevices, revokeDevice });
    const authorization = new DeviceAuthorization(store);

    await expect(
      authorization.listDevicesForDevice(limitedDevice),
    ).resolves.toEqual([limitedDevice]);
    await expect(
      authorization.revokeDeviceForDevice(limitedDevice, otherDevice.deviceId),
    ).resolves.toBe(false);
    expect(revokeDevice).not.toHaveBeenCalled();
    await expect(
      authorization.revokeDeviceForDevice(
        limitedDevice,
        limitedDevice.deviceId,
      ),
    ).resolves.toBe(true);
    await expect(
      authorization.listDevicesForDevice(otherDevice),
    ).resolves.toEqual([limitedDevice, otherDevice]);
    await expect(
      authorization.revokeDeviceForDevice(otherDevice, limitedDevice.deviceId),
    ).resolves.toBe(true);
    expect(listDevices).toHaveBeenCalledWith("owner");
    expect(revokeDevice).toHaveBeenCalledTimes(2);
    expect(revokeDevice).toHaveBeenNthCalledWith(1, "owner", validUuid);
    expect(revokeDevice).toHaveBeenNthCalledWith(
      2,
      "owner",
      limitedDevice.deviceId,
    );
    await expect(
      authorization.revokeDeviceForDevice(limitedDevice, "not-a-uuid"),
    ).rejects.toThrow("deviceId must be a UUID");
    for (const deviceId of [
      `x${validUuid}`,
      `${validUuid}x`,
      42,
      { toString: () => validUuid },
    ]) {
      await expect(
        authorization.revokeDeviceForDevice(
          limitedDevice,
          deviceId as unknown as string,
        ),
      ).rejects.toThrow("deviceId must be a UUID");
    }
  });

  it("checks specific and wildcard budget access", () => {
    const device = createDevice();
    expect(DeviceAuthorization.canAccessBudget(device, "budget-one")).toBe(
      true,
    );
    expect(DeviceAuthorization.canAccessBudget(device, "budget-two")).toBe(
      false,
    );
    expect(DeviceAuthorization.canAccessBudget(device, "")).toBe(false);
    expect(
      DeviceAuthorization.canAccessBudget(
        createDevice({ budgetIds: ["*"] }),
        "budget-two",
      ),
    ).toBe(true);
    expect(
      DeviceAuthorization.canAccessBudget(
        createDevice({ budgetIds: ["*"] }),
        "",
      ),
    ).toBe(false);
  });

  it("rejects invalid principals, device names, and budget scopes", async () => {
    const consumePairingCode = vi.fn(async () => undefined);
    const { store } = createStore({ consumePairingCode });
    const authorization = new DeviceAuthorization(store);
    const invalidPrincipals = ["", " owner", "owner\n", "x".repeat(201)];
    for (const principalId of invalidPrincipals) {
      await expect(
        authorization.createPairingCode({ principalId, budgetIds: ["budget"] }),
      ).rejects.toThrow("principalId must be a valid non-empty identifier");
    }
    await expect(
      authorization.createPairingCode({
        principalId: null as unknown as string,
        budgetIds: ["budget"],
      }),
    ).rejects.toThrow("principalId must be a valid non-empty identifier");
    const maximumPrincipalId = "x".repeat(200);
    await expect(
      authorization.createPairingCode({
        principalId: "x",
        budgetIds: ["budget"],
      }),
    ).resolves.toMatchObject({ principalId: "x" });
    await expect(
      authorization.createPairingCode({
        principalId: maximumPrincipalId,
        budgetIds: ["budget"],
      }),
    ).resolves.toMatchObject({ principalId: maximumPrincipalId });
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: [] as string[],
      }),
    ).rejects.toThrow("budgetIds must contain between 1 and 100 values");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["*", "budget"],
      }),
    ).rejects.toThrow("Wildcard budget access cannot be combined");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["budget", "budget"],
      }),
    ).rejects.toThrow("budgetIds must not contain duplicates");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: Array.from({ length: 101 }, (_, index) => `budget-${index}`),
      }),
    ).rejects.toThrow("budgetIds must contain between 1 and 100 values");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: Array.from({ length: 100 }, (_, index) => `budget-${index}`),
      }),
    ).resolves.toMatchObject({
      budgetIds: Array.from({ length: 100 }, (_, index) => `budget-${index}`),
    });
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: [""],
      }),
    ).rejects.toThrow("budgetIds must contain valid non-empty identifiers");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: [null as unknown as string],
      }),
    ).rejects.toThrow("budgetIds must contain valid non-empty identifiers");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: [" budget"],
      }),
    ).rejects.toThrow("budgetIds must contain valid non-empty identifiers");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["x".repeat(501)],
      }),
    ).rejects.toThrow("budgetIds must contain valid non-empty identifiers");
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["x".repeat(500)],
      }),
    ).resolves.toMatchObject({ budgetIds: ["x".repeat(500)] });
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["*"],
      }),
    ).resolves.toMatchObject({ budgetIds: ["*"] });
    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["bad\u007f"],
      }),
    ).rejects.toThrow("budgetIds must contain valid non-empty identifiers");
    await expect(
      authorization.pair({ code: validToken, deviceName: " " }),
    ).rejects.toThrow("deviceName must contain between 1 and 100 characters");
    await expect(
      authorization.pair({ code: validToken, deviceName: "x".repeat(101) }),
    ).rejects.toThrow("deviceName must contain between 1 and 100 characters");
    await expect(
      authorization.pair({ code: validToken, deviceName: "x" }),
    ).resolves.toBeUndefined();
    await expect(
      authorization.pair({
        code: validToken,
        deviceName: `${" ".repeat(2)}${"x".repeat(100)}${" ".repeat(2)}`,
      }),
    ).resolves.toBeUndefined();
    await expect(
      authorization.pair({
        code: validToken,
        deviceName: null as unknown as string,
      }),
    ).rejects.toThrow("deviceName must contain between 1 and 100 characters");
    await expect(
      authorization.pair({ code: validToken, deviceName: "Phone\u0001" }),
    ).rejects.toThrow("deviceName must contain between 1 and 100 characters");
  });

  it("rejects a broken cryptographic random source", async () => {
    const { store } = createStore();
    const authorization = new DeviceAuthorization(store, undefined, () =>
      Buffer.alloc(16),
    );

    await expect(
      authorization.createPairingCode({
        principalId: "owner",
        budgetIds: ["budget"],
      }),
    ).rejects.toThrow("Secure token generator returned an invalid secret");
  });

  it("copies budget scopes instead of retaining a caller's mutable array", async () => {
    const { store } = createStore();
    const authorization = new DeviceAuthorization(store);
    const budgetIds = ["budget-one"];

    const pairing = await authorization.createPairingCode({
      principalId: "owner",
      budgetIds,
    });

    expect(pairing.budgetIds).not.toBe(budgetIds);
    expect(pairing.budgetIds).toEqual(budgetIds);
  });
});

import { createHash, createHmac } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import type { Pool } from "pg";
import { buildApiApp } from "../src/app.js";
import {
  DeviceAuthorization,
  LOCAL_IDENTITY_CONTEXT,
  type DeviceAuthStore,
} from "../src/auth/device-auth.js";
import { isPrivateNetworkAddress } from "../src/auth/local-identity-routes.js";
import { PostgresDeviceAuthStore } from "../src/auth/postgres-device-auth-store.js";

const deviceId = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10";
const credentialHash = createHash("sha256")
  .update("device-token", "utf8")
  .digest("hex");
const nonce = "A".repeat(43);

function createStore(hash: string | null = credentialHash) {
  const activeCredentialHash = vi.fn(async () => hash ?? undefined);
  const store = {
    createDevice: vi.fn(),
    authenticate: vi.fn(async () => undefined),
    listDevices: vi.fn(async () => []),
    revokeDevice: vi.fn(async () => false),
    activeCredentialHash,
  } as unknown as DeviceAuthStore;
  return { store, activeCredentialHash };
}

function expectedProof(hash: string) {
  return createHmac("sha256", hash)
    .update(`${LOCAL_IDENTITY_CONTEXT}\n${deviceId}\n${nonce}`, "utf8")
    .digest("base64url");
}

async function buildApp(store: DeviceAuthStore) {
  return buildApiApp(
    { ping: vi.fn(async () => undefined), close: vi.fn(async () => undefined) },
    { deviceAuth: new DeviceAuthorization(store) },
  );
}

describe("local identity", () => {
  it("recognises private and VPN network addresses only", () => {
    for (const address of [
      "10.0.0.2",
      "127.0.0.1",
      "172.16.4.1",
      "172.31.255.1",
      "192.168.1.10",
      "100.64.0.5",
      "::1",
      "fd12:3456::1",
      "fe80::1",
      "::ffff:192.168.1.10",
    ]) {
      expect(isPrivateNetworkAddress(address)).toBe(true);
    }
    for (const address of [
      "8.8.8.8",
      "172.32.0.1",
      "192.169.1.1",
      "100.128.0.1",
      "2001:4860::8888",
      "not-an-ip",
    ]) {
      expect(isPrivateNetworkAddress(address)).toBe(false);
    }
  });

  it("proves identity with the device credential hash", async () => {
    const { store, activeCredentialHash } = createStore();
    const authorization = new DeviceAuthorization(store);

    expect(await authorization.localIdentityProof(deviceId, nonce)).toBe(
      expectedProof(credentialHash),
    );
    expect(activeCredentialHash).toHaveBeenCalledWith(deviceId);
  });

  it("answers unknown or malformed devices with an unusable proof", async () => {
    const { store, activeCredentialHash } = createStore(null);
    const authorization = new DeviceAuthorization(store);

    expect(await authorization.localIdentityProof(deviceId, nonce)).not.toBe(
      expectedProof(credentialHash),
    );
    await authorization.localIdentityProof("not-a-uuid", nonce);
    expect(activeCredentialHash).toHaveBeenCalledTimes(1);
  });

  it("answers only requests from private networks", async () => {
    const app = await buildApp(createStore().store);
    const request = (remoteAddress: string, body: unknown) =>
      app.inject({
        method: "POST",
        url: "/api/v1/local-identity",
        remoteAddress,
        payload: body as Record<string, unknown>,
      });

    const local = await request("192.168.1.20", { deviceId, nonce });
    expect(local.statusCode).toBe(200);
    expect(local.headers["cache-control"]).toBe("no-store");
    expect(local.json()).toEqual({ proof: expectedProof(credentialHash) });

    const remote = await request("203.0.113.9", { deviceId, nonce });
    expect(remote.statusCode).toBe(404);

    const invalid = await request("192.168.1.20", { deviceId, nonce: "short" });
    expect(invalid.statusCode).toBe(400);
    await app.close();
  });

  it("rate limits local identity checks per address", async () => {
    const app = await buildApp(createStore().store);
    let last = 0;
    for (let index = 0; index < 31; index += 1) {
      const response = await app.inject({
        method: "POST",
        url: "/api/v1/local-identity",
        remoteAddress: "10.0.0.8",
        payload: { deviceId, nonce },
      });
      last = response.statusCode;
      if (last === 429) {
        expect(response.headers["retry-after"]).toBeDefined();
      }
    }
    expect(last).toBe(429);
    await app.close();
  });

  it("reads only active, unexpired device credentials", async () => {
    const query = vi
      .fn()
      .mockResolvedValueOnce({ rows: [{ credential_hash: "stored-hash" }] })
      .mockResolvedValueOnce({ rows: [] });
    const store = new PostgresDeviceAuthStore({ query } as unknown as Pool);

    expect(await store.activeCredentialHash(deviceId)).toBe("stored-hash");
    expect(await store.activeCredentialHash(deviceId)).toBeUndefined();
    expect(query.mock.calls[0]?.[0]).toContain("revoked_at IS NULL");
  });
});

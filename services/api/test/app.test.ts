import type { Access, BudgetAccess } from "../src/actual-access.js";
import { buildApp } from "../src/app.js";
import { type ExtensionStore, InMemoryExtensionStore } from "../src/store.js";
import { describe, expect, it } from "vitest";

const budget = "9f1c2a6e-budget";
const record = {
  recordType: "planning.savings_goal",
  schemaVersion: 1,
  revision: 1,
  baseRevision: null,
  deleted: false,
  iv: "AAECAwQFBgcICQoL",
  ciphertext: "c2VhbGVk",
};

function allow(answer: Access = "allowed"): BudgetAccess & { calls: string[] } {
  const calls: string[] = [];
  return {
    calls,
    async check(token, budgetId) {
      calls.push(`${token}:${budgetId}`);
      return answer;
    },
  };
}

async function app(
  store: ExtensionStore = new InMemoryExtensionStore(),
  access: BudgetAccess = allow(),
) {
  return buildApp({ store, access, actualServerUrl: "https://actual.example" });
}

const auth = { "x-actual-token": "token" };

describe("extension sync", () => {
  it("limits each session's request rate", async () => {
    const known = { ...allow(), isKnown: (token: string) => token !== "guess" };
    const server = await buildApp({
      store: new InMemoryExtensionStore(),
      access: known,
      actualServerUrl: "https://actual.example",
      requestsPerMinute: 2,
    });
    const get = (headers: Record<string, string> = {}) =>
      server.inject({
        method: "GET",
        url: "/extension/v1/budgets/b1/changes",
        headers,
      });
    expect((await get(auth)).statusCode).toBe(200);
    expect((await get(auth)).statusCode).toBe(200);
    const limited = await get(auth);
    expect(limited.statusCode).toBe(429);
    expect(limited.json().error.code).toBe("rate_limited");
    expect((await get({ "x-actual-token": "other" })).statusCode).toBe(200);
    expect((await get()).statusCode).toBe(401);
  });

  it("limits unverified tokens by client address", async () => {
    const access = allow("denied");
    const server = await buildApp({
      store: new InMemoryExtensionStore(),
      access,
      actualServerUrl: "https://actual.example",
      requestsPerMinute: 2,
      trustProxy: true,
    });
    const get = (token: string, ip: string) =>
      server.inject({
        method: "GET",
        url: "/extension/v1/budgets/b1/changes",
        headers: { "x-actual-token": token, "x-forwarded-for": ip },
      });
    expect((await get("a", "203.0.113.1")).statusCode).toBe(403);
    expect((await get("b", "203.0.113.1")).statusCode).toBe(403);
    expect((await get("c", "203.0.113.1")).statusCode).toBe(429);
    expect((await get("d", "203.0.113.2")).statusCode).toBe(403);
    expect(access.calls).toHaveLength(3);
  });

  it("reports health and capabilities", async () => {
    const server = await app();
    expect(
      (await server.inject({ url: "/extension/v1/health/live" })).json(),
    ).toEqual({ status: "ok" });
    expect(
      (await server.inject({ url: "/extension/v1/health/ready" })).json(),
    ).toEqual({ status: "ok" });
    expect(
      (await server.inject({ url: "/extension/v1/capabilities" })).json(),
    ).toEqual({
      apiVersion: 1,
      features: ["planning-records", "planning-key"],
    });
    const broken = await app({
      ...new InMemoryExtensionStore(),
      ping: async () => Promise.reject(new Error("down")),
    } as unknown as ExtensionStore);
    const ready = await broken.inject({ url: "/extension/v1/health/ready" });
    expect(ready.statusCode).toBe(503);
    expect(ready.json().error.code).toBe("storage_unavailable");
  });

  it("requires an Actual session that can open the budget", async () => {
    const unsigned = await (
      await app()
    ).inject({ url: `/extension/v1/budgets/${budget}/changes` });
    expect(unsigned.statusCode).toBe(401);
    const denied = await (
      await app(undefined, allow("denied"))
    ).inject({ url: `/extension/v1/budgets/${budget}/changes`, headers: auth });
    expect(denied.statusCode).toBe(403);
    expect(denied.json().error.code).toBe("budget_forbidden");
    const down = await (
      await app(undefined, allow("unavailable"))
    ).inject({ url: `/extension/v1/budgets/${budget}/key`, headers: auth });
    expect(down.statusCode).toBe(503);
    for (const [method, url, payload] of [
      [
        "PUT",
        `/extension/v1/budgets/${budget}/key`,
        {
          keyId: "k",
          wrappedKey: "AA==",
          wrapSalt: "AA==",
          keyCheck: "AA==",
          expectedKeyId: null,
        },
      ],
      ["PUT", `/extension/v1/budgets/${budget}/records/r1`, record],
    ] as const) {
      expect(
        (
          await (
            await app(undefined, allow("denied"))
          ).inject({ method, url, payload, headers: auth })
        ).statusCode,
      ).toBe(403);
    }
    expect(
      (
        await (
          await app()
        ).inject({
          url: "/extension/v1/budgets/bad%20id/changes",
          headers: auth,
        })
      ).statusCode,
    ).toBe(400);
  });

  it("stores sealed records and pages changes by sequence", async () => {
    const access = allow();
    const server = await app(new InMemoryExtensionStore(), access);
    const put = (id: string, body: object) =>
      server.inject({
        method: "PUT",
        url: `/extension/v1/budgets/${budget}/records/${id}`,
        payload: body,
        headers: auth,
      });
    expect((await put("r1", record)).json()).toEqual({ seq: 1 });
    expect((await put("r2", record)).json()).toEqual({ seq: 2 });
    const stale = await put("r1", record);
    expect(stale.statusCode).toBe(409);
    expect(stale.json().current.revision).toBe(1);
    expect(
      (
        await put("r1", {
          ...record,
          revision: 2,
          baseRevision: 1,
          deleted: true,
        })
      ).json(),
    ).toEqual({ seq: 3 });
    expect(
      (await put("r3", { ...record, revision: 2, baseRevision: 1 })).json()
        .current,
    ).toBeNull();
    expect((await put("r3", { ...record, iv: "not base64!" })).statusCode).toBe(
      400,
    );
    const first = (
      await server.inject({
        url: `/extension/v1/budgets/${budget}/changes?limit=1`,
        headers: auth,
      })
    ).json();
    expect(first).toMatchObject({
      cursor: 2,
      hasMore: true,
      records: [{ recordId: "r2" }],
    });
    const rest = (
      await server.inject({
        url: `/extension/v1/budgets/${budget}/changes?after=2`,
        headers: auth,
      })
    ).json();
    expect(rest).toMatchObject({
      cursor: 3,
      hasMore: false,
      records: [{ recordId: "r1", deleted: true, revision: 2 }],
    });
    const none = (
      await server.inject({
        url: `/extension/v1/budgets/other/changes?after=0`,
        headers: auth,
      })
    ).json();
    expect(none).toEqual({
      records: [],
      cursor: 0,
      hasMore: false,
      restored: false,
    });
    const restored = async (query: string) =>
      (
        await server.inject({
          url: `/extension/v1/budgets/${budget}/changes?${query}`,
          headers: auth,
        })
      ).json().restored;
    expect(await restored("after=2&anchor=r2")).toBe(false);
    expect(await restored("after=2&anchor=r1")).toBe(true);
    expect(await restored("after=3")).toBe(false);
    expect(await restored("after=99")).toBe(true);
    expect(
      await restored(`after=1&anchor=${encodeURIComponent("bad id")}`),
    ).toBeUndefined();
    expect(access.calls[0]).toBe(`token:${budget}`);
  });

  it("accepts the planning record ids the app creates", async () => {
    const server = await app(new InMemoryExtensionStore(), allow());
    const put = (id: string) =>
      server.inject({
        method: "PUT",
        url: `/extension/v1/budgets/${budget}/records/${id}`,
        payload: record,
        headers: auth,
      });
    for (const id of [
      "currency:USD",
      "fx:USD:EUR",
      "device:3f1c-a9",
      "goal.1_a",
    ]) {
      expect((await put(id)).statusCode).toBe(200);
    }
    expect((await put(encodeURIComponent("bad id"))).statusCode).toBe(400);
    expect((await put("x".repeat(101))).statusCode).toBeGreaterThanOrEqual(400);
    const changes = await server.inject({
      url: `/extension/v1/budgets/${budget}/changes?after=2&anchor=${encodeURIComponent("fx:USD:EUR")}`,
      headers: auth,
    });
    expect(changes.json().restored).toBe(false);
  });

  it("shares one wrapped planning key per budget", async () => {
    const server = await app();
    const url = `/extension/v1/budgets/${budget}/key`;
    const key = {
      keyId: "key-1",
      wrappedKey: "d3JhcHBlZA==",
      wrapSalt: "c2FsdA==",
      keyCheck: "Y2hlY2s=",
    };
    expect((await server.inject({ url, headers: auth })).statusCode).toBe(404);
    expect(
      (
        await server.inject({
          method: "PUT",
          url,
          headers: auth,
          payload: { ...key, expectedKeyId: null },
        })
      ).json(),
    ).toEqual({ status: "stored" });
    expect((await server.inject({ url, headers: auth })).json()).toEqual(key);
    const raced = await server.inject({
      method: "PUT",
      url,
      headers: auth,
      payload: { ...key, keyId: "key-2", expectedKeyId: null },
    });
    expect(raced.statusCode).toBe(409);
    expect(
      (
        await server.inject({
          method: "PUT",
          url,
          headers: auth,
          payload: { ...key, keyId: "key-2", expectedKeyId: "key-1" },
        })
      ).json(),
    ).toEqual({ status: "stored" });
  });

  it("hides unexpected storage failures from clients and logs them", async () => {
    class BrokenStore extends InMemoryExtensionStore {
      override async changes(): Promise<never> {
        throw new Error('relation "planning_records" does not exist');
      }
    }
    const lines: string[] = [];
    const server = await buildApp({
      store: new BrokenStore(),
      access: allow(),
      actualServerUrl: "https://actual.example",
      logger: { stream: { write: (line: string) => lines.push(line) } },
    });
    const failed = await server.inject({
      url: `/extension/v1/budgets/${budget}/changes`,
      headers: auth,
    });
    expect(failed.statusCode).toBe(500);
    expect(failed.json()).toEqual({
      error: {
        code: "internal_error",
        message: "Something went wrong on the server.",
      },
    });
    expect(failed.body).not.toContain("planning_records");
    expect(lines.join("")).toContain("planning_records");
  });

  it("rejects integers outside what storage can hold", async () => {
    const server = await app();
    const put = (body: object) =>
      server.inject({
        method: "PUT",
        url: `/extension/v1/budgets/${budget}/records/r1`,
        payload: { ...record, ...body },
        headers: auth,
      });
    for (const body of [
      { revision: 2 ** 63 },
      { revision: 2, baseRevision: 2 ** 63 },
      { schemaVersion: 2 ** 31 },
    ]) {
      const rejected = await put(body);
      expect(rejected.statusCode).toBe(400);
      expect(rejected.json().code).toBe("FST_ERR_VALIDATION");
    }
    expect(
      (
        await server.inject({
          url: `/extension/v1/budgets/${budget}/changes?after=99999999999999999999`,
          headers: auth,
        })
      ).statusCode,
    ).toBe(400);
  });

  it("publishes an OpenAPI description", async () => {
    const server = await app();
    await server.ready();
    const document = server.swagger() as { paths: Record<string, unknown> };
    expect(Object.keys(document.paths)).toContain(
      "/extension/v1/budgets/{budgetId}/records/{recordId}",
    );
    await new InMemoryExtensionStore().close();
  });
});

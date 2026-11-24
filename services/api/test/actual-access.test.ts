import { describe, expect, it } from "vitest";
import { ActualBudgetAccess } from "../src/actual-access.js";

function fakeFetch(
  responses: Array<{ ok: boolean; status: number; body?: unknown } | Error>,
) {
  const urls: string[] = [];
  const fetcher = async (
    url: string,
    init: { headers: Record<string, string> },
  ) => {
    urls.push(`${url} ${init.headers["x-actual-token"]}`);
    const next = responses.shift()!;
    if (next instanceof Error) throw next;
    return {
      ok: next.ok,
      status: next.status,
      json: async () =>
        next.body === "bad" ? Promise.reject(new Error("bad json")) : next.body,
    };
  };
  return { urls, fetcher };
}

describe("Actual budget access", () => {
  it("allows only budgets the session lists and caches the answer", async () => {
    let now = 0;
    const { urls, fetcher } = fakeFetch([
      {
        ok: true,
        status: 200,
        body: {
          data: [
            { groupId: "a" },
            { groupId: "gone", deleted: 1 },
            { name: "no group" },
          ],
        },
      },
      { ok: true, status: 200, body: { data: [] } },
    ]);
    const access = new ActualBudgetAccess(
      "https://actual.example",
      fetcher,
      () => now,
    );
    expect(await access.check("t", "a")).toBe("allowed");
    expect(await access.check("t", "gone")).toBe("denied");
    expect(await access.check("t", "a")).toBe("allowed");
    expect(urls).toEqual(["https://actual.example/sync/list-user-files t"]);
    now = 61_000;
    expect(await access.check("t", "a")).toBe("denied");
  });

  it("treats rejected sessions as denied and outages as unavailable", async () => {
    const { fetcher } = fakeFetch([
      { ok: false, status: 401 },
      { ok: false, status: 403 },
      { ok: false, status: 500 },
      new Error("offline"),
      { ok: true, status: 200, body: "bad" },
      { ok: true, status: 200, body: { status: "ok" } },
    ]);
    const access = new ActualBudgetAccess("https://actual.example", fetcher);
    expect(await access.check("1", "a")).toBe("denied");
    expect(await access.check("2", "a")).toBe("denied");
    expect(await access.check("3", "a")).toBe("unavailable");
    expect(await access.check("4", "a")).toBe("unavailable");
    expect(await access.check("5", "a")).toBe("unavailable");
    expect(await access.check("6", "a")).toBe("unavailable");
    expect(new ActualBudgetAccess("https://actual.example")).toBeInstanceOf(
      ActualBudgetAccess,
    );
  });

  it("remembers rejected tokens briefly and knows accepted ones", async () => {
    let now = 0;
    const { urls, fetcher } = fakeFetch([
      { ok: false, status: 401 },
      { ok: true, status: 200, body: { data: [{ groupId: "a" }] } },
      { ok: true, status: 200, body: { data: [{ groupId: "a" }] } },
    ]);
    const access = new ActualBudgetAccess(
      "https://actual.example",
      fetcher,
      () => now,
    );
    expect(await access.check("bad", "a")).toBe("denied");
    expect(await access.check("bad", "a")).toBe("denied");
    expect(access.isKnown("bad")).toBe(false);
    expect(urls).toHaveLength(1);
    now = 11_000;
    expect(await access.check("bad", "a")).toBe("allowed");
    expect(access.isKnown("bad")).toBe(true);
    expect(access.isKnown("never")).toBe(false);
    now = 80_000;
    expect(access.isKnown("bad")).toBe(false);
    expect(await access.check("bad", "a")).toBe("allowed");
  });

  it("keeps the cache bounded", async () => {
    let now = 0;
    const fetcher = async () => ({
      ok: false,
      status: 401,
      json: async () => ({}),
    });
    const access = new ActualBudgetAccess(
      "https://actual.example",
      fetcher,
      () => now,
    );
    for (let i = 0; i < 10_000; i++) await access.check(`t${i}`, "a");
    now = 5_000;
    await access.check("newest", "a");
    const size = (access as unknown as { cache: Map<string, unknown> }).cache
      .size;
    expect(size).toBe(10_000);
    now = 20_000;
    await access.check("after-expiry", "a");
    expect(
      (access as unknown as { cache: Map<string, unknown> }).cache.size,
    ).toBe(1);
  });
});

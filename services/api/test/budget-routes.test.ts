import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import {
  DeviceAuthorization,
  type DeviceRecord,
} from "../src/auth/device-auth.js";
import type { BudgetReadApiPort } from "../src/budget-projection/budget-read-api.js";
import { BudgetProjectionInputError } from "../src/budget-projection/contracts.js";

const accessToken = "A".repeat(43);
const generation = "d9d69fa5-45fb-48ee-9a8c-99bf49f873bd";
const snapshot = {
  budgetId: "budget-1",
  generation,
  cursor: "2",
  displaySettings: null,
  records: [
    {
      type: "account",
      id: "account-1",
      revision: "f".repeat(64),
      body: { id: "account-1", balance_current: 100 },
    },
  ],
};
const page = {
  budgetId: "budget-1",
  generation,
  currentCursor: "2",
  throughCursor: "2",
  nextCursor: "2",
  hasMore: false,
  resetRequired: false,
  changes: [],
  displaySettings: null,
};
const snapshotPage = {
  budgetId: "budget-1",
  generation,
  cursor: "2",
  records: snapshot.records,
  hasMore: false,
  resetRequired: false,
  nextAfterType: null,
  nextAfterId: null,
  displaySettings: null,
};

function createDevice(): DeviceRecord {
  return {
    deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
    principalId: "owner",
    name: "Synthetic phone",
    budgetIds: ["budget-1"],
    createdAt: new Date("2026-11-01T00:00:00.000Z"),
    lastSeenAt: null,
    revokedAt: null,
  };
}

function createDeviceAuth(device?: DeviceRecord) {
  return {
    createDevice: vi.fn(),
    pair: vi.fn(),
    authenticate: vi.fn(async (token?: string) =>
      token === accessToken ? device : undefined,
    ),
    listDevicesForDevice: vi.fn(),
    revokeDeviceForDevice: vi.fn(),
  } as unknown as DeviceAuthorization;
}

function createBudgetReadApi(overrides: Record<string, unknown> = {}) {
  return {
    listBudgets: vi.fn(async () => [
      { budgetId: "budget-1", name: "Household", isEncrypted: false },
      { budgetId: "private-budget", name: "Private", isEncrypted: true },
    ]),
    getSnapshot: vi.fn(async () => snapshot),
    getSnapshotPage: vi.fn(async () => snapshotPage),
    getChanges: vi.fn(async () => page),
    ...overrides,
  } as unknown as BudgetReadApiPort;
}

async function buildApp(
  budgetReadApi: BudgetReadApiPort | undefined,
  device = createDevice(),
) {
  return buildApiApp(
    { ping: vi.fn(async () => undefined), close: vi.fn(async () => undefined) },
    {
      deviceAuth: createDeviceAuth(device),
      ...(budgetReadApi === undefined ? {} : { budgetReadApi }),
    },
  );
}

const authHeaders = { authorization: `Bearer ${accessToken}` };

describe("budget snapshot and change feed routes", () => {
  it("lists named, encryption-aware budgets only within the device scope", async () => {
    const budgetApi = createBudgetReadApi({
      listBudgets: vi.fn(async () => [
        { budgetId: "budget-1", name: "Household", isEncrypted: false },
        {
          budgetId: "budget-1",
          name: "Duplicate household",
          isEncrypted: false,
        },
        { budgetId: "private-budget", name: "Private", isEncrypted: true },
      ]),
    });
    const app = await buildApp(budgetApi);
    const unauthorized = await app.inject({
      method: "GET",
      url: "/api/v1/budgets",
    });
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/budgets",
      headers: authHeaders,
    });

    expect(unauthorized.statusCode).toBe(401);
    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({
      budgets: [
        { budgetId: "budget-1", name: "Household", isEncrypted: false },
      ],
    });
    expect(budgetApi.listBudgets).toHaveBeenCalledOnce();
    await app.close();
  });

  it("reports unavailable or failed budget catalog reads", async () => {
    const withoutActual = await buildApp(undefined);
    const unavailable = await withoutActual.inject({
      method: "GET",
      url: "/api/v1/budgets",
      headers: authHeaders,
    });
    expect(unavailable.statusCode).toBe(503);
    await withoutActual.close();

    const failedApi = createBudgetReadApi({
      listBudgets: vi.fn(async () => {
        throw new Error("private Actual account data");
      }),
    });
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const app = await buildApp(failedApi);
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/budgets",
      headers: authHeaders,
    });
    expect(response.statusCode).toBe(502);
    expect(response.body).not.toContain("private Actual account data");
    expect(consoleError).toHaveBeenCalledWith("Budget catalog read failed", {
      errorType: "Error",
    });
    vi.restoreAllMocks();
    await app.close();
  });

  it("returns a no-store snapshot and forwards its budget encryption password", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot",
      headers: {
        ...authHeaders,
        "x-budget-encryption-password": "encrypted-budget-password",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual(snapshot);
    expect(budgetApi.getSnapshot).toHaveBeenCalledWith(
      "budget-1",
      "encrypted-budget-password",
    );
    await app.close();
  });

  it("returns bounded snapshot pages and forwards a continuation checkpoint", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const first = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages",
      headers: {
        ...authHeaders,
        "x-budget-encryption-password": "encrypted-budget-password",
      },
    });
    const next = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/snapshot-pages?generation=${generation}&cursor=2&afterType=transaction&afterId=txn-1&limit=500`,
      headers: authHeaders,
    });

    expect(first.statusCode).toBe(200);
    expect(first.headers["cache-control"]).toBe("no-store");
    expect(first.json()).toEqual(snapshotPage);
    expect(budgetApi.getSnapshotPage).toHaveBeenNthCalledWith(1, {
      budgetId: "budget-1",
      limit: 2_000,
      encryptionPassword: "encrypted-budget-password",
    });
    expect(next.statusCode).toBe(200);
    expect(budgetApi.getSnapshotPage).toHaveBeenNthCalledWith(2, {
      budgetId: "budget-1",
      generation,
      cursor: "2",
      afterType: "transaction",
      afterId: "txn-1",
      limit: 500,
      encryptionPassword: undefined,
    });
    await app.close();
  });

  it("rejects incomplete snapshot continuation parameters", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const missingCursor = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/snapshot-pages?generation=${generation}`,
      headers: authHeaders,
    });
    const missingAfterId = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/snapshot-pages?generation=${generation}&cursor=2&afterType=transaction`,
      headers: authHeaders,
    });
    const afterWithoutCheckpoint = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages?afterType=transaction&afterId=txn-1",
      headers: authHeaders,
    });
    const oversizedPage = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/snapshot-pages?limit=2001`,
      headers: authHeaders,
    });
    expect(missingCursor.statusCode).toBe(400);
    expect(missingAfterId.statusCode).toBe(400);
    expect(afterWithoutCheckpoint.statusCode).toBe(400);
    expect(oversizedPage.statusCode).toBe(400);
    expect(budgetApi.getSnapshotPage).not.toHaveBeenCalled();
    await app.close();
  });

  it("enforces snapshot page scope and reports unavailable or failed reads safely", async () => {
    const outOfScopeApi = createBudgetReadApi();
    const outOfScopeApp = await buildApp(outOfScopeApi);
    const outOfScope = await outOfScopeApp.inject({
      method: "GET",
      url: "/api/v1/budgets/private-budget/snapshot-pages",
      headers: authHeaders,
    });
    expect(outOfScope.statusCode).toBe(404);
    expect(outOfScopeApi.getSnapshotPage).not.toHaveBeenCalled();
    await outOfScopeApp.close();

    const unavailableApp = await buildApp(undefined);
    const unavailable = await unavailableApp.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages",
      headers: authHeaders,
    });
    expect(unavailable.statusCode).toBe(503);
    await unavailableApp.close();

    const invalidApi = createBudgetReadApi({
      getSnapshotPage: vi.fn(async () => {
        throw new BudgetProjectionInputError("invalid snapshot page input");
      }),
    });
    const invalidApp = await buildApp(invalidApi);
    const invalid = await invalidApp.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages",
      headers: authHeaders,
    });
    expect(invalid.statusCode).toBe(400);
    await invalidApp.close();

    const failedApi = createBudgetReadApi({
      getSnapshotPage: vi.fn(async () => {
        throw new Error("private Actual workspace path");
      }),
    });
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const failedApp = await buildApp(failedApi);
    const failed = await failedApp.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages",
      headers: authHeaders,
    });
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual workspace path");
    expect(consoleError).toHaveBeenCalledWith(
      "Budget snapshot page read failed",
      {
        errorType: "Error",
      },
    );
    vi.restoreAllMocks();
    await failedApp.close();
  });

  it("requires device credentials and hides budgets outside the device scope", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const unauthorized = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot",
    });
    const unauthorizedChanges = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0`,
    });
    const unauthorizedSnapshotPage = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot-pages",
    });
    const outOfScope = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/private-budget/snapshot",
      headers: authHeaders,
    });
    const outOfScopeChanges = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/private-budget/changes?generation=${generation}&cursor=0`,
      headers: authHeaders,
    });

    expect(unauthorized.statusCode).toBe(401);
    expect(unauthorizedChanges.statusCode).toBe(401);
    expect(unauthorizedSnapshotPage.statusCode).toBe(401);
    expect(outOfScope.statusCode).toBe(404);
    expect(outOfScopeChanges.statusCode).toBe(404);
    expect(budgetApi.getSnapshot).not.toHaveBeenCalled();
    expect(budgetApi.getSnapshotPage).not.toHaveBeenCalled();
    expect(budgetApi.getChanges).not.toHaveBeenCalled();
    await app.close();
  });

  it("reports unavailable Actual integration and sanitized refresh failures", async () => {
    const withoutActual = await buildApp(undefined);
    const unavailable = await withoutActual.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot",
      headers: authHeaders,
    });
    const unavailableChanges = await withoutActual.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0`,
      headers: authHeaders,
    });
    expect(unavailable.statusCode).toBe(503);
    expect(unavailableChanges.statusCode).toBe(503);
    await withoutActual.close();

    const failedApi = createBudgetReadApi({
      getSnapshot: vi.fn(async () => {
        throw new Error("private Actual workspace path");
      }),
    });
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const app = await buildApp(failedApi);
    const failure = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot",
      headers: authHeaders,
    });

    expect(failure.statusCode).toBe(502);
    expect(failure.body).not.toContain("private Actual workspace path");
    expect(failure.json()).toEqual({
      error: {
        code: "budget_sync_failed",
        message: "The budget projection could not be refreshed",
      },
    });
    expect(consoleError).toHaveBeenCalledWith("Budget snapshot read failed", {
      errorType: "Error",
    });
    vi.restoreAllMocks();
    await app.close();
  });

  it("maps projection input failures to a client error and validates budget IDs", async () => {
    const failedApi = createBudgetReadApi({
      getSnapshot: vi.fn(async () => {
        throw new BudgetProjectionInputError("invalid synthetic input");
      }),
    });
    const app = await buildApp(failedApi);
    const inputError = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/snapshot",
      headers: authHeaders,
    });
    const schemaError = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/%20/snapshot",
      headers: authHeaders,
    });
    expect(inputError.statusCode).toBe(400);
    expect(schemaError.statusCode).toBe(400);
    expect(failedApi.getSnapshot).toHaveBeenCalledOnce();
    await app.close();
  });

  it("refreshes the first changes page and preserves its fixed high-water cursor", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const initial = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0`,
      headers: {
        ...authHeaders,
        "x-budget-encryption-password": "encrypted-budget-password",
      },
    });
    const nextPage = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=1&throughCursor=2&limit=1`,
      headers: authHeaders,
    });

    expect(initial.statusCode).toBe(200);
    expect(initial.headers["cache-control"]).toBe("no-store");
    expect(initial.json()).toEqual(page);
    expect(budgetApi.getChanges).toHaveBeenNthCalledWith(1, {
      budgetId: "budget-1",
      generation,
      cursor: "0",
      limit: 100,
      encryptionPassword: "encrypted-budget-password",
    });
    expect(nextPage.statusCode).toBe(200);
    expect(budgetApi.getChanges).toHaveBeenNthCalledWith(2, {
      budgetId: "budget-1",
      generation,
      cursor: "1",
      throughCursor: "2",
      limit: 1,
    });
    await app.close();
  });

  it("rejects malformed feed cursors and enforces the page size maximum", async () => {
    const budgetApi = createBudgetReadApi();
    const app = await buildApp(budgetApi);
    const badCursor = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=01`,
      headers: authHeaders,
    });
    const badLimit = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0&limit=501`,
      headers: authHeaders,
    });
    const missingGeneration = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/budget-1/changes?cursor=0",
      headers: authHeaders,
    });
    expect(badCursor.statusCode).toBe(400);
    expect(badLimit.statusCode).toBe(400);
    expect(missingGeneration.statusCode).toBe(400);
    expect(budgetApi.getChanges).not.toHaveBeenCalled();
    await app.close();
  });

  it("maps change-feed input and refresh failures to safe responses", async () => {
    const invalidApi = createBudgetReadApi({
      getChanges: vi.fn(async () => {
        throw new BudgetProjectionInputError("invalid synthetic cursor");
      }),
    });
    const invalidApp = await buildApp(invalidApi);
    const invalid = await invalidApp.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0`,
      headers: authHeaders,
    });
    expect(invalid.statusCode).toBe(400);
    await invalidApp.close();

    const failedApi = createBudgetReadApi({
      getChanges: vi.fn(async () => {
        throw new Error("private Actual workspace path");
      }),
    });
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const failedApp = await buildApp(failedApi);
    const failed = await failedApp.inject({
      method: "GET",
      url: `/api/v1/budgets/budget-1/changes?generation=${generation}&cursor=0`,
      headers: authHeaders,
    });

    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual workspace path");
    expect(failed.json()).toEqual({
      error: {
        code: "budget_sync_failed",
        message: "The budget projection could not be refreshed",
      },
    });
    expect(consoleError).toHaveBeenCalledWith(
      "Budget change feed read failed",
      {
        errorType: "Error",
      },
    );
    vi.restoreAllMocks();
    await failedApp.close();
  });

  it("returns the server OpenAPI contract for the budget synchronization routes", async () => {
    const app = await buildApp(createBudgetReadApi());
    const response = await app.inject({
      method: "GET",
      url: "/api/v1/openapi.json",
    });
    const document = response.json() as {
      paths: Record<string, Record<string, unknown>>;
    };
    expect(
      document.paths["/api/v1/budgets/{budgetId}/snapshot"]?.get,
    ).toBeDefined();
    expect(
      document.paths["/api/v1/budgets/{budgetId}/snapshot-pages"]?.get,
    ).toBeDefined();
    expect(
      document.paths["/api/v1/budgets/{budgetId}/changes"]?.get,
    ).toBeDefined();
    await app.close();
  });
});

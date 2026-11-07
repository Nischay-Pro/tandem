import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import type { BudgetMaintenanceApiPort } from "../src/actual/budget-maintenance-api.js";
import { BudgetMaintenanceInputError } from "../src/actual/budget-maintenance-api.js";
import { BudgetMaintenanceBusyError } from "../src/actual/budget-maintenance-api.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";
import { IdempotencyKeyReuseError } from "../src/write-journal/contracts.js";
import type { ApiAppOptions } from "../src/app.js";
import type {
  WriteOperation,
  WriteOperationInput,
} from "../src/write-journal/contracts.js";

const budgetId = "budget-1";
const accessToken = "A".repeat(43);
const device: DeviceRecord = {
  deviceId: "device-1",
  principalId: "owner",
  name: "Test device",
  budgetIds: [budgetId],
  createdAt: new Date("2026-11-01T00:00:00.000Z"),
  lastSeenAt: null,
  revokedAt: null,
};

async function createApi(
  maintenanceConfigured = true,
  journalConfigured = true,
) {
  const budgetMaintenanceApi: BudgetMaintenanceApiPort = {
    runBankSync: vi.fn(async () => undefined),
    createAccount: vi.fn(async () => undefined),
    createCategory: vi.fn(async () => "category-created"),
    createPayee: vi.fn(async () => "payee-created"),
    renamePayee: vi.fn(async () => undefined),
    mergePayee: vi.fn(async () => undefined),
    deletePayee: vi.fn(async () => undefined),
    setBudgetAmount: vi.fn(async () => undefined),
    setBudgetCarryover: vi.fn(async () => undefined),
    copyBudgetMonth: vi.fn(async () => undefined),
    moveMoney: vi.fn(async () => undefined),
    closeAccount: vi.fn(async () => undefined),
    renameAccount: vi.fn(async () => undefined),
    reopenAccount: vi.fn(async () => undefined),
    renameCategory: vi.fn(async () => undefined),
    deleteCategory: vi.fn(async () => undefined),
    renameSchedule: vi.fn(async () => undefined),
    editSchedule: vi.fn(async () => undefined),
    skipSchedule: vi.fn(async () => undefined),
    postSchedule: vi.fn(async () => undefined),
    deleteSchedule: vi.fn(async () => undefined),
  };
  const authenticate = vi.fn(async (token?: string) =>
    token === accessToken ? device : undefined,
  );
  const deviceAuth = {
    authenticate,
    createPairingCode: vi.fn(),
    pair: vi.fn(),
    listDevicesForDevice: vi.fn(),
    revokeDeviceForDevice: vi.fn(),
  } as unknown as DeviceAuthorization;
  const ping = vi.fn(async () => undefined);
  const close = vi.fn(async () => undefined);
  const operations = new Map<string, WriteOperation>();
  const keys = new Map<string, WriteOperation>();
  const journal = {
    async enqueue(input: WriteOperationInput) {
      const key = `${input.principalId}:${input.budgetId}:${input.idempotencyKey}`;
      const requestHash = JSON.stringify([
        input.action,
        input.entityId,
        input.patch,
      ]);
      const existing = keys.get(key);
      if (existing) {
        if (existing.requestHash !== requestHash)
          throw new IdempotencyKeyReuseError();
        return { operation: existing, replayed: true };
      }
      const now = new Date();
      const operation: WriteOperation = {
        ...input,
        requestHash,
        status: "queued",
        attemptCount: 0,
        errorCode: null,
        actualResult: null,
        writeEvidence: null,
        createdAt: now,
        updatedAt: now,
        startedAt: null,
        leaseExpiresAt: null,
        completedAt: null,
      };
      keys.set(key, operation);
      operations.set(operation.operationId, operation);
      return { operation, replayed: false };
    },
    async claim(operationId: string) {
      const operation = operations.get(operationId);
      if (!operation) throw new Error("Missing operation");
      if (operation.status !== "queued") return { operation, claimed: false };
      operation.status = "applying";
      operation.attemptCount += 1;
      return { operation, claimed: true };
    },
    async markCommitted(
      operationId: string,
      result: WriteOperation["actualResult"],
    ) {
      const operation = operations.get(operationId);
      if (!operation) throw new Error("Missing operation");
      operation.status = "committed";
      operation.actualResult = result;
      return operation;
    },
    async markRejected(operationId: string, errorCode: string) {
      const operation = operations.get(operationId);
      if (!operation) throw new Error("Missing operation");
      operation.status = "rejected";
      operation.errorCode = errorCode;
      return operation;
    },
    async markUncertain(operationId: string, errorCode: string) {
      const operation = operations.get(operationId);
      if (!operation) throw new Error("Missing operation");
      operation.status = "uncertain";
      operation.errorCode = errorCode;
      return operation;
    },
  } as NonNullable<ApiAppOptions["maintenanceJournal"]>;
  return {
    budgetMaintenanceApi,
    authenticate,
    journal,
    operations,
    app: await buildApiApp(
      { ping, close },
      {
        deviceAuth,
        ...(maintenanceConfigured ? { budgetMaintenanceApi } : {}),
        ...(journalConfigured ? { maintenanceJournal: journal } : {}),
      },
    ),
  };
}

const testHeaders = () => ({
  authorization: `Bearer ${accessToken}`,
  "idempotency-key": randomUUID(),
});

const maintenanceRouteCases = (routeBudgetId = budgetId) => [
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/bank-sync`,
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/accounts`,
    payload: { name: "Checking", offbudget: false },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/accounts/account-1/close`,
    payload: { transferAccountId: null, transferCategoryId: null },
  },
  {
    method: "PATCH" as const,
    url: `/api/v1/budgets/${routeBudgetId}/accounts/account-1`,
    payload: { name: "Daily checking" },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/accounts/account-1/reopen`,
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/payees`,
    payload: { name: "Corner Market" },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/payees/payee-1/actions`,
    payload: { action: "delete" },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories`,
    payload: { name: "Dining", categoryGroupId: "group-1" },
  },
  {
    method: "PUT" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories/category-1/budget-amount`,
    payload: { month: "2026-11", amountMinor: 1200 },
  },
  {
    method: "PUT" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories/category-1/carryover`,
    payload: { month: "2026-11", enabled: true },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/months/2026-11/actions`,
    payload: { action: "copy-last-month" },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories/move-money`,
    payload: {
      month: "2026-11",
      fromCategoryId: "category-1",
      toCategoryId: "category-2",
      amountMinor: 1200,
    },
  },
  {
    method: "PATCH" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories/category-1`,
    payload: { name: "Home care" },
  },
  {
    method: "DELETE" as const,
    url: `/api/v1/budgets/${routeBudgetId}/categories/category-1`,
    payload: { transferCategoryId: null },
  },
  {
    method: "POST" as const,
    url: `/api/v1/budgets/${routeBudgetId}/schedules/schedule-1/actions`,
    payload: { action: "post" },
  },
];

const injectMaintenanceRequest = (
  app: Awaited<ReturnType<typeof buildApiApp>>,
  route: ReturnType<typeof maintenanceRouteCases>[number],
  headers?: ReturnType<typeof testHeaders>,
) =>
  app.inject({
    method: route.method,
    url: route.url,
    ...(headers ? { headers } : {}),
    ...(route.payload === undefined ? {} : { payload: route.payload }),
  });

describe("budget maintenance routes", () => {
  it("applies auth, validation, budget-scope, and configuration gates to every route", async () => {
    const routes = maintenanceRouteCases();
    const unauthorizedFake = await createApi();
    const unauthorized = await Promise.all(
      routes.map((route) =>
        injectMaintenanceRequest(unauthorizedFake.app, route),
      ),
    );
    expect(unauthorized.map((response) => response.statusCode)).toEqual(
      routes.map(() => 401),
    );
    await unauthorizedFake.app.close();

    const invalidBudgetFake = await createApi();
    const invalidBudget = await Promise.all(
      maintenanceRouteCases("%20").map((route) =>
        injectMaintenanceRequest(invalidBudgetFake.app, route, testHeaders()),
      ),
    );
    expect(invalidBudget.map((response) => response.statusCode)).toEqual(
      routes.map(() => 400),
    );
    await invalidBudgetFake.app.close();

    const outOfScopeFake = await createApi();
    const outOfScope = await Promise.all(
      maintenanceRouteCases("other-budget").map((route) =>
        injectMaintenanceRequest(outOfScopeFake.app, route, testHeaders()),
      ),
    );
    expect(outOfScope.map((response) => response.statusCode)).toEqual(
      routes.map(() => 404),
    );
    await outOfScopeFake.app.close();

    const missingActualFake = await createApi(false);
    const missingActual = await Promise.all(
      routes.map((route) =>
        injectMaintenanceRequest(missingActualFake.app, route, testHeaders()),
      ),
    );
    expect(missingActual.map((response) => response.statusCode)).toEqual(
      routes.map(() => 503),
    );
    await missingActualFake.app.close();

    const missingJournalFake = await createApi(true, false);
    const missingJournal = await Promise.all(
      routes.map((route) =>
        injectMaintenanceRequest(missingJournalFake.app, route, testHeaders()),
      ),
    );
    expect(missingJournal.map((response) => response.statusCode)).toEqual(
      routes.map(() => 503),
    );
    await missingJournalFake.app.close();
  });

  it("requires a paired device and budget scope for bank sync", async () => {
    const fake = await createApi();
    const unauthorized = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
    });
    const outOfScope = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/other-budget/bank-sync",
      headers: testHeaders(),
    });

    expect(unauthorized.statusCode).toBe(401);
    expect(outOfScope.statusCode).toBe(404);
    expect(fake.budgetMaintenanceApi.runBankSync).not.toHaveBeenCalled();
    await fake.app.close();
  });

  it("rejects unauthenticated account and category writes", async () => {
    const fake = await createApi();
    const create = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      payload: { name: "Savings", offbudget: false },
    });
    const rename = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      payload: { name: "Home" },
    });
    const remove = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      payload: { transferCategoryId: null },
    });

    expect(create.statusCode).toBe(401);
    expect(rename.statusCode).toBe(401);
    expect(remove.statusCode).toBe(401);
    await fake.app.close();
  });

  it("validates budget parameters and account/category bodies", async () => {
    const fake = await createApi();
    const invalidBudget = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/%20/bank-sync",
      headers: testHeaders(),
    });
    const invalidAccount = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: testHeaders(),
      payload: { name: "Savings" },
    });
    const invalidCategory = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { name: "" },
    });

    expect(invalidBudget.statusCode).toBe(400);
    expect(invalidAccount.statusCode).toBe(400);
    expect(invalidCategory.statusCode).toBe(400);
    await fake.app.close();
  });

  it("rejects a category amount beyond the supported budget maximum", async () => {
    const fake = await createApi();
    const response = await fake.app.inject({
      method: "PUT",
      url: `/api/v1/budgets/${budgetId}/categories/category-1/budget-amount`,
      headers: testHeaders(),
      payload: { month: "2026-11", amountMinor: 100_000_000_001 },
    });

    expect(response.statusCode).toBe(400);
    expect(fake.budgetMaintenanceApi.setBudgetAmount).not.toHaveBeenCalled();
    await fake.app.close();
  });

  it("reports missing Actual integration for each maintenance operation", async () => {
    const fake = await createApi(false);
    const responses = await Promise.all([
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/bank-sync`,
        headers: testHeaders(),
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts`,
        headers: testHeaders(),
        payload: { name: "Savings", offbudget: false },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1/close`,
        headers: testHeaders(),
        payload: { transferAccountId: null, transferCategoryId: null },
      }),
      fake.app.inject({
        method: "PATCH",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1`,
        headers: testHeaders(),
        payload: { name: "Daily checking" },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1/reopen`,
        headers: testHeaders(),
      }),
      fake.app.inject({
        method: "PATCH",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: testHeaders(),
        payload: { name: "Home" },
      }),
      fake.app.inject({
        method: "DELETE",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: testHeaders(),
        payload: { transferCategoryId: null },
      }),
    ]);

    expect(responses.map((response) => response.statusCode)).toEqual([
      503, 503, 503, 503, 503, 503, 503,
    ]);
    expect(responses.map((response) => response.json().error.code)).toEqual(
      Array(7).fill("actual_not_configured"),
    );
    await fake.app.close();
  });

  it("runs the configured Actual bank sync with an encrypted-budget password", async () => {
    const fake = await createApi();
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers: { ...testHeaders(), "x-budget-encryption-password": "secret" },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
    expect(fake.budgetMaintenanceApi.runBankSync).toHaveBeenCalledWith(
      budgetId,
      "secret",
    );
    await fake.app.close();
  });

  it("replays a completed maintenance write without calling Actual twice", async () => {
    const fake = await createApi();
    const headers = testHeaders();
    const first = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers,
      payload: { name: "Savings", offbudget: false },
    });
    const replay = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers,
      payload: { name: "Savings", offbudget: false },
    });

    expect(first.statusCode).toBe(200);
    expect(replay.statusCode).toBe(200);
    expect(replay.json()).toEqual(first.json());
    expect(fake.budgetMaintenanceApi.createAccount).toHaveBeenCalledTimes(1);
    await fake.app.close();
  });

  it("rejects reusing a maintenance key for a different write", async () => {
    const fake = await createApi();
    const headers = testHeaders();
    await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers,
      payload: { name: "Savings", offbudget: false },
    });
    const replay = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers,
      payload: { name: "Checking", offbudget: false },
    });

    expect(replay.statusCode).toBe(409);
    expect(replay.json().error.code).toBe("idempotency_key_reused");
    expect(fake.budgetMaintenanceApi.createAccount).toHaveBeenCalledTimes(1);
    await fake.app.close();
  });

  it("requires an idempotency key on maintenance writes", async () => {
    const fake = await createApi();
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: { authorization: `Bearer ${accessToken}` },
      payload: { name: "Savings", offbudget: false },
    });

    expect(response.statusCode).toBe(400);
    expect(fake.budgetMaintenanceApi.createAccount).not.toHaveBeenCalled();
    await fake.app.close();
  });

  it("handles replay states and journal failures without retrying Actual", async () => {
    const route = {
      method: "POST" as const,
      url: `/api/v1/budgets/${budgetId}/accounts`,
      payload: { name: "Savings", offbudget: false },
    };

    for (const [status, code] of [
      ["applying", "operation_in_progress"],
      ["uncertain", "operation_uncertain"],
      ["rejected", "operation_rejected"],
      ["conflict", "operation_uncertain"],
    ] as const) {
      const fake = await createApi();
      const headers = testHeaders();
      const first = await fake.app.inject({ ...route, headers });
      const operation = fake.operations.values().next().value;
      expect(first.statusCode).toBe(200);
      expect(operation).toBeDefined();
      operation!.status = status;
      const replay = await fake.app.inject({ ...route, headers });

      expect(replay.statusCode).toBe(409);
      expect(replay.json().error.code).toBe(code);
      expect(fake.budgetMaintenanceApi.createAccount).toHaveBeenCalledOnce();
      await fake.app.close();
    }

    const queuedFake = await createApi();
    const queuedHeaders = testHeaders();
    await queuedFake.app.inject({ ...route, headers: queuedHeaders });
    const queuedOperation = queuedFake.operations.values().next().value!;
    queuedOperation.status = "queued";
    vi.spyOn(queuedFake.journal, "claim").mockResolvedValueOnce({
      operation: queuedOperation,
      claimed: false,
    });
    const queuedReplay = await queuedFake.app.inject({
      ...route,
      headers: queuedHeaders,
    });
    expect(queuedReplay.statusCode).toBe(409);
    expect(queuedReplay.json().error.code).toBe("operation_in_progress");
    expect(
      queuedFake.budgetMaintenanceApi.createAccount,
    ).toHaveBeenCalledOnce();
    await queuedFake.app.close();

    const expiredFake = await createApi();
    const expiredHeaders = testHeaders();
    await expiredFake.app.inject({ ...route, headers: expiredHeaders });
    const expiredOperation = expiredFake.operations.values().next().value!;
    expiredOperation.status = "committed";
    expiredOperation.actualResult = null;
    const expiredReplay = await expiredFake.app.inject({
      ...route,
      headers: expiredHeaders,
    });
    expect(expiredReplay.statusCode).toBe(409);
    expect(expiredReplay.json().error.code).toBe(
      "operation_result_unavailable",
    );
    await expiredFake.app.close();

    const missingKeyFake = await createApi();
    const blankKey = await missingKeyFake.app.inject({
      ...route,
      headers: {
        authorization: `Bearer ${accessToken}`,
        "idempotency-key": "   ",
      },
    });
    expect(blankKey.statusCode).toBe(400);
    expect(blankKey.json().error.code).toBe("invalid_request");
    await missingKeyFake.app.close();

    const enqueueFailureFake = await createApi();
    vi.spyOn(enqueueFailureFake.journal, "enqueue").mockRejectedValueOnce(
      new Error("private database detail"),
    );
    const enqueueFailure = await enqueueFailureFake.app.inject({
      ...route,
      headers: testHeaders(),
    });
    expect(enqueueFailure.statusCode).toBe(502);
    expect(enqueueFailure.body).not.toContain("private database detail");
    await enqueueFailureFake.app.close();
  });

  it("maps an in-progress replay consistently across every maintenance route", async () => {
    const fake = await createApi();
    const enqueue = fake.journal.enqueue.bind(fake.journal);
    vi.spyOn(fake.journal, "enqueue").mockImplementation(async (input) => {
      const result = await enqueue(input);
      result.operation.status = "applying";
      return result;
    });

    const responses = await Promise.all(
      maintenanceRouteCases().map((route) =>
        injectMaintenanceRequest(fake.app, route, testHeaders()),
      ),
    );

    expect(responses.map((response) => response.statusCode)).toEqual(
      maintenanceRouteCases().map(() => 409),
    );
    expect(responses.map((response) => response.json().error.code)).toEqual(
      maintenanceRouteCases().map(() => "operation_in_progress"),
    );
    for (const action of Object.values(fake.budgetMaintenanceApi)) {
      expect(action).not.toHaveBeenCalled();
    }
    await fake.app.close();
  });

  it("moves money between categories through the journaled maintenance route", async () => {
    const fake = await createApi();
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/categories/move-money`,
      headers: testHeaders(),
      payload: {
        month: "2026-11",
        fromCategoryId: "category-1",
        toCategoryId: "category-2",
        amountMinor: 12_500,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
    expect(fake.budgetMaintenanceApi.moveMoney).toHaveBeenCalledWith(
      budgetId,
      "2026-11",
      "category-1",
      "category-2",
      12_500,
      undefined,
    );
    await fake.app.close();
  });

  it("journals carryover changes and last-month copies", async () => {
    const fake = await createApi();
    const carryover = await fake.app.inject({
      method: "PUT",
      url: `/api/v1/budgets/${budgetId}/categories/category-1/carryover`,
      headers: testHeaders(),
      payload: { month: "2026-11", enabled: true },
    });
    expect(carryover.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.setBudgetCarryover).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      "2026-11",
      true,
      undefined,
    );

    const copy = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/months/2026-11/actions`,
      headers: testHeaders(),
      payload: { action: "copy-last-month" },
    });
    expect(copy.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.copyBudgetMonth).toHaveBeenCalledWith(
      budgetId,
      "2026-11",
      undefined,
    );
    await fake.app.close();
  });

  it("returns a safe error when Actual bank sync fails", async () => {
    const fake = await createApi();
    const headers = testHeaders();
    vi.mocked(fake.budgetMaintenanceApi.runBankSync).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers,
    });
    const retry = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers,
    });

    expect(response.statusCode).toBe(502);
    expect(response.json().error).toMatchObject({ code: "bank_sync_failed" });
    expect(response.body).not.toContain("private Actual detail");
    expect(retry.statusCode).toBe(409);
    expect(retry.json().error.code).toBe("operation_uncertain");
    expect(fake.budgetMaintenanceApi.runBankSync).toHaveBeenCalledTimes(1);
    await fake.app.close();
  });

  it("limits repeated bank sync requests without starting another provider call", async () => {
    const fake = await createApi();
    vi.mocked(fake.budgetMaintenanceApi.runBankSync).mockRejectedValueOnce(
      new BudgetMaintenanceBusyError(30),
    );
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers: testHeaders(),
    });

    expect(response.statusCode).toBe(429);
    expect(response.headers["retry-after"]).toBe("30");
    expect(response.json().error.code).toBe("bank_sync_busy");
    expect(fake.budgetMaintenanceApi.runBankSync).toHaveBeenCalledTimes(1);
    await fake.app.close();
  });

  it("creates accounts and renames categories only inside the authorized budget", async () => {
    const fake = await createApi();
    const account = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: testHeaders(),
      payload: {
        name: "  Savings  ",
        offbudget: false,
        accountGroupId: "cash-group",
      },
    });
    const renamedAccount = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/accounts/account-1`,
      headers: testHeaders(),
      payload: { name: "Daily checking" },
    });
    const reopenedAccount = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts/account-1/reopen`,
      headers: testHeaders(),
    });
    const category = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { name: "Home care" },
    });
    const createdCategory = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/categories`,
      headers: testHeaders(),
      payload: { name: "Dining out", categoryGroupId: "group-1" },
    });
    const createdPayee = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/payees`,
      headers: testHeaders(),
      payload: { name: " Corner Market " },
    });
    const budgetAmount = await fake.app.inject({
      method: "PUT",
      url: `/api/v1/budgets/${budgetId}/categories/category-1/budget-amount`,
      headers: testHeaders(),
      payload: { month: "2026-11", amountMinor: 125_000 },
    });

    expect(account.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.createAccount).toHaveBeenCalledWith(
      budgetId,
      "Savings",
      false,
      "cash-group",
      undefined,
    );
    expect(renamedAccount.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.renameAccount).toHaveBeenCalledWith(
      budgetId,
      "account-1",
      "Daily checking",
      undefined,
    );
    expect(reopenedAccount.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.reopenAccount).toHaveBeenCalledWith(
      budgetId,
      "account-1",
      undefined,
    );
    expect(category.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.renameCategory).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      "Home care",
      undefined,
    );
    expect(createdCategory.statusCode).toBe(200);
    expect(createdCategory.json()).toEqual({
      status: "ok",
      categoryId: "category-created",
    });
    expect(createdPayee.statusCode).toBe(200);
    expect(createdPayee.json()).toEqual({
      status: "ok",
      payeeId: "payee-created",
    });
    expect(fake.budgetMaintenanceApi.createPayee).toHaveBeenCalledWith(
      budgetId,
      "Corner Market",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.createCategory).toHaveBeenCalledWith(
      budgetId,
      "Dining out",
      "group-1",
      undefined,
    );
    expect(budgetAmount.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.setBudgetAmount).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      "2026-11",
      125_000,
      undefined,
    );
    await fake.app.close();
  });

  it("renames and reopens accounts and creates payees and categories", async () => {
    const fake = await createApi();
    const responses = await Promise.all([
      fake.app.inject({
        method: "PATCH",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1`,
        headers: testHeaders(),
        payload: { name: "  Daily checking  " },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts/account-2/reopen`,
        headers: testHeaders(),
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/payees`,
        headers: testHeaders(),
        payload: { name: "  Corner Market  " },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/categories`,
        headers: testHeaders(),
        payload: { name: "  Dining out  ", categoryGroupId: "group-1" },
      }),
      fake.app.inject({
        method: "PUT",
        url: `/api/v1/budgets/${budgetId}/categories/category-1/budget-amount`,
        headers: testHeaders(),
        payload: { month: "2026-11", amountMinor: 1200 },
      }),
    ]);

    expect(responses.map((response) => response.statusCode)).toEqual([
      200, 200, 200, 200, 200,
    ]);
    expect(responses[2]?.json()).toEqual({
      status: "ok",
      payeeId: "payee-created",
    });
    expect(responses[3]?.json()).toEqual({
      status: "ok",
      categoryId: "category-created",
    });
    expect(fake.budgetMaintenanceApi.renameAccount).toHaveBeenCalledWith(
      budgetId,
      "account-1",
      "Daily checking",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.reopenAccount).toHaveBeenCalledWith(
      budgetId,
      "account-2",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.createPayee).toHaveBeenCalledWith(
      budgetId,
      "Corner Market",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.createCategory).toHaveBeenCalledWith(
      budgetId,
      "Dining out",
      "group-1",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.setBudgetAmount).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      "2026-11",
      1200,
      undefined,
    );
    await fake.app.close();
  });

  it("maps invalid maintenance results and sanitizes Actual failures", async () => {
    const fake = await createApi();
    const routes = [
      {
        method: "PATCH" as const,
        url: `/api/v1/budgets/${budgetId}/accounts/account-1`,
        payload: { name: "Daily checking" },
        action: vi.mocked(fake.budgetMaintenanceApi.renameAccount),
      },
      {
        method: "POST" as const,
        url: `/api/v1/budgets/${budgetId}/accounts/account-1/reopen`,
        action: vi.mocked(fake.budgetMaintenanceApi.reopenAccount),
      },
      {
        method: "POST" as const,
        url: `/api/v1/budgets/${budgetId}/payees`,
        payload: { name: "Corner Market" },
        action: vi.mocked(fake.budgetMaintenanceApi.createPayee),
      },
      {
        method: "POST" as const,
        url: `/api/v1/budgets/${budgetId}/categories`,
        payload: { name: "Dining", categoryGroupId: "group-1" },
        action: vi.mocked(fake.budgetMaintenanceApi.createCategory),
      },
      {
        method: "PUT" as const,
        url: `/api/v1/budgets/${budgetId}/categories/category-1/budget-amount`,
        payload: { month: "2026-11", amountMinor: 1200 },
        action: vi.mocked(fake.budgetMaintenanceApi.setBudgetAmount),
      },
      {
        method: "PUT" as const,
        url: `/api/v1/budgets/${budgetId}/categories/category-1/carryover`,
        payload: { month: "2026-11", enabled: true },
        action: vi.mocked(fake.budgetMaintenanceApi.setBudgetCarryover),
      },
      {
        method: "POST" as const,
        url: `/api/v1/budgets/${budgetId}/months/2026-11/actions`,
        payload: { action: "copy-last-month" },
        action: vi.mocked(fake.budgetMaintenanceApi.copyBudgetMonth),
      },
      {
        method: "POST" as const,
        url: `/api/v1/budgets/${budgetId}/categories/move-money`,
        payload: {
          month: "2026-11",
          fromCategoryId: "category-1",
          toCategoryId: "category-2",
          amountMinor: 1200,
        },
        action: vi.mocked(fake.budgetMaintenanceApi.moveMoney),
      },
    ];

    for (const route of routes) {
      route.action.mockRejectedValueOnce(
        new BudgetMaintenanceInputError("Invalid change"),
      );
      const rejected = await fake.app.inject({
        ...route,
        headers: testHeaders(),
      });
      expect(rejected.statusCode).toBe(400);
      expect(rejected.json().error.code).toBe("invalid_request");

      route.action.mockRejectedValueOnce(new Error("private Actual detail"));
      const failed = await fake.app.inject({
        ...route,
        headers: testHeaders(),
      });
      expect(failed.statusCode).toBe(502);
      expect(failed.body).not.toContain("private Actual detail");
    }
    await fake.app.close();
  });

  it("sanitizes Actual account creation failures", async () => {
    const fake = await createApi();
    vi.mocked(fake.budgetMaintenanceApi.createAccount).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: testHeaders(),
      payload: { name: "Savings", offbudget: false },
    });

    expect(response.statusCode).toBe(502);
    expect(response.body).not.toContain("private Actual detail");
    await fake.app.close();
  });

  it("hides out-of-scope account creation and reports Actual validation errors", async () => {
    const fake = await createApi();
    const outOfScope = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/other-budget/accounts",
      headers: testHeaders(),
      payload: { name: "Savings", offbudget: false },
    });
    vi.mocked(fake.budgetMaintenanceApi.createAccount).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Account name is already in use"),
    );
    const rejected = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: testHeaders(),
      payload: { name: "Savings", offbudget: false },
    });

    expect(outOfScope.statusCode).toBe(404);
    expect(rejected.statusCode).toBe(400);
    expect(rejected.json().error).toMatchObject({ code: "invalid_request" });
    await fake.app.close();
  });

  it("checks authorization, validation, and Actual results when closing accounts", async () => {
    const fake = await createApi();
    const closeUrl = `/api/v1/budgets/${budgetId}/accounts/account-1/close`;
    const payload = {
      transferAccountId: "account-2",
      transferCategoryId: null,
    };
    const unauthorized = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      payload,
    });
    const outOfScope = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/other-budget/accounts/account-1/close",
      headers: testHeaders(),
      payload,
    });
    const invalid = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: testHeaders(),
      payload: { transferAccountId: "account-2" },
    });
    const success = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: { ...testHeaders(), "x-budget-encryption-password": "secret" },
      payload,
    });
    vi.mocked(fake.budgetMaintenanceApi.closeAccount).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Choose a different open account"),
    );
    const rejected = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: testHeaders(),
      payload,
    });
    vi.mocked(fake.budgetMaintenanceApi.closeAccount).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: testHeaders(),
      payload,
    });

    expect(unauthorized.statusCode).toBe(401);
    expect(outOfScope.statusCode).toBe(404);
    expect(invalid.statusCode).toBe(400);
    expect(success.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.closeAccount).toHaveBeenNthCalledWith(
      1,
      budgetId,
      "account-1",
      "account-2",
      null,
      "secret",
    );
    expect(rejected.statusCode).toBe(400);
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual detail");
    await fake.app.close();
  });

  it("requires an explicit reassignment choice when deleting a category", async () => {
    const fake = await createApi();
    const missingChoice = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
    });
    const unassigned = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { transferCategoryId: null },
    });

    expect(missingChoice.statusCode).toBe(400);
    expect(unassigned.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.deleteCategory).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      null,
      undefined,
    );
    await fake.app.close();
  });

  it("returns a safe validation error for invalid category deletion", async () => {
    const fake = await createApi();
    vi.mocked(fake.budgetMaintenanceApi.deleteCategory).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Choose a different category"),
    );
    const response = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { transferCategoryId: "category-1" },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatchObject({ code: "invalid_request" });
    await fake.app.close();
  });

  it("hides out-of-scope category deletion and sanitizes Actual failures", async () => {
    const fake = await createApi();
    const outOfScope = await fake.app.inject({
      method: "DELETE",
      url: "/api/v1/budgets/other-budget/categories/category-1",
      headers: testHeaders(),
      payload: { transferCategoryId: null },
    });
    vi.mocked(fake.budgetMaintenanceApi.deleteCategory).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { transferCategoryId: null },
    });

    expect(outOfScope.statusCode).toBe(404);
    expect(fake.budgetMaintenanceApi.deleteCategory).toHaveBeenCalledOnce();
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual detail");
    await fake.app.close();
  });

  it("hides out-of-scope category renames and handles Actual errors safely", async () => {
    const fake = await createApi();
    const outOfScope = await fake.app.inject({
      method: "PATCH",
      url: "/api/v1/budgets/other-budget/categories/category-1",
      headers: testHeaders(),
      payload: { name: "Home care" },
    });
    vi.mocked(fake.budgetMaintenanceApi.renameCategory).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Category does not exist in this budget"),
    );
    const invalid = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/missing`,
      headers: testHeaders(),
      payload: { name: "Home care" },
    });
    vi.mocked(fake.budgetMaintenanceApi.renameCategory).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: testHeaders(),
      payload: { name: "Home care" },
    });

    expect(outOfScope.statusCode).toBe(404);
    expect(invalid.statusCode).toBe(400);
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual detail");
    await fake.app.close();
  });

  it("journals schedule edits and actions with budget scope and stable retries", async () => {
    const fake = await createApi();
    const path = `/api/v1/budgets/${budgetId}/schedules/schedule-1/actions`;
    const renameHeaders = {
      ...testHeaders(),
      "idempotency-key": "schedule-rename-1",
    };
    const rename = () =>
      fake.app.inject({
        method: "POST",
        url: path,
        headers: renameHeaders,
        payload: { action: "rename", name: "Home rent" },
      });

    const firstRename = await rename();
    const replayedRename = await rename();
    const editHeaders = {
      ...testHeaders(),
      "idempotency-key": "schedule-edit-1",
    };
    const editBody = {
      action: "edit",
      name: "House rent",
      accountId: "checking-1",
      payeeId: "landlord-1",
      amountMinor: -180_000,
      amountOp: "is",
      date: {
        frequency: "monthly",
        interval: 1,
        start: "2026-12-01",
        endMode: "never",
        skipWeekend: false,
      },
    };
    const edit = () =>
      fake.app.inject({
        method: "POST",
        url: path,
        headers: editHeaders,
        payload: editBody,
      });
    const firstEdit = await edit();
    const replayedEdit = await edit();
    const skip = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "skip" },
    });
    const post = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "post" },
    });
    const deleted = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "delete" },
    });
    const outOfScope = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/other-budget/schedules/schedule-1/actions",
      headers: testHeaders(),
      payload: { action: "skip" },
    });
    const missingName = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "rename" },
    });
    const extraName = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "skip", name: "Should not be accepted" },
    });

    expect([
      firstRename.statusCode,
      replayedRename.statusCode,
      firstEdit.statusCode,
      replayedEdit.statusCode,
      skip.statusCode,
      post.statusCode,
      deleted.statusCode,
    ]).toEqual([200, 200, 200, 200, 200, 200, 200]);
    expect(outOfScope.statusCode).toBe(404);
    expect(missingName.statusCode).toBe(400);
    expect(extraName.statusCode).toBe(400);
    expect(fake.budgetMaintenanceApi.renameSchedule).toHaveBeenCalledOnce();
    expect(fake.budgetMaintenanceApi.renameSchedule).toHaveBeenCalledWith(
      budgetId,
      "schedule-1",
      "Home rent",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.editSchedule).toHaveBeenCalledOnce();
    expect(fake.budgetMaintenanceApi.editSchedule).toHaveBeenCalledWith(
      budgetId,
      "schedule-1",
      {
        name: "House rent",
        accountId: "checking-1",
        payeeId: "landlord-1",
        amountMinor: -180_000,
        amountOp: "is",
        date: editBody.date,
      },
      undefined,
    );
    expect(fake.budgetMaintenanceApi.skipSchedule).toHaveBeenCalledOnce();
    expect(fake.budgetMaintenanceApi.postSchedule).toHaveBeenCalledOnce();
    expect(fake.budgetMaintenanceApi.deleteSchedule).toHaveBeenCalledOnce();
    expect([...fake.operations.values()].map(({ action }) => action)).toEqual([
      "schedule-rename",
      "schedule-edit",
      "schedule-skip",
      "schedule-post",
      "schedule-delete",
    ]);
    await fake.app.close();
  });

  it("journals payee rename, merge, and delete actions", async () => {
    const fake = await createApi();
    const path = `/api/v1/budgets/${budgetId}/payees/payee-1/actions`;
    const renameHeaders = {
      ...testHeaders(),
      "idempotency-key": "payee-rename-1",
    };
    const rename = () =>
      fake.app.inject({
        method: "POST",
        url: path,
        headers: renameHeaders,
        payload: { action: "rename", name: "Home store" },
      });
    const firstRename = await rename();
    const replayedRename = await rename();
    const merged = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "merge", targetPayeeId: "payee-2" },
    });
    const deleted = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "delete" },
    });
    const missingName = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "rename" },
    });
    const missingTarget = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "merge" },
    });
    const extraName = await fake.app.inject({
      method: "POST",
      url: path,
      headers: testHeaders(),
      payload: { action: "delete", name: "Not allowed" },
    });
    const outOfScope = await fake.app.inject({
      method: "POST",
      url: "/api/v1/budgets/other-budget/payees/payee-1/actions",
      headers: testHeaders(),
      payload: { action: "delete" },
    });

    expect([
      firstRename.statusCode,
      replayedRename.statusCode,
      merged.statusCode,
      deleted.statusCode,
    ]).toEqual([200, 200, 200, 200]);
    expect(missingName.statusCode).toBe(400);
    expect(missingTarget.statusCode).toBe(400);
    expect(extraName.statusCode).toBe(400);
    expect(outOfScope.statusCode).toBe(404);
    expect(fake.budgetMaintenanceApi.renamePayee).toHaveBeenCalledOnce();
    expect(fake.budgetMaintenanceApi.renamePayee).toHaveBeenCalledWith(
      budgetId,
      "payee-1",
      "Home store",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.mergePayee).toHaveBeenCalledWith(
      budgetId,
      "payee-1",
      "payee-2",
      undefined,
    );
    expect(fake.budgetMaintenanceApi.deletePayee).toHaveBeenCalledOnce();
    expect([...fake.operations.values()].map(({ action }) => action)).toEqual([
      "payee-rename",
      "payee-merge",
      "payee-delete",
    ]);
    await fake.app.close();
  });

  it("sanitizes schedule validation and Actual failures", async () => {
    const fake = await createApi();
    const url = `/api/v1/budgets/${budgetId}/schedules/schedule-1/actions`;
    vi.mocked(fake.budgetMaintenanceApi.skipSchedule).mockRejectedValueOnce(
      new BudgetMaintenanceInputError(
        "A one-time schedule has no next date to skip",
      ),
    );
    const rejected = await fake.app.inject({
      method: "POST",
      url,
      headers: testHeaders(),
      payload: { action: "skip" },
    });
    vi.mocked(fake.budgetMaintenanceApi.postSchedule).mockRejectedValueOnce(
      new Error("private Actual schedule details"),
    );
    const failed = await fake.app.inject({
      method: "POST",
      url,
      headers: testHeaders(),
      payload: { action: "post" },
    });

    expect(rejected.statusCode).toBe(400);
    expect(rejected.json().error.code).toBe("invalid_request");
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual schedule details");
    await fake.app.close();
  });

  it("audits budget maintenance outcomes without logging passwords", async () => {
    const fake = await createApi();
    const auditLog = vi
      .spyOn(console, "info")
      .mockImplementation(() => undefined);
    vi.mocked(fake.budgetMaintenanceApi.closeAccount).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Choose a different open account"),
    );

    const requests = [
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/bank-sync`,
        headers: { ...testHeaders(), "x-budget-encryption-password": "secret" },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts`,
        headers: testHeaders(),
        payload: { name: "Savings", offbudget: false },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1/close`,
        headers: testHeaders(),
        payload: { transferAccountId: null, transferCategoryId: null },
      }),
      fake.app.inject({
        method: "PATCH",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: testHeaders(),
        payload: { name: "Home" },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/categories`,
        headers: testHeaders(),
        payload: { name: "Dining", categoryGroupId: "group-1" },
      }),
      fake.app.inject({
        method: "PUT",
        url: `/api/v1/budgets/${budgetId}/categories/category-1/budget-amount`,
        headers: testHeaders(),
        payload: { month: "2026-11", amountMinor: 900 },
      }),
      fake.app.inject({
        method: "DELETE",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: testHeaders(),
        payload: { transferCategoryId: null },
      }),
    ];
    const responses = await Promise.all(requests);
    const events = auditLog.mock.calls.map(
      ([line]) => JSON.parse(String(line)) as Record<string, unknown>,
    );

    expect(responses.map((response) => response.statusCode)).toEqual([
      200, 200, 400, 200, 200, 200, 200,
    ]);
    expect(events.map((event) => event.event).sort()).toEqual(
      [
        "budget.bank_sync",
        "budget.account_create",
        "budget.account_close",
        "budget.category_rename",
        "budget.category_create",
        "budget.category_budget",
        "budget.category_delete",
      ].sort(),
    );
    expect(events).toContainEqual(
      expect.objectContaining({
        event: "budget.account_close",
        outcome: "rejected",
      }),
    );
    expect(events.filter((event) => event.outcome === "success")).toHaveLength(
      6,
    );
    expect(
      events.every(
        (event) =>
          event.deviceId === device.deviceId && event.budgetId === budgetId,
      ),
    ).toBe(true);
    expect(JSON.stringify(events)).not.toContain("secret");
    auditLog.mockRestore();
    await fake.app.close();
  });
});

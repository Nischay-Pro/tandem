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

async function createApi(maintenanceConfigured = true) {
  const budgetMaintenanceApi: BudgetMaintenanceApiPort = {
    runBankSync: vi.fn(async () => undefined),
    createAccount: vi.fn(async () => undefined),
    createCategory: vi.fn(async () => "category-created"),
    createPayee: vi.fn(async () => "payee-created"),
    setBudgetAmount: vi.fn(async () => undefined),
    moveMoney: vi.fn(async () => undefined),
    closeAccount: vi.fn(async () => undefined),
    renameAccount: vi.fn(async () => undefined),
    reopenAccount: vi.fn(async () => undefined),
    renameCategory: vi.fn(async () => undefined),
    deleteCategory: vi.fn(async () => undefined),
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
    app: await buildApiApp(
      { ping, close },
      {
        deviceAuth,
        ...(maintenanceConfigured ? { budgetMaintenanceApi } : {}),
        maintenanceJournal: journal,
      },
    ),
  };
}

const testHeaders = () => ({
  authorization: `Bearer ${accessToken}`,
  "idempotency-key": randomUUID(),
});

describe("budget maintenance routes", () => {
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

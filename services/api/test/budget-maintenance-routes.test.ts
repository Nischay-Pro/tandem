import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import type { BudgetMaintenanceApiPort } from "../src/actual/budget-maintenance-api.js";
import { BudgetMaintenanceInputError } from "../src/actual/budget-maintenance-api.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";

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
    closeAccount: vi.fn(async () => undefined),
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
  return {
    budgetMaintenanceApi,
    authenticate,
    app: await buildApiApp(
      { ping, close },
      {
        deviceAuth,
        ...(maintenanceConfigured ? { budgetMaintenanceApi } : {}),
      },
    ),
  };
}

const authHeaders = { authorization: `Bearer ${accessToken}` };

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
      headers: authHeaders,
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
      headers: authHeaders,
    });
    const invalidAccount = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: authHeaders,
      payload: { name: "Savings" },
    });
    const invalidCategory = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: authHeaders,
      payload: { name: "" },
    });

    expect(invalidBudget.statusCode).toBe(400);
    expect(invalidAccount.statusCode).toBe(400);
    expect(invalidCategory.statusCode).toBe(400);
    await fake.app.close();
  });

  it("reports missing Actual integration for each maintenance operation", async () => {
    const fake = await createApi(false);
    const responses = await Promise.all([
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/bank-sync`,
        headers: authHeaders,
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts`,
        headers: authHeaders,
        payload: { name: "Savings", offbudget: false },
      }),
      fake.app.inject({
        method: "POST",
        url: `/api/v1/budgets/${budgetId}/accounts/account-1/close`,
        headers: authHeaders,
        payload: { transferAccountId: null, transferCategoryId: null },
      }),
      fake.app.inject({
        method: "PATCH",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: authHeaders,
        payload: { name: "Home" },
      }),
      fake.app.inject({
        method: "DELETE",
        url: `/api/v1/budgets/${budgetId}/categories/category-1`,
        headers: authHeaders,
        payload: { transferCategoryId: null },
      }),
    ]);

    expect(responses.map((response) => response.statusCode)).toEqual([
      503, 503, 503, 503, 503,
    ]);
    expect(responses.map((response) => response.json().error.code)).toEqual(
      Array(5).fill("actual_not_configured"),
    );
    await fake.app.close();
  });

  it("runs the configured Actual bank sync with an encrypted-budget password", async () => {
    const fake = await createApi();
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers: { ...authHeaders, "x-budget-encryption-password": "secret" },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
    expect(fake.budgetMaintenanceApi.runBankSync).toHaveBeenCalledWith(
      budgetId,
      "secret",
    );
    await fake.app.close();
  });

  it("returns a safe error when Actual bank sync fails", async () => {
    const fake = await createApi();
    vi.mocked(fake.budgetMaintenanceApi.runBankSync).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const response = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/bank-sync`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(502);
    expect(response.json().error).toMatchObject({ code: "bank_sync_failed" });
    expect(response.body).not.toContain("private Actual detail");
    await fake.app.close();
  });

  it("creates accounts and renames categories only inside the authorized budget", async () => {
    const fake = await createApi();
    const account = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: authHeaders,
      payload: { name: "  Savings  ", offbudget: false },
    });
    const category = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: authHeaders,
      payload: { name: "Home care" },
    });

    expect(account.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.createAccount).toHaveBeenCalledWith(
      budgetId,
      "Savings",
      false,
      undefined,
    );
    expect(category.statusCode).toBe(200);
    expect(fake.budgetMaintenanceApi.renameCategory).toHaveBeenCalledWith(
      budgetId,
      "category-1",
      "Home care",
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
      headers: authHeaders,
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
      headers: authHeaders,
      payload: { name: "Savings", offbudget: false },
    });
    vi.mocked(fake.budgetMaintenanceApi.createAccount).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Account name is already in use"),
    );
    const rejected = await fake.app.inject({
      method: "POST",
      url: `/api/v1/budgets/${budgetId}/accounts`,
      headers: authHeaders,
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
      headers: authHeaders,
      payload,
    });
    const invalid = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: authHeaders,
      payload: { transferAccountId: "account-2" },
    });
    const success = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: { ...authHeaders, "x-budget-encryption-password": "secret" },
      payload,
    });
    vi.mocked(fake.budgetMaintenanceApi.closeAccount).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Choose a different open account"),
    );
    const rejected = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: authHeaders,
      payload,
    });
    vi.mocked(fake.budgetMaintenanceApi.closeAccount).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "POST",
      url: closeUrl,
      headers: authHeaders,
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
      headers: authHeaders,
    });
    const unassigned = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: authHeaders,
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
      headers: authHeaders,
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
      headers: authHeaders,
      payload: { transferCategoryId: null },
    });
    vi.mocked(fake.budgetMaintenanceApi.deleteCategory).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "DELETE",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: authHeaders,
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
      headers: authHeaders,
      payload: { name: "Home care" },
    });
    vi.mocked(fake.budgetMaintenanceApi.renameCategory).mockRejectedValueOnce(
      new BudgetMaintenanceInputError("Category does not exist in this budget"),
    );
    const invalid = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/missing`,
      headers: authHeaders,
      payload: { name: "Home care" },
    });
    vi.mocked(fake.budgetMaintenanceApi.renameCategory).mockRejectedValueOnce(
      new Error("private Actual detail"),
    );
    const failed = await fake.app.inject({
      method: "PATCH",
      url: `/api/v1/budgets/${budgetId}/categories/category-1`,
      headers: authHeaders,
      payload: { name: "Home care" },
    });

    expect(outOfScope.statusCode).toBe(404);
    expect(invalid.statusCode).toBe(400);
    expect(failed.statusCode).toBe(502);
    expect(failed.body).not.toContain("private Actual detail");
    await fake.app.close();
  });
});

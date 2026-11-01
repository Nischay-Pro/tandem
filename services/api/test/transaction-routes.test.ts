import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import type { TransactionApiPort } from "../src/actual/transaction-api.js";
import type { TransactionSnapshot } from "../src/actual/transaction-reconciliation.js";
import { TransactionRequestValidationError } from "../src/actual/transaction-reconciler.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";
import {
  IdempotencyKeyReuseError,
  OperationIdReuseError,
  type JsonValue,
  type OperationConflict,
  type WriteOperation,
} from "../src/write-journal/contracts.js";

const budgetId = "synthetic-budget";
const transactionId = "transaction-1";
const accessToken = "A".repeat(43);
const operationId = "abf3ca24-35b1-4f10-8433-311e9e8197c2";
const device: DeviceRecord = {
  deviceId: "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10",
  principalId: "owner",
  name: "Synthetic phone",
  budgetIds: [budgetId],
  createdAt: new Date("2026-11-01T00:00:00.000Z"),
  lastSeenAt: null,
  revokedAt: null,
};
const snapshot: TransactionSnapshot = {
  id: transactionId,
  account: "checking-account",
  amount: -1500,
  date: "2026-11-01",
  payee: null,
  category: null,
  notes: null,
  cleared: null,
  reconciled: null,
  importedId: null,
  importedPayee: null,
  startingBalance: null,
  schedule: null,
  transferId: null,
  isParent: false,
  isChild: false,
  parentId: null,
  subtransactions: [],
};

function createOperation(
  overrides: Partial<WriteOperation> = {},
): WriteOperation {
  const now = new Date("2026-11-01T00:00:00.000Z");
  return {
    operationId,
    principalId: "owner",
    budgetId,
    idempotencyKey: "request-1",
    requestHash: "request-hash",
    entityType: "transaction",
    action: "update",
    entityId: transactionId,
    baseRevision: "revision-1",
    baseSnapshot: snapshot as unknown as JsonValue,
    patch: { amount: -1200 },
    status: "committed",
    attemptCount: 1,
    errorCode: null,
    actualResult: { transactionId },
    writeEvidence: null,
    createdAt: now,
    updatedAt: now,
    startedAt: now,
    leaseExpiresAt: null,
    completedAt: now,
    ...overrides,
  };
}

function createConflict(): OperationConflict {
  return {
    conflictId: randomUUID(),
    operationId,
    conflictType: "same-field-edit",
    baseSnapshot: { id: transactionId, amount: -1500 },
    localPatch: { amount: -1200 },
    remoteSnapshot: null,
    conflictingFields: ["amount"],
    createdAt: new Date("2026-11-01T00:00:01.000Z"),
  };
}

function createApi(
  overrides: {
    readTransaction?: TransactionApiPort["readTransaction"];
    submit?: TransactionApiPort["submit"];
    getOperation?: TransactionApiPort["getOperation"];
    authenticatedDevice?: DeviceRecord | undefined;
  } = {},
) {
  const readTransaction = vi.fn(
    overrides.readTransaction ??
      (async () => ({ snapshot, revision: "f".repeat(64) })),
  );
  const submit = vi.fn(
    overrides.submit ??
      (async () => ({ operation: createOperation(), conflict: null })),
  );
  const getOperation = vi.fn(
    overrides.getOperation ??
      (async () => ({ operation: createOperation(), conflict: null })),
  );
  const transactionApi: TransactionApiPort = {
    readTransaction,
    submit,
    getOperation,
  };
  const authenticate = vi.fn(async (token?: string) =>
    token === accessToken
      ? (overrides.authenticatedDevice ?? device)
      : undefined,
  );
  const deviceAuth = {
    authenticate,
    createPairingCode: vi.fn(),
    pair: vi.fn(),
    listDevicesForDevice: vi.fn(),
    revokeDeviceForDevice: vi.fn(),
  } as unknown as DeviceAuthorization;
  const ping = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  const close = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  return {
    transactionApi,
    readTransaction,
    submit,
    getOperation,
    authenticate,
    buildApp: (withTransactionApi = true) =>
      buildApiApp(
        { ping, close },
        {
          deviceAuth,
          ...(withTransactionApi ? { transactionApi } : {}),
        },
      ),
  };
}

const authHeaders = { authorization: `Bearer ${accessToken}` };
const transactionUrl = `/api/v1/budgets/${budgetId}/transactions/${transactionId}`;
const requestBody = {
  operationId,
  action: "update" as const,
  baseRevision: "revision-1",
  baseSnapshot: snapshot as unknown as JsonValue,
  patch: { amount: -1200 },
};

describe("transaction REST routes", () => {
  it("documents transaction read, operation submission, and status routes", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const document = await app.inject({
      method: "GET",
      url: "/api/v1/openapi.json",
    });
    expect(document.json().paths).toMatchObject({
      "/api/v1/budgets/{budgetId}/transactions/{transactionId}": {
        get: expect.any(Object),
      },
      "/api/v1/budgets/{budgetId}/transactions/{transactionId}/operations": {
        post: expect.any(Object),
      },
      "/api/v1/operations/{operationId}": { get: expect.any(Object) },
    });
    await app.close();
  });

  it("reads a scoped transaction and returns its revision without caching", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: transactionUrl,
      headers: {
        ...authHeaders,
        "x-budget-encryption-password": "budget-secret",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({
      transaction: snapshot,
      revision: "f".repeat(64),
    });
    expect(fake.readTransaction).toHaveBeenCalledWith(
      budgetId,
      transactionId,
      "budget-secret",
    );
    await app.close();
  });

  it("returns 401 when transaction routes receive no valid device", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({ method: "GET", url: transactionUrl });
    const writeResponse = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: { "idempotency-key": "request-1" },
      payload: requestBody,
    });

    expect(response.statusCode).toBe(401);
    expect(writeResponse.statusCode).toBe(401);
    expect(fake.readTransaction).not.toHaveBeenCalled();
    expect(fake.submit).not.toHaveBeenCalled();
    await app.close();
  });

  it("hides budgets outside the device scope", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: `/api/v1/budgets/other-budget/transactions/${transactionId}`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(404);
    expect(fake.readTransaction).not.toHaveBeenCalled();
    await app.close();
  });

  it("returns 503 when Actual integration is not configured", async () => {
    const fake = createApi();
    const app = await fake.buildApp(false);
    const response = await app.inject({
      method: "GET",
      url: transactionUrl,
      headers: authHeaders,
    });
    const writeResponse = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: { ...authHeaders, "idempotency-key": "request-1" },
      payload: requestBody,
    });

    expect(response.statusCode).toBe(503);
    expect(response.json().error.code).toBe("actual_not_configured");
    expect(writeResponse.statusCode).toBe(503);
    await app.close();
  });

  it("returns 400 for malformed transaction paths or encryption headers", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const malformedPath = await app.inject({
      method: "GET",
      url: "/api/v1/budgets/%20/transactions/transaction-1",
      headers: authHeaders,
    });
    const malformedHeader = await app.inject({
      method: "GET",
      url: transactionUrl,
      headers: {
        ...authHeaders,
        "x-budget-encryption-password": "s".repeat(1_025),
      },
    });

    expect(malformedPath.statusCode).toBe(400);
    expect(malformedHeader.statusCode).toBe(400);
    expect(fake.readTransaction).not.toHaveBeenCalled();
    await app.close();
  });

  it("returns 404 for transactions absent from Actual", async () => {
    const fake = createApi({
      readTransaction: async () => undefined,
    });
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: transactionUrl,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(404);
    await app.close();
  });

  it("sanitizes Actual read failures even if logging throws", async () => {
    const fake = createApi({
      readTransaction: async () => {
        throw new Error("private Actual server detail");
      },
    });
    const app = await fake.buildApp();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {
      throw new Error("synthetic logger error");
    });
    const response = await app.inject({
      method: "GET",
      url: transactionUrl,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(502);
    expect(response.body).not.toContain("private Actual server detail");
    expect(consoleError).toHaveBeenCalledOnce();
    consoleError.mockRestore();
    await app.close();
  });

  it("submits an operation with server-derived identity and transient encryption secret", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: {
        ...authHeaders,
        "idempotency-key": "request-1",
        "x-budget-encryption-password": "budget-secret",
      },
      payload: requestBody,
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(fake.submit).toHaveBeenCalledWith({
      ...requestBody,
      principalId: "owner",
      budgetId,
      idempotencyKey: "request-1",
      transactionId,
      encryptionPassword: "budget-secret",
    });
    expect(response.json().operation).toMatchObject({
      operationId,
      status: "committed",
      transactionId,
      completedAt: "2026-11-01T00:00:00.000Z",
    });
    await app.close();
  });

  it("submits operations without storing or forwarding an omitted password", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: { ...authHeaders, "idempotency-key": "request-1" },
      payload: requestBody,
    });

    expect(response.statusCode).toBe(200);
    expect(fake.submit).toHaveBeenCalledWith({
      ...requestBody,
      principalId: "owner",
      budgetId,
      idempotencyKey: "request-1",
      transactionId,
    });
    await app.close();
  });

  it("returns 400 for invalid bodies and missing idempotency keys", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const invalidBody = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: { ...authHeaders, "idempotency-key": "request-1" },
      payload: { ...requestBody, action: "create" },
    });
    const missingKey = await app.inject({
      method: "POST",
      url: `${transactionUrl}/operations`,
      headers: authHeaders,
      payload: requestBody,
    });

    expect(invalidBody.statusCode).toBe(400);
    expect(missingKey.statusCode).toBe(400);
    expect(fake.submit).not.toHaveBeenCalled();
    await app.close();
  });

  it("returns 404 without submitting writes for a budget outside the device scope", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "POST",
      url: `/api/v1/budgets/other-budget/transactions/${transactionId}/operations`,
      headers: { ...authHeaders, "idempotency-key": "request-1" },
      payload: requestBody,
    });

    expect(response.statusCode).toBe(404);
    expect(fake.submit).not.toHaveBeenCalled();
    await app.close();
  });

  it.each([
    [new TransactionRequestValidationError("bad base revision"), 400],
    [new IdempotencyKeyReuseError(), 409],
    [new OperationIdReuseError(), 409],
    [new Error("secret Actual failure"), 502],
    ["non-error Actual failure", 502],
  ])(
    "maps transaction submission errors to safe HTTP responses",
    async (error, statusCode) => {
      const fake = createApi({
        submit: async () => {
          throw error;
        },
      });
      const app = await fake.buildApp();
      const response = await app.inject({
        method: "POST",
        url: `${transactionUrl}/operations`,
        headers: { ...authHeaders, "idempotency-key": "request-1" },
        payload: requestBody,
      });

      expect(response.statusCode).toBe(statusCode);
      expect(response.body).not.toContain("secret Actual failure");
      await app.close();
    },
  );

  it("reads an owned operation and its conflict without caching", async () => {
    const fake = createApi({
      getOperation: async () => ({
        operation: createOperation({ status: "conflict" }),
        conflict: createConflict(),
      }),
    });
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json().conflict.conflictingFields).toEqual(["amount"]);
    expect(fake.getOperation).toHaveBeenCalledWith("owner", operationId);
    await app.close();
  });

  it("serializes queued operations with null optional fields", async () => {
    const fake = createApi({
      getOperation: async () => ({
        operation: createOperation({
          status: "queued",
          entityId: null,
          baseRevision: null,
          startedAt: null,
          completedAt: null,
        }),
        conflict: null,
      }),
    });
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().operation).toMatchObject({
      transactionId: "",
      baseRevision: "",
      startedAt: null,
      completedAt: null,
    });
    await app.close();
  });

  it("hides missing, foreign, and out-of-scope operations", async () => {
    const fake = createApi({ getOperation: async () => undefined });
    const app = await fake.buildApp();
    const missing = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });
    const foreign = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: { authorization: `Bearer ${"B".repeat(43)}` },
    });

    expect(missing.statusCode).toBe(404);
    expect(foreign.statusCode).toBe(401);
    await app.close();
  });

  it("returns 404 when an operation belongs to a budget outside device scope", async () => {
    const fake = createApi({
      getOperation: async () => ({
        operation: createOperation({ budgetId: "other-budget" }),
        conflict: null,
      }),
    });
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(404);
    await app.close();
  });

  it("returns 400 for an invalid operation ID and 503 when Actual is disabled", async () => {
    const fake = createApi();
    const app = await fake.buildApp();
    const invalidId = await app.inject({
      method: "GET",
      url: "/api/v1/operations/not-a-uuid",
      headers: authHeaders,
    });
    await app.close();

    const disabled = await fake.buildApp(false);
    const unavailable = await disabled.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });

    expect(invalidId.statusCode).toBe(400);
    expect(unavailable.statusCode).toBe(503);
    await disabled.close();
  });

  it("sanitizes operation lookup failures", async () => {
    const fake = createApi({
      getOperation: async () => {
        throw new Error("private PostgreSQL failure");
      },
    });
    const app = await fake.buildApp();
    const response = await app.inject({
      method: "GET",
      url: `/api/v1/operations/${operationId}`,
      headers: authHeaders,
    });

    expect(response.statusCode).toBe(502);
    expect(response.body).not.toContain("private PostgreSQL failure");
    await app.close();
  });
});

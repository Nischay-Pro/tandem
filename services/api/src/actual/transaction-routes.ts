import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { TransactionApiPort } from "./transaction-api.js";
import { TransactionRequestValidationError } from "./transaction-reconciler.js";
import { DeviceAuthorization, type DeviceRecord } from "../auth/device-auth.js";
import {
  IDEMPOTENCY_KEY_MAX_LENGTH,
  IdempotencyKeyReuseError,
  OperationIdReuseError,
  type JsonValue,
  type OperationConflict,
  type WriteOperation,
} from "../write-journal/contracts.js";
import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";

const errorSchema = API_ERROR_SCHEMA_REF;

const budgetEncryptionHeaderSchema = {
  type: "object",
  properties: {
    "x-budget-encryption-password": {
      type: "string",
      minLength: 1,
      maxLength: 1_024,
    },
  },
  additionalProperties: true,
};

export interface TransactionRouteOptions {
  deviceAuth: DeviceAuthorization;
  transactionApi?: TransactionApiPort;
  requireDevice: (
    deviceAuth: DeviceAuthorization,
    request: FastifyRequest,
    reply: FastifyReply,
  ) => Promise<DeviceRecord | undefined>;
  invalidTransactionRequest: (
    reply: FastifyReply,
    message?: string,
    code?: string,
  ) => FastifyReply;
  transactionNotFound: (reply: FastifyReply) => FastifyReply;
  actualNotConfigured: (reply: FastifyReply) => FastifyReply;
  reportTransactionFailure: (context: string, error?: unknown) => void;
  actualRequestFailed: (reply: FastifyReply, error?: unknown) => FastifyReply;
}

interface TransactionRouteParams {
  budgetId: string;
  transactionId: string;
}

interface OperationRouteParams {
  operationId: string;
}

interface OperationHeaders {
  "x-budget-encryption-password"?: string;
}

interface SubmitTransactionBody {
  operationId: string;
  action: "create" | "update" | "delete";
  baseRevision?: string;
  baseSnapshot?: JsonValue;
  patch: { [key: string]: JsonValue };
}

interface TransactionHeaders {
  "idempotency-key": string;
  "x-budget-encryption-password"?: string;
}

export function registerTransactionRoutes(
  app: FastifyInstance,
  options: TransactionRouteOptions,
): void {
  const {
    deviceAuth,
    transactionApi,
    requireDevice,
    invalidTransactionRequest,
    transactionNotFound,
    actualNotConfigured,
    reportTransactionFailure,
    actualRequestFailed,
  } = options;
  const nullableStringSchema = { type: ["string", "null"] };
  const nullableBooleanSchema = { type: ["boolean", "null"] };
  const transactionSnapshotSchema = {
    type: "object",
    properties: {
      id: { type: "string" },
      account: { type: "string" },
      amount: { type: "integer" },
      date: { type: "string", format: "date" },
      payee: nullableStringSchema,
      category: nullableStringSchema,
      notes: nullableStringSchema,
      cleared: nullableBooleanSchema,
      reconciled: nullableBooleanSchema,
      importedId: nullableStringSchema,
      importedPayee: nullableStringSchema,
      startingBalance: nullableBooleanSchema,
      schedule: nullableStringSchema,
      transferId: nullableStringSchema,
      isParent: { type: "boolean" },
      isChild: { type: "boolean" },
      parentId: nullableStringSchema,
      subtransactions: {
        type: "array",
        maxItems: 1_000,
        items: { type: "object", additionalProperties: true },
      },
    },
    required: [
      "id",
      "account",
      "amount",
      "date",
      "payee",
      "category",
      "notes",
      "cleared",
      "reconciled",
      "importedId",
      "importedPayee",
      "startingBalance",
      "schedule",
      "transferId",
      "isParent",
      "isChild",
      "parentId",
      "subtransactions",
    ],
    additionalProperties: false,
  };
  const conflictSchema = {
    type: ["object", "null"],
    properties: {
      conflictId: { type: "string", format: "uuid" },
      conflictType: { type: "string" },
      baseSnapshot: { type: "object", additionalProperties: true },
      localPatch: { type: "object", additionalProperties: true },
      remoteSnapshot: { anyOf: [transactionSnapshotSchema, { type: "null" }] },
      conflictingFields: { type: "array", items: { type: "string" } },
      createdAt: { type: "string", format: "date-time" },
    },
    required: [
      "conflictId",
      "conflictType",
      "baseSnapshot",
      "localPatch",
      "remoteSnapshot",
      "conflictingFields",
      "createdAt",
    ],
    additionalProperties: false,
  };
  const operationResultSchema = {
    type: "object",
    properties: {
      operation: {
        type: "object",
        properties: {
          operationId: { type: "string", format: "uuid" },
          budgetId: { type: "string" },
          action: { type: "string", enum: ["create", "update", "delete"] },
          transactionId: { type: "string" },
          baseRevision: { type: "string" },
          baseSnapshot: { type: "object", additionalProperties: true },
          patch: { type: "object", additionalProperties: true },
          status: {
            type: "string",
            enum: [
              "queued",
              "applying",
              "committed",
              "conflict",
              "uncertain",
              "rejected",
            ],
          },
          attemptCount: { type: "integer" },
          errorCode: nullableStringSchema,
          actualResult: {
            type: ["object", "array", "string", "number", "boolean", "null"],
            additionalProperties: true,
            items: {},
          },
          createdAt: { type: "string", format: "date-time" },
          updatedAt: { type: "string", format: "date-time" },
          startedAt: { type: ["string", "null"], format: "date-time" },
          completedAt: { type: ["string", "null"], format: "date-time" },
        },
        required: [
          "operationId",
          "budgetId",
          "action",
          "transactionId",
          "baseRevision",
          "baseSnapshot",
          "patch",
          "status",
          "attemptCount",
          "errorCode",
          "actualResult",
          "createdAt",
          "updatedAt",
          "startedAt",
          "completedAt",
        ],
        additionalProperties: false,
      },
      conflict: conflictSchema,
    },
    required: ["operation", "conflict"],
    additionalProperties: false,
  };
  const transactionParamsSchema = {
    type: "object",
    properties: {
      budgetId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
      transactionId: {
        type: "string",
        minLength: 1,
        maxLength: 500,
        pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
      },
    },
    required: ["budgetId", "transactionId"],
    additionalProperties: false,
  };
  app.get<{
    Params: TransactionRouteParams;
    Headers: TransactionHeaders;
  }>(
    "/api/v1/budgets/:budgetId/transactions/:transactionId",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "getBudgetTransaction",
        summary: "Read one transaction and its optimistic concurrency revision",
        security: [{ bearerAuth: [] }],
        params: transactionParamsSchema,
        headers: budgetEncryptionHeaderSchema,
        response: {
          200: {
            type: "object",
            properties: {
              transaction: transactionSnapshotSchema,
              revision: { type: "string", pattern: "^[a-f0-9]{64}$" },
            },
            required: ["transaction", "revision"],
            additionalProperties: false,
          },
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const result = await transactionApi.readTransaction(
          request.params.budgetId,
          request.params.transactionId,
          request.headers["x-budget-encryption-password"],
        );
        if (!result) return transactionNotFound(reply);
        reply.header("cache-control", "no-store");
        return reply.code(200).send({
          transaction: result.snapshot,
          revision: result.revision,
        });
      } catch (error) {
        reportTransactionFailure("Transaction read failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{
    Params: TransactionRouteParams;
    Headers: TransactionHeaders;
    Body: SubmitTransactionBody;
  }>(
    "/api/v1/budgets/:budgetId/transactions/:transactionId/operations",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "submitTransactionOperation",
        summary:
          "Submit an idempotent stable-ID transaction create, update, or deletion",
        security: [{ bearerAuth: [] }],
        params: transactionParamsSchema,
        headers: {
          ...budgetEncryptionHeaderSchema,
          properties: {
            ...budgetEncryptionHeaderSchema.properties,
            "idempotency-key": {
              type: "string",
              minLength: 1,
              maxLength: IDEMPOTENCY_KEY_MAX_LENGTH,
              pattern: "\\S",
            },
          },
          required: ["idempotency-key"],
        },
        body: {
          type: "object",
          properties: {
            operationId: { type: "string", format: "uuid" },
            action: { type: "string", enum: ["create", "update", "delete"] },
            baseRevision: { type: "string", minLength: 1, maxLength: 500 },
            baseSnapshot: {
              type: "object",
              additionalProperties: true,
              maxProperties: 20,
            },
            patch: {
              type: "object",
              additionalProperties: true,
              maxProperties: 8,
            },
          },
          required: ["operationId", "action", "patch"],
          additionalProperties: false,
        },
        response: {
          200: operationResultSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          409: errorSchema,
          502: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (
        !DeviceAuthorization.canAccessBudget(device, request.params.budgetId)
      ) {
        return transactionNotFound(reply);
      }
      if (!transactionApi) return actualNotConfigured(reply);

      const idempotencyKey = request.headers["idempotency-key"];
      try {
        if (
          request.body.action === "create" &&
          (request.body.baseRevision !== undefined ||
            request.body.baseSnapshot !== undefined)
        ) {
          return invalidTransactionRequest(reply);
        }
        if (
          request.body.action !== "create" &&
          (request.body.baseRevision === undefined ||
            request.body.baseSnapshot === undefined)
        ) {
          return invalidTransactionRequest(reply);
        }
        const transactionRequest =
          request.body.action === "create"
            ? {
                operationId: request.body.operationId,
                principalId: device.principalId,
                budgetId: request.params.budgetId,
                idempotencyKey,
                transactionId: request.params.transactionId,
                action: "create" as const,
                patch: request.body.patch,
                ...(request.headers["x-budget-encryption-password"] ===
                undefined
                  ? {}
                  : {
                      encryptionPassword:
                        request.headers["x-budget-encryption-password"],
                    }),
              }
            : {
                operationId: request.body.operationId,
                principalId: device.principalId,
                budgetId: request.params.budgetId,
                idempotencyKey,
                transactionId: request.params.transactionId,
                baseRevision: request.body.baseRevision!,
                baseSnapshot: request.body.baseSnapshot!,
                action: request.body.action,
                patch: request.body.patch,
                ...(request.headers["x-budget-encryption-password"] ===
                undefined
                  ? {}
                  : {
                      encryptionPassword:
                        request.headers["x-budget-encryption-password"],
                    }),
              };
        const result = await transactionApi.submit({
          ...transactionRequest,
        });
        if (result.operation.status === "rejected") {
          const errorCode = result.operation.errorCode ?? "invalid_request";
          return invalidTransactionRequest(
            reply,
            rejectedTransactionMessage(errorCode),
            errorCode,
          );
        }
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        if (error instanceof TransactionRequestValidationError) {
          return invalidTransactionRequest(
            reply,
            error.message,
            error.errorCode,
          );
        }
        if (error instanceof IdempotencyKeyReuseError) {
          return reply.code(409).send({
            error: {
              code: "idempotency_key_reused",
              message: "Idempotency key was already used for another request",
            },
          });
        }
        if (error instanceof OperationIdReuseError) {
          return reply.code(409).send({
            error: {
              code: "operation_id_reused",
              message: "Operation ID was already used for another request",
            },
          });
        }
        reportTransactionFailure("Transaction operation failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.get<{ Params: OperationRouteParams; Headers: OperationHeaders }>(
    "/api/v1/operations/:operationId",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "getTransactionOperation",
        summary: "Read the durable status and any conflict for an operation",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: { operationId: { type: "string", format: "uuid" } },
          required: ["operationId"],
          additionalProperties: false,
        },
        headers: budgetEncryptionHeaderSchema,
        response: {
          200: operationResultSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const encryptionPassword =
          request.headers["x-budget-encryption-password"];
        const result =
          encryptionPassword === undefined
            ? await transactionApi.getOperation(
                device.principalId,
                request.params.operationId,
              )
            : await transactionApi.getOperation(
                device.principalId,
                request.params.operationId,
                encryptionPassword,
              );
        if (!result) {
          return transactionNotFound(reply);
        } else if (
          !DeviceAuthorization.canAccessBudget(
            device,
            result.operation.budgetId,
          )
        ) {
          return transactionNotFound(reply);
        }
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        reportTransactionFailure("Transaction operation lookup failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );

  app.post<{ Params: OperationRouteParams }>(
    "/api/v1/operations/:operationId/discard",
    {
      schema: {
        tags: ["Transactions"],
        operationId: "discardUncertainTransactionOperation",
        summary: "Discard an uncertain transaction operation on the server",
        description:
          "Marks an uncertain operation as discarded only if it is still uncertain. If its status changed, returns the current status without discarding it.",
        security: [{ bearerAuth: [] }],
        params: {
          type: "object",
          properties: { operationId: { type: "string", format: "uuid" } },
          required: ["operationId"],
          additionalProperties: false,
        },
        response: {
          200: operationResultSchema,
          400: errorSchema,
          401: errorSchema,
          404: errorSchema,
          503: errorSchema,
        },
      },
      attachValidation: true,
    },
    async (request, reply) => {
      const device = await requireDevice(deviceAuth, request, reply);
      if (!device) return reply;
      if (request.validationError) return invalidTransactionRequest(reply);
      if (!transactionApi) return actualNotConfigured(reply);

      try {
        const result = await transactionApi.discardUncertainOperation(
          device.principalId,
          request.params.operationId,
        );
        if (
          !result ||
          !DeviceAuthorization.canAccessBudget(
            device,
            result.operation.budgetId,
          )
        ) {
          return transactionNotFound(reply);
        }
        reply.header("cache-control", "no-store");
        return reply.code(200).send(toOperationResultResponse(result));
      } catch (error) {
        reportTransactionFailure("Uncertain operation discard failed", error);
        return actualRequestFailed(reply, error);
      }
    },
  );
}

function rejectedTransactionMessage(errorCode: string): string {
  switch (errorCode) {
    case "account-not-found":
      return "Account does not exist in this budget";
    case "account-closed":
      return "Choose an open account for this transaction";
    case "payee-not-found":
      return "Payee does not exist in this budget";
    case "transfer-account-closed":
      return "Choose an open destination account for this transfer";
    case "transfer-account-same-as-source":
      return "Choose a different destination account for this transfer";
    case "category-not-found":
      return "Category does not exist in this budget";
    case "category-hidden":
      return "Choose a visible category for this transaction";
    default:
      return "Transaction request is invalid";
  }
}

function toOperationResultResponse(result: {
  operation: WriteOperation;
  conflict: OperationConflict | null;
}) {
  return {
    operation: {
      operationId: result.operation.operationId,
      budgetId: result.operation.budgetId,
      action: result.operation.action,
      transactionId: result.operation.entityId ?? "",
      baseRevision: result.operation.baseRevision ?? "",
      baseSnapshot: result.operation.baseSnapshot,
      patch: result.operation.patch,
      status: result.operation.status,
      attemptCount: result.operation.attemptCount,
      errorCode: result.operation.errorCode,
      actualResult: result.operation.actualResult,
      createdAt: result.operation.createdAt.toISOString(),
      updatedAt: result.operation.updatedAt.toISOString(),
      startedAt: result.operation.startedAt?.toISOString() ?? null,
      completedAt: result.operation.completedAt?.toISOString() ?? null,
    },
    conflict: result.conflict
      ? {
          conflictId: result.conflict.conflictId,
          conflictType: result.conflict.conflictType,
          baseSnapshot: result.conflict.baseSnapshot,
          localPatch: result.conflict.localPatch,
          remoteSnapshot: result.conflict.remoteSnapshot,
          conflictingFields: result.conflict.conflictingFields,
          createdAt: result.conflict.createdAt.toISOString(),
        }
      : null,
  };
}

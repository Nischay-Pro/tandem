import { API_ERROR_SCHEMA_REF } from "../openapi/error-responses.js";
import { IDEMPOTENCY_KEY_MAX_LENGTH } from "../write-journal/contracts.js";

export const errorSchema = API_ERROR_SCHEMA_REF;
export const statusOk = {
  type: "object",
  properties: { status: { type: "string", const: "ok" } },
  required: ["status"],
  additionalProperties: false,
};
export const accountCreatedResponse = {
  type: "object",
  properties: {
    status: { type: "string", const: "ok" },
    accountId: { type: "string", minLength: 1 },
  },
  required: ["status", "accountId"],
  additionalProperties: false,
};
export const budgetEncryptionHeaderSchema = {
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

export const paramsSchema = {
  type: "object",
  properties: {
    budgetId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
  },
  required: ["budgetId"],
  additionalProperties: false,
};
export const accountParamsSchema = {
  type: "object",
  properties: {
    budgetId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
    accountId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
  },
  required: ["budgetId", "accountId"],
  additionalProperties: false,
};
export const categoryParamsSchema = {
  type: "object",
  properties: {
    budgetId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
    categoryId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
  },
  required: ["budgetId", "categoryId"],
  additionalProperties: false,
};
export const categoryGroupParamsSchema = {
  type: "object",
  properties: {
    budgetId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
    groupId: {
      type: "string",
      minLength: 1,
      maxLength: 500,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
  },
  required: ["budgetId", "groupId"],
  additionalProperties: false,
};
export const headersSchema = {
  type: "object",
  properties: {
    "x-budget-encryption-password": {
      type: "string",
      minLength: 1,
      maxLength: 1_024,
    },
    "idempotency-key": {
      type: "string",
      minLength: 1,
      maxLength: IDEMPOTENCY_KEY_MAX_LENGTH,
      pattern: "^(?=.*\\S)[^\\u0000-\\u001f\\u007f]+$",
    },
  },
  required: ["idempotency-key"],
  additionalProperties: true,
};
export const commonResponseSchema = {
  200: statusOk,
  400: errorSchema,
  401: errorSchema,
  404: errorSchema,
  409: errorSchema,
  429: errorSchema,
  502: errorSchema,
  503: errorSchema,
};

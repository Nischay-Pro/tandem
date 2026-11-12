export const API_ERROR_SCHEMA_ID = "TandemApiError";

export const API_ERROR_SCHEMA = {
  $id: API_ERROR_SCHEMA_ID,
  type: "object",
  properties: {
    error: {
      type: "object",
      properties: {
        // Business validation codes retain their existing spelling; the
        // contract standardizes the envelope without narrowing code values.
        code: { type: "string", minLength: 1 },
        message: { type: "string", minLength: 1 },
      },
      required: ["code", "message"],
      additionalProperties: false,
    },
  },
  required: ["error"],
  additionalProperties: false,
} as const;

export const API_ERROR_SCHEMA_REF = {
  $ref: `${API_ERROR_SCHEMA_ID}#`,
} as const;

export function buildOpenApiLocalReference(
  schema: { $id?: unknown },
  index: number,
): string {
  return typeof schema.$id === "string" ? schema.$id : `def-${index}`;
}

const sharedErrorResponses = {
  400: {
    name: "BadRequest",
    description: "The request is invalid.",
    example: {
      error: {
        code: "invalid_request",
        message: "Check the request details and try again.",
      },
    },
  },
  401: {
    name: "Unauthorized",
    description: "Valid device credentials are required.",
    example: {
      error: {
        code: "invalid_device_credential",
        message: "Valid device credentials are required.",
      },
    },
  },
  404: {
    name: "NotFound",
    description: "The requested resource was not found.",
    example: {
      error: { code: "not_found", message: "The resource was not found." },
    },
  },
  409: {
    name: "Conflict",
    description: "The request conflicts with the current resource state.",
    example: {
      error: {
        code: "request_conflict",
        message: "Refresh the resource, then try again.",
      },
    },
  },
  413: {
    name: "PayloadTooLarge",
    description: "The request body is too large.",
    example: {
      error: {
        code: "request_too_large",
        message: "Reduce the request size and try again.",
      },
    },
  },
  415: {
    name: "UnsupportedMediaType",
    description: "The request content type is unsupported.",
    example: {
      error: {
        code: "unsupported_media_type",
        message: "Send this request as application/json.",
      },
    },
  },
  429: {
    name: "TooManyRequests",
    description: "The request was rate limited or the operation is busy.",
    example: {
      error: {
        code: "rate_limited",
        message: "Wait a moment, then try again.",
      },
    },
  },
  500: {
    name: "InternalServerError",
    description: "The API could not complete the request.",
    example: {
      error: {
        code: "internal_error",
        message: "Tandem could not complete the request. Try again shortly.",
      },
    },
  },
  502: {
    name: "BadGateway",
    description: "The Actual Budget operation could not be completed.",
    example: {
      error: {
        code: "actual_operation_failed",
        message: "The Actual Budget operation could not be completed.",
      },
    },
  },
  503: {
    name: "ServiceUnavailable",
    description: "A required service is unavailable.",
    example: {
      error: {
        code: "actual_unavailable",
        message: "Check the server connection and try again.",
      },
    },
  },
} as const;

type OpenApiDocument = {
  paths: Record<string, unknown>;
  components?: Record<string, unknown>;
  [key: string]: unknown;
};

type OpenApiOperation = Record<string, unknown> & {
  responses?: Record<string, unknown>;
};

const httpMethods = new Set([
  "get",
  "put",
  "post",
  "delete",
  "options",
  "head",
  "patch",
  "trace",
]);

/**
 * Adds status documentation implied by each route's security and request
 * shape, then replaces every operation error response with a named shared
 * OpenAPI response component.
 */
export function withSharedApiErrorResponses<T extends OpenApiDocument>(
  source: T,
): T {
  const document = structuredClone(source);
  const components = (document.components ??= {});
  const schemas = (components.schemas ??= {}) as Record<string, unknown>;
  const responses = (components.responses ??= {}) as Record<string, unknown>;

  if (!(API_ERROR_SCHEMA_ID in schemas)) {
    throw new Error(
      `OpenAPI document is missing the ${API_ERROR_SCHEMA_ID} schema`,
    );
  }

  for (const definition of Object.values(sharedErrorResponses)) {
    responses[definition.name] = {
      description: definition.description,
      content: {
        "application/json": {
          schema: { $ref: `#/components/schemas/${API_ERROR_SCHEMA_ID}` },
          example: definition.example,
        },
      },
    };
  }

  for (const [path, pathItemValue] of Object.entries(document.paths)) {
    if (!isRecord(pathItemValue)) continue;
    for (const [method, operationValue] of Object.entries(pathItemValue)) {
      if (!httpMethods.has(method) || !isRecord(operationValue)) continue;
      const operation = operationValue as OpenApiOperation;
      const operationResponses = (operation.responses ??= {});
      const requiredStatuses = new Set<number>();

      for (const status of Object.keys(operationResponses)) {
        const statusCode = Number(status);
        if (
          Number.isInteger(statusCode) &&
          statusCode >= 400 &&
          statusCode <= 599
        ) {
          requiredStatuses.add(statusCode);
        }
      }

      const hasBody = isRecord(operation.requestBody);
      const hasParameters =
        Array.isArray(operation.parameters) && operation.parameters.length > 0;
      if (hasBody || hasParameters) requiredStatuses.add(400);
      if (hasBody) {
        requiredStatuses.add(413);
        requiredStatuses.add(415);
      }
      if (hasBearerSecurity(operation.security)) requiredStatuses.add(401);
      if (operation["x-tandem-rate-limited"] === true) {
        requiredStatuses.add(429);
      }
      requiredStatuses.add(500);

      for (const status of requiredStatuses) {
        const definition =
          sharedErrorResponses[status as keyof typeof sharedErrorResponses];
        if (!definition) {
          throw new Error(
            `No shared API error response is defined for HTTP ${status} at ${method.toUpperCase()} ${path}`,
          );
        }
        operationResponses[String(status)] = {
          $ref: `#/components/responses/${definition.name}`,
        };
      }
    }
  }

  return document;
}

function hasBearerSecurity(value: unknown): boolean {
  return (
    Array.isArray(value) &&
    value.some((entry) => isRecord(entry) && "bearerAuth" in entry)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

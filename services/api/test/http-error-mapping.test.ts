import { describe, expect, it } from "vitest";
import {
  errorCodeFromPayload,
  frameworkErrorCode,
  frameworkErrorStatus,
  responseErrorCode,
  safeFrameworkErrorResponse,
} from "../src/app.js";

describe("framework error mapping", () => {
  it("reads only typed framework status codes and codes", () => {
    expect(frameworkErrorStatus(null)).toBeUndefined();
    expect(frameworkErrorStatus({})).toBeUndefined();
    expect(frameworkErrorStatus({ statusCode: "409" })).toBeUndefined();
    expect(frameworkErrorStatus({ statusCode: 409 })).toBe(409);

    expect(frameworkErrorCode(null)).toBeUndefined();
    expect(frameworkErrorCode({})).toBeUndefined();
    expect(frameworkErrorCode({ code: 409 })).toBeUndefined();
    expect(frameworkErrorCode({ code: "FST_ERR_TEST" })).toBe("FST_ERR_TEST");
  });

  it("maps framework parser errors and safe HTTP statuses", () => {
    expect(
      safeFrameworkErrorResponse(400, "FST_ERR_CTP_INVALID_MEDIA_TYPE"),
    ).toMatchObject({ code: "unsupported_media_type" });
    expect(safeFrameworkErrorResponse(415)).toMatchObject({
      code: "unsupported_media_type",
    });
    expect(
      safeFrameworkErrorResponse(400, "FST_ERR_CTP_BODY_TOO_LARGE"),
    ).toMatchObject({ code: "request_too_large" });
    expect(safeFrameworkErrorResponse(413)).toMatchObject({
      code: "request_too_large",
    });
    expect(safeFrameworkErrorResponse(400)).toMatchObject({
      code: "invalid_request",
    });
    expect(safeFrameworkErrorResponse(401)).toMatchObject({
      code: "invalid_device_credential",
    });
    expect(safeFrameworkErrorResponse(422)).toMatchObject({
      code: "internal_error",
    });
    expect(safeFrameworkErrorResponse(404)).toMatchObject({
      code: "route_not_found",
    });
    expect(safeFrameworkErrorResponse(409)).toMatchObject({
      code: "request_conflict",
    });
    expect(safeFrameworkErrorResponse(429)).toMatchObject({
      code: "rate_limited",
    });
    expect(safeFrameworkErrorResponse(418)).toMatchObject({
      code: "internal_error",
    });
  });
});

describe("errorCodeFromPayload", () => {
  it("accepts safe codes and rejects malformed or unsafe payloads", () => {
    expect(errorCodeFromPayload(undefined)).toBeUndefined();
    expect(errorCodeFromPayload("not json")).toBeUndefined();
    expect(errorCodeFromPayload('"text"')).toBeUndefined();
    expect(errorCodeFromPayload("null")).toBeUndefined();
    expect(errorCodeFromPayload("{}")).toBeUndefined();
    expect(errorCodeFromPayload('{"error":null}')).toBeUndefined();
    expect(errorCodeFromPayload('{"error":12}')).toBeUndefined();
    expect(errorCodeFromPayload('{"error":{}}')).toBeUndefined();
    expect(errorCodeFromPayload('{"error":{"code":12}}')).toBeUndefined();
    expect(errorCodeFromPayload('{"error":{"code":"private detail"}}')).toBe(
      undefined,
    );
    expect(
      errorCodeFromPayload('{"error":{"code":"actual_unavailable"}}'),
    ).toBe("actual_unavailable");
  });

  it("falls back to the HTTP status when a payload has no safe code", () => {
    expect(responseErrorCode("not json", 503)).toBe("http_503");
    expect(
      responseErrorCode('{"error":{"code":"actual_unavailable"}}', 503),
    ).toBe("actual_unavailable");
  });
});

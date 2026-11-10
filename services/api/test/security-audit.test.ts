import { describe, expect, it, vi } from "vitest";
import { logSecurityAuditEvent } from "../src/security-audit.js";

describe("security audit events", () => {
  it("redacts credentials and one-use codes from structured fields", () => {
    const writeLine = vi.fn<(line: string) => void>();

    logSecurityAuditEvent(
      {
        event: "device.sign_in",
        outcome: "success",
        authorization: "Bearer secret-token",
        headers: {
          "X-Budget-Encryption-Password": "budget-password",
        },
        code: "single-use-return-code",
        authorizationCode: "single-use-return-code",
        accessToken: "secret-token",
        access_token: "secret-token",
        sessionToken: "actual-session-token",
        sessionPassword: "budget-password",
      },
      writeLine,
    );

    expect(writeLine).toHaveBeenCalledOnce();
    const logged = JSON.parse(writeLine.mock.calls[0]![0]) as Record<
      string,
      unknown
    >;
    expect(logged).toMatchObject({
      event: "device.sign_in",
      outcome: "success",
      authorization: "[REDACTED]",
      headers: { "X-Budget-Encryption-Password": "[REDACTED]" },
      code: "[REDACTED]",
      authorizationCode: "[REDACTED]",
      accessToken: "[REDACTED]",
      access_token: "[REDACTED]",
      sessionToken: "[REDACTED]",
      sessionPassword: "[REDACTED]",
    });
    expect(writeLine.mock.calls[0]![0]).not.toContain("secret-token");
    expect(writeLine.mock.calls[0]![0]).not.toContain("actual-session-token");
    expect(writeLine.mock.calls[0]![0]).not.toContain("single-use-return-code");
  });

  it("does not interrupt the request when the log sink throws", () => {
    expect(() =>
      logSecurityAuditEvent({ event: "device.sign_in" }, () => {
        throw new Error("log sink unavailable");
      }),
    ).not.toThrow();
  });

  it("skips output when JSON serialization returns undefined", () => {
    const writeLine = vi.fn<(line: string) => void>();

    logSecurityAuditEvent(
      undefined as unknown as Record<string, unknown>,
      writeLine,
    );

    expect(writeLine).not.toHaveBeenCalled();
  });

  it("does not interrupt the request when an event cannot be serialized", () => {
    const writeLine = vi.fn<(line: string) => void>();
    const event: Record<string, unknown> = { event: "device.sign_in" };
    event.circular = event;

    expect(() => logSecurityAuditEvent(event, writeLine)).not.toThrow();
    expect(writeLine).not.toHaveBeenCalled();
  });
});

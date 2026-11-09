import { describe, expect, it, vi } from "vitest";
import { logSecurityAuditEvent } from "../src/security-audit.js";

describe("security audit events", () => {
  it("redacts credentials and pairing codes from structured fields", () => {
    const writeLine = vi.fn<(line: string) => void>();

    logSecurityAuditEvent(
      {
        event: "device.pair",
        outcome: "success",
        authorization: "Bearer secret-token",
        headers: {
          "X-Budget-Encryption-Password": "budget-password",
        },
        code: "4DF7-J1M9",
        pairingCode: "4DF7-J1M9",
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
      event: "device.pair",
      outcome: "success",
      authorization: "[REDACTED]",
      headers: { "X-Budget-Encryption-Password": "[REDACTED]" },
      code: "[REDACTED]",
      pairingCode: "[REDACTED]",
      accessToken: "[REDACTED]",
      access_token: "[REDACTED]",
      sessionToken: "[REDACTED]",
      sessionPassword: "[REDACTED]",
    });
    expect(writeLine.mock.calls[0]![0]).not.toContain("secret-token");
    expect(writeLine.mock.calls[0]![0]).not.toContain("actual-session-token");
    expect(writeLine.mock.calls[0]![0]).not.toContain("4DF7-J1M9");
  });

  it("does not interrupt the request when the log sink throws", () => {
    expect(() =>
      logSecurityAuditEvent({ event: "device.pair" }, () => {
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
    const event: Record<string, unknown> = { event: "device.pair" };
    event.circular = event;

    expect(() => logSecurityAuditEvent(event, writeLine)).not.toThrow();
    expect(writeLine).not.toHaveBeenCalled();
  });
});

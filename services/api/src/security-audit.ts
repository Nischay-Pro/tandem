function isSensitiveAuditField(key: string): boolean {
  const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, "");
  return (
    normalized === "authorization" ||
    normalized.includes("code") ||
    normalized.includes("token") ||
    normalized.includes("password") ||
    normalized.includes("secret")
  );
}

export function logSecurityAuditEvent(
  event: Record<string, unknown>,
  writeLine: (line: string) => void = (line) => console.info(line),
): void {
  try {
    const serialized = JSON.stringify(event, (key, value: unknown) =>
      isSensitiveAuditField(key) ? "[REDACTED]" : value,
    );
    if (serialized !== undefined) writeLine(serialized);
  } catch {
    // Audit output must never interrupt authentication.
  }
}

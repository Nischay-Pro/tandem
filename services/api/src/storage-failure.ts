const STORAGE_FULL_CODES = new Set(["53100", "SQLITE_FULL", "ENOSPC"]);

export const SERVER_STORAGE_FULL_ERROR = {
  code: "server_storage_full",
  message:
    "Tandem server storage is full. Free disk space on the server and try again.",
};

export function isStorageFullError(error: unknown): boolean {
  const visited = new Set<object>();
  let current: unknown = error;

  while (
    typeof current === "object" &&
    current !== null &&
    !visited.has(current)
  ) {
    visited.add(current);
    const candidate = current as {
      cause?: unknown;
      code?: unknown;
      errno?: unknown;
      message?: unknown;
    };
    if (
      (typeof candidate.code === "string" &&
        STORAGE_FULL_CODES.has(candidate.code.toUpperCase())) ||
      (typeof candidate.errno === "string" &&
        STORAGE_FULL_CODES.has(candidate.errno.toUpperCase()))
    ) {
      return true;
    }

    const message =
      typeof candidate.message === "string"
        ? candidate.message.toLowerCase()
        : "";
    if (
      message.includes("no space left on device") ||
      message.includes("database or disk is full") ||
      message.includes("could not extend file")
    ) {
      return true;
    }
    current = candidate.cause;
  }

  return false;
}

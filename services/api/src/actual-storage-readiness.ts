import { statfsSync } from "node:fs";
import { dirname, resolve } from "node:path";

export interface ActualReadiness {
  ready: boolean;
  code?: string;
  message?: string;
}

type WorkspaceStats = Pick<ReturnType<typeof statfsSync>, "bavail">;
type ReadWorkspaceStats = (path: string) => WorkspaceStats;

export function actualWorkspaceHasFreeSpace(
  dataDir: string,
  readStats: ReadWorkspaceStats = statfsSync,
): boolean {
  let storagePath = resolve(dataDir);
  while (true) {
    try {
      return readStats(storagePath).bavail > 0;
    } catch (error) {
      let code: unknown;
      if (typeof error === "object" && error !== null && "code" in error) {
        code = error.code;
      }
      const parent = dirname(storagePath);
      if (code !== "ENOENT" || parent === storagePath) throw error;
      storagePath = parent;
    }
  }
}

export function readActualStorageReadiness(
  dataDir: string,
  startup: ActualReadiness,
  hasFreeSpace: (path: string) => boolean = actualWorkspaceHasFreeSpace,
): ActualReadiness {
  if (!startup.ready) {
    return {
      ready: false,
      ...(startup.message === undefined ? {} : { message: startup.message }),
    };
  }
  try {
    if (!hasFreeSpace(dataDir)) {
      return {
        ready: false,
        code: "server_storage_full",
        message:
          "Tandem server storage is full. Free disk space on the server and try again.",
      };
    }
  } catch {
    return {
      ready: false,
      message:
        "Tandem could not check available server storage. Check the server and try again.",
    };
  }
  return { ready: true };
}

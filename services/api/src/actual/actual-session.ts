import * as actualApi from "@actual-app/api";

export const SUPPORTED_ACTUAL_SERVER_VERSION = "26.11.0";
export const ACTUAL_SESSION_OPERATION_TIMEOUT_MS = 5 * 60 * 1_000;

export type ActualConnectionConfig = {
  serverURL: string;
  dataDir: string;
  verbose?: boolean;
} & (
  | { password: string; sessionToken?: never }
  | { sessionToken: string; password?: never }
);

export type ActualServerVersion =
  { version: string } | { error: "no-server" | "network-failure" };

export interface ActualApiPort {
  init(config: ActualConnectionConfig): Promise<unknown>;
  getServerVersion(): Promise<ActualServerVersion>;
  downloadBudget(
    syncId: string,
    options?: { password?: string },
  ): Promise<void>;
  sync(): Promise<void>;
  shutdown(): Promise<void>;
}

export interface BudgetOperationOptions {
  encryptionPassword?: string;
  synchronizeAfterWrite?: boolean | ((result: unknown) => boolean);
}

export interface ActualSecondaryFailure {
  phase: "initialization-cleanup" | "post-write-sync";
  primaryError: unknown;
  secondaryError: unknown;
}

export class ActualPostWriteSyncError<T = unknown> extends Error {
  readonly kind = "post-write-sync-failed";

  constructor(
    readonly operationResult: T,
    readonly syncError: unknown,
  ) {
    super(
      "Actual operation returned a result, but budget synchronization failed",
    );
    this.name = "ActualPostWriteSyncError";
  }
}

export class ActualSessionOperationTimeoutError extends Error {
  constructor(readonly timeoutMs: number) {
    super(`Actual operation exceeded its ${timeoutMs} ms time limit`);
    this.name = "ActualSessionOperationTimeoutError";
  }
}

export type ActualSecondaryFailureHandler = (
  failure: ActualSecondaryFailure,
) => void;

export type ActualCompatibilityFailure =
  "server-version-mismatch" | "version-check-failed";

export class ActualCompatibilityError extends Error {
  constructor(
    readonly kind: ActualCompatibilityFailure,
    readonly expectedVersion: string,
    readonly actualVersion?: string,
    reason?: string,
  ) {
    const message =
      kind === "server-version-mismatch"
        ? `Unsupported Actual server version ${actualVersion}; this build supports ${expectedVersion}`
        : `Could not verify the Actual server version (${reason}); this build requires ${expectedVersion}`;
    super(message);
    this.name = "ActualCompatibilityError";
  }
}

let actualApiQueue: Promise<void> = Promise.resolve();
let activeActualSession: symbol | undefined;

function enqueueActualApiOperation<T>(operation: () => Promise<T>): Promise<T> {
  const result = actualApiQueue.then(operation);
  actualApiQueue = result.then(
    () => undefined,
    () => undefined,
  );
  return result;
}

function reportSecondaryFailure(failure: ActualSecondaryFailure): void {
  const errorName = (error: unknown): string =>
    error instanceof Error ? error.name : typeof error;

  console.error("Actual session secondary failure", {
    phase: failure.phase,
    primaryError: errorName(failure.primaryError),
    secondaryError: errorName(failure.secondaryError),
  });
}

export class ActualSession {
  #sessionId = Symbol("ActualSession");
  #closing = false;
  #poisoned = false;
  #cleanupFailed = false;
  #closePromise: Promise<void> | undefined;

  constructor(
    private readonly client: ActualApiPort,
    private readonly config: ActualConnectionConfig,
    private readonly expectedVersion = SUPPORTED_ACTUAL_SERVER_VERSION,
    private readonly onSecondaryFailure: ActualSecondaryFailureHandler = reportSecondaryFailure,
    private readonly operationTimeoutMs = ACTUAL_SESSION_OPERATION_TIMEOUT_MS,
  ) {
    if (!Number.isSafeInteger(operationTimeoutMs) || operationTimeoutMs < 1) {
      throw new Error("Actual operation timeout must be a positive integer");
    }
  }

  async start(): Promise<void> {
    this.#assertAcceptingWork();
    return enqueueActualApiOperation(() =>
      this.#withTimeout(() => this.#initialize()),
    );
  }

  async execute<T>(
    operation: () => Promise<T>,
    options: Pick<BudgetOperationOptions, "synchronizeAfterWrite"> = {},
  ): Promise<T> {
    this.#assertAcceptingWork();
    return enqueueActualApiOperation(() =>
      this.#withTimeout(async () => {
        await this.#initialize();
        return this.#executeOperation(operation, options.synchronizeAfterWrite);
      }),
    );
  }

  async runBudget<T>(
    syncId: string,
    operation: () => Promise<T>,
    options: BudgetOperationOptions = {},
  ): Promise<T> {
    const normalizedSyncId = syncId.trim();
    if (normalizedSyncId.length === 0) {
      throw new Error("Actual budget sync ID is required");
    }

    this.#assertAcceptingWork();
    return enqueueActualApiOperation(() =>
      this.#withTimeout(async () => {
        await this.#initialize();
        const encryptionOptions =
          options.encryptionPassword === undefined
            ? undefined
            : { password: options.encryptionPassword };
        await this.client.downloadBudget(normalizedSyncId, encryptionOptions);
        return this.#executeOperation(operation, options.synchronizeAfterWrite);
      }),
    );
  }

  close(): Promise<void> {
    if (this.#closePromise) {
      return this.#closePromise;
    }

    this.#closing = true;
    const closePromise = enqueueActualApiOperation(async () => {
      if (this.#poisoned) {
        return;
      }
      if (activeActualSession !== this.#sessionId) {
        return;
      }

      await this.#withTimeout(() => this.client.shutdown());
      activeActualSession = undefined;
    });
    this.#closePromise = closePromise;
    void closePromise.catch(() => {
      this.#closePromise = undefined;
    });
    return closePromise;
  }

  #assertAcceptingWork(): void {
    if (this.#poisoned) {
      throw new Error(
        "Actual session is unavailable after an operation timed out; restart the API process",
      );
    }
    if (this.#closing) {
      throw new Error("Actual session is closing");
    }
  }

  async #withTimeout<T>(operation: () => Promise<T>): Promise<T> {
    let rejectTimeout!: (error: ActualSessionOperationTimeoutError) => void;
    const timeout = new Promise<never>((_resolve, reject) => {
      rejectTimeout = reject;
    });
    const timeoutHandle = setTimeout(() => {
      this.#poisoned = true;
      rejectTimeout(
        new ActualSessionOperationTimeoutError(this.operationTimeoutMs),
      );
    }, this.operationTimeoutMs);
    timeoutHandle.unref?.();

    try {
      return await Promise.race([Promise.resolve().then(operation), timeout]);
    } finally {
      clearTimeout(timeoutHandle);
    }
  }

  async #initialize(): Promise<void> {
    if (this.#poisoned) {
      throw new Error(
        "Actual session is unavailable after an operation timed out; restart the API process",
      );
    }
    if (this.#cleanupFailed) {
      throw new Error(
        "Actual client cleanup previously failed; close the session before retrying",
      );
    }
    if (
      activeActualSession !== undefined &&
      activeActualSession !== this.#sessionId
    ) {
      throw new Error(
        "Another Actual session owns the process-wide API client; close it before opening another session",
      );
    }
    if (activeActualSession === this.#sessionId) {
      return;
    }

    activeActualSession = this.#sessionId;

    try {
      await this.client.init(this.config);

      const server = await this.client.getServerVersion();
      if ("error" in server) {
        throw new ActualCompatibilityError(
          "version-check-failed",
          this.expectedVersion,
          undefined,
          server.error,
        );
      }

      if (server.version !== this.expectedVersion) {
        throw new ActualCompatibilityError(
          "server-version-mismatch",
          this.expectedVersion,
          server.version,
        );
      }
    } catch (error) {
      let cleanupFailed = false;
      let cleanupError: unknown;
      try {
        await this.client.shutdown();
      } catch (shutdownError) {
        cleanupFailed = true;
        cleanupError = shutdownError;
      }
      this.#cleanupFailed = cleanupFailed;

      if (cleanupFailed) {
        this.#reportSecondaryFailure({
          phase: "initialization-cleanup",
          primaryError: error,
          secondaryError: cleanupError,
        });
      } else {
        activeActualSession = undefined;
      }
      throw error;
    }
  }

  async #executeOperation<T>(
    operation: () => Promise<T>,
    synchronizeAfterWrite: BudgetOperationOptions["synchronizeAfterWrite"] = false,
  ): Promise<T> {
    let result: T;
    let operationFailed = false;
    let operationError: unknown;
    try {
      result = await operation();
    } catch (error) {
      operationFailed = true;
      operationError = error;
    }

    let shouldSynchronize = synchronizeAfterWrite === true;
    if (operationFailed && typeof synchronizeAfterWrite === "function") {
      // The operation may have applied part of a write before throwing.
      shouldSynchronize = true;
    } else if (
      !operationFailed &&
      typeof synchronizeAfterWrite === "function"
    ) {
      try {
        shouldSynchronize = synchronizeAfterWrite(result!);
      } catch (error) {
        operationFailed = true;
        operationError = error;
        // A policy callback can fail after the operation mutated local state.
        shouldSynchronize = true;
      }
    }

    if (shouldSynchronize) {
      try {
        await this.client.sync();
      } catch (syncError) {
        if (!operationFailed) {
          throw new ActualPostWriteSyncError(result!, syncError);
        }
        this.#reportSecondaryFailure({
          phase: "post-write-sync",
          primaryError: operationError,
          secondaryError: syncError,
        });
      }
    }

    if (operationFailed) {
      throw operationError;
    }
    return result!;
  }

  #reportSecondaryFailure(failure: ActualSecondaryFailure): void {
    try {
      this.onSecondaryFailure(failure);
    } catch {
      // An observability callback must never replace the financial operation error.
    }
  }
}

export function createActualSession(
  config: ActualConnectionConfig,
  client: ActualApiPort = actualApi,
  onSecondaryFailure?: ActualSecondaryFailureHandler,
): ActualSession {
  return new ActualSession(
    client,
    config,
    SUPPORTED_ACTUAL_SERVER_VERSION,
    onSecondaryFailure,
  );
}

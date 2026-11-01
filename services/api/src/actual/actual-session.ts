import * as actualApi from "@actual-app/api";

export const SUPPORTED_ACTUAL_SERVER_VERSION = "26.10.0";

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
  synchronizeAfterWrite?: boolean;
}

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

export class ActualSession {
  #queue: Promise<void> = Promise.resolve();
  #initialized = false;
  #closing = false;
  #closePromise: Promise<void> | undefined;

  constructor(
    private readonly client: ActualApiPort,
    private readonly config: ActualConnectionConfig,
    private readonly expectedVersion = SUPPORTED_ACTUAL_SERVER_VERSION,
  ) {}

  start(): Promise<void> {
    this.#assertAcceptingWork();
    return this.#enqueue(() => this.#initialize());
  }

  execute<T>(
    operation: () => Promise<T>,
    options: Pick<BudgetOperationOptions, "synchronizeAfterWrite"> = {},
  ): Promise<T> {
    this.#assertAcceptingWork();
    return this.#enqueue(async () => {
      await this.#initialize();
      const result = await operation();
      if (options.synchronizeAfterWrite) {
        await this.client.sync();
      }
      return result;
    });
  }

  runBudget<T>(
    syncId: string,
    operation: () => Promise<T>,
    options: BudgetOperationOptions = {},
  ): Promise<T> {
    if (syncId.trim().length === 0) {
      throw new Error("Actual budget sync ID is required");
    }

    return this.execute(async () => {
      const encryptionOptions =
        options.encryptionPassword === undefined
          ? undefined
          : { password: options.encryptionPassword };
      await this.client.downloadBudget(syncId, encryptionOptions);
      return operation();
    }, options);
  }

  close(): Promise<void> {
    if (this.#closePromise) {
      return this.#closePromise;
    }

    this.#closing = true;
    this.#closePromise = this.#enqueue(async () => {
      if (!this.#initialized) {
        return;
      }

      try {
        await this.client.shutdown();
      } finally {
        this.#initialized = false;
      }
    });
    return this.#closePromise;
  }

  #assertAcceptingWork(): void {
    if (this.#closing) {
      throw new Error("Actual session is closing");
    }
  }

  #enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.#queue.then(operation);
    this.#queue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }

  async #initialize(): Promise<void> {
    if (this.#initialized) {
      return;
    }

    try {
      await this.client.init(this.config);
      this.#initialized = true;

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
      this.#initialized = false;

      if (cleanupFailed) {
        throw new AggregateError(
          [error, cleanupError],
          "Actual compatibility check failed and the client did not shut down cleanly",
          { cause: error },
        );
      }
      throw error;
    }
  }
}

export function createActualSession(
  config: ActualConnectionConfig,
  client: ActualApiPort = actualApi,
): ActualSession {
  return new ActualSession(client, config);
}

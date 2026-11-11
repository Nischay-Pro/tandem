import { OperatorMessageError } from "../operator-message-error.js";
import { getActualPrincipalId } from "../auth/actual-principal-context.js";

export const SUPPORTED_ACTUAL_SERVER_VERSION = "26.11.0";
export const ACTUAL_SESSION_OPERATION_TIMEOUT_MS = 5 * 60 * 1_000;
export const ACTUAL_SESSION_TOKEN_REJECTED_MESSAGE =
  "Actual rejected ACTUAL_SESSION_TOKEN. Replace the token and restart Tandem.";
export const ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE =
  "Actual sign-in expired. Sign in again to continue.";

export class ActualSessionTokenRejectedError extends OperatorMessageError {
  constructor() {
    super(ACTUAL_SESSION_TOKEN_REJECTED_MESSAGE);
    this.name = "ActualSessionTokenRejectedError";
  }
}

export class ActualPrincipalSessionExpiredError extends Error {
  constructor() {
    super(ACTUAL_PRINCIPAL_SESSION_EXPIRED_MESSAGE);
    this.name = "ActualPrincipalSessionExpiredError";
  }
}

interface ActualConnectionOptions {
  serverURL: string;
  dataDir: string;
  verbose?: boolean;
}

export type ActualConnectionConfig = ActualConnectionOptions &
  (
    | { password: string; sessionToken?: never }
    | { sessionToken: string; password?: never }
  );

export interface ActualSessionConfig extends ActualConnectionOptions {
  password?: string;
  sessionToken?: string;
  publicURL?: string;
}

export interface ActualPrincipalSessionResolver {
  sessionToken(principalId: string): Promise<string | undefined>;
  dataDir(principalId: string): Promise<string>;
}

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

export interface BudgetSyncSnapshotResult<T> {
  changed: boolean;
  value: T;
}

interface ActualSyncEvent {
  type: string;
  tables?: readonly string[];
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
  #config: ActualSessionConfig;
  #baseConfig: ActualSessionConfig;
  #activePrincipalId: string | undefined;
  #closing = false;
  #poisoned = false;
  #cleanupFailed = false;
  #readinessErrorMessage: string | undefined;
  #sessionTokenErrorLogged = false;
  #closePromise: Promise<void> | undefined;
  #loadedBudgetSyncId: string | undefined;
  #loadedBudgetWasOpenedWithPassword = false;
  #lastSyncEvent: ActualSyncEvent | undefined;
  #syncEventListenerAttached = false;
  #dirtyBudgetSyncIds = new Set<string>();
  #budgetWriteRevisions = new Map<string, number>();

  constructor(
    private readonly client: ActualApiPort,
    config: ActualSessionConfig,
    private readonly expectedVersion = SUPPORTED_ACTUAL_SERVER_VERSION,
    private readonly onSecondaryFailure: ActualSecondaryFailureHandler = reportSecondaryFailure,
    private readonly operationTimeoutMs = ACTUAL_SESSION_OPERATION_TIMEOUT_MS,
    private readonly principalSessions?: ActualPrincipalSessionResolver,
  ) {
    this.#config = { ...config };
    this.#baseConfig = { ...config };
    if (!Number.isSafeInteger(operationTimeoutMs) || operationTimeoutMs < 1) {
      throw new Error("Actual operation timeout must be a positive integer");
    }
  }

  async start(): Promise<void> {
    if (!this.#hasCredential()) return;
    this.#assertAcceptingWork();
    try {
      await enqueueActualApiOperation(() =>
        this.#withTimeout(() => this.#initialize()),
      );
    } catch (error) {
      if (!(error instanceof ActualSessionTokenRejectedError)) throw error;
    }
  }

  async useSessionToken(sessionToken: string): Promise<void> {
    if (sessionToken.trim().length === 0) {
      throw new Error("Actual session token must not be empty");
    }
    await enqueueActualApiOperation(async () => {
      if (this.#poisoned) {
        throw new Error(
          "Actual session is unavailable after an operation timed out; restart the API process",
        );
      }
      if (this.#closing) this.#closing = false;
      if (activeActualSession === this.#sessionId) {
        await this.#withTimeout(() => this.client.shutdown());
        activeActualSession = undefined;
      }
      this.#config = {
        serverURL: this.#config.serverURL,
        dataDir: this.#config.dataDir,
        ...(this.#config.verbose === undefined
          ? {}
          : { verbose: this.#config.verbose }),
        sessionToken,
      };
      this.#baseConfig = { ...this.#config };
      this.#activePrincipalId = undefined;
      this.#loadedBudgetSyncId = undefined;
      this.#loadedBudgetWasOpenedWithPassword = false;
      this.#closePromise = undefined;
      this.#readinessErrorMessage = undefined;
      await this.#withTimeout(() => this.#initialize());
    });
  }

  get isReady(): boolean {
    return (
      this.#readinessErrorMessage === undefined &&
      activeActualSession === this.#sessionId
    );
  }

  get readinessMessage(): string | undefined {
    return this.#readinessErrorMessage;
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
    const result = await this.#runBudgetSnapshot(
      syncId,
      async () => operation(),
      options,
      false,
    );
    return result.value;
  }

  async runBudgetSnapshot<T>(
    syncId: string,
    operation: (changed: boolean) => Promise<T>,
    options: BudgetOperationOptions = {},
  ): Promise<BudgetSyncSnapshotResult<T>> {
    return this.#runBudgetSnapshot(syncId, operation, options, true);
  }

  async #runBudgetSnapshot<T>(
    syncId: string,
    operation: (changed: boolean) => Promise<T>,
    options: BudgetOperationOptions,
    consumeDirtyProjection: boolean,
  ): Promise<BudgetSyncSnapshotResult<T>> {
    const normalizedSyncId = syncId.trim();
    if (normalizedSyncId.length === 0) {
      throw new Error("Actual budget sync ID is required");
    }

    this.#assertAcceptingWork();
    return enqueueActualApiOperation(() =>
      this.#withTimeout(async () => {
        await this.#initialize();
        const canReuseLoadedBudget =
          this.#loadedBudgetSyncId === normalizedSyncId &&
          !this.#loadedBudgetWasOpenedWithPassword &&
          options.encryptionPassword === undefined;
        if (canReuseLoadedBudget) {
          this.#lastSyncEvent = undefined;
          await this.#runClientOperation(() => this.client.sync());
          const event = this.#readLastSyncEvent();
          const changed =
            this.#dirtyBudgetSyncIds.has(normalizedSyncId) ||
            event?.type !== "success" ||
            !Array.isArray(event.tables) ||
            event.tables.length > 0;
          const writeRevision =
            this.#budgetWriteRevisions.get(normalizedSyncId) ?? 0;
          const value = await this.#executeOperation(
            () => operation(changed),
            options.synchronizeAfterWrite,
            () => this.#markBudgetDirty(normalizedSyncId),
          );
          if (
            consumeDirtyProjection &&
            changed &&
            (this.#budgetWriteRevisions.get(normalizedSyncId) ?? 0) ===
              writeRevision
          ) {
            this.#dirtyBudgetSyncIds.delete(normalizedSyncId);
          }
          return { changed, value };
        } else {
          this.#loadedBudgetSyncId = undefined;
          this.#loadedBudgetWasOpenedWithPassword = false;
          const encryptionOptions =
            options.encryptionPassword === undefined
              ? undefined
              : { password: options.encryptionPassword };
          await this.#runClientOperation(() =>
            this.client.downloadBudget(normalizedSyncId, encryptionOptions),
          );
          this.#loadedBudgetSyncId = normalizedSyncId;
          this.#loadedBudgetWasOpenedWithPassword =
            options.encryptionPassword !== undefined;
        }
        const writeRevision =
          this.#budgetWriteRevisions.get(normalizedSyncId) ?? 0;
        const value = await this.#executeOperation(
          () => operation(true),
          options.synchronizeAfterWrite,
          () => this.#markBudgetDirty(normalizedSyncId),
        );
        if (
          consumeDirtyProjection &&
          (this.#budgetWriteRevisions.get(normalizedSyncId) ?? 0) ===
            writeRevision
        ) {
          this.#dirtyBudgetSyncIds.delete(normalizedSyncId);
        }
        return { changed: true, value };
      }),
    );
  }

  #markBudgetDirty(syncId: string): void {
    this.#dirtyBudgetSyncIds.add(syncId);
    this.#budgetWriteRevisions.set(
      syncId,
      (this.#budgetWriteRevisions.get(syncId) ?? 0) + 1,
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
      this.#loadedBudgetSyncId = undefined;
      this.#loadedBudgetWasOpenedWithPassword = false;
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
    const principalId = getActualPrincipalId();
    if (principalId && this.principalSessions) {
      await this.#selectPrincipal(principalId);
    } else if (this.#activePrincipalId !== undefined) {
      await this.#shutdownActivePrincipal();
      this.#config = { ...this.#baseConfig };
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

    if (!this.#hasCredential()) {
      this.#readinessErrorMessage = "Sign in to Actual to load budgets.";
      return;
    }

    activeActualSession = this.#sessionId;

    try {
      const initialized = await this.client.init(this.#clientConfig());

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
      this.#attachSyncEventListener(initialized);
      this.#readinessErrorMessage = undefined;
    } catch (error) {
      const reportedError = this.#mapCredentialFailure(error);
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
          primaryError: reportedError,
          secondaryError: cleanupError,
        });
      } else {
        activeActualSession = undefined;
        this.#activePrincipalId = undefined;
      }
      throw reportedError;
    }
  }

  async #selectPrincipal(principalId: string): Promise<void> {
    if (this.#activePrincipalId === principalId) return;
    const [sessionToken, dataDir] = await Promise.all([
      this.principalSessions?.sessionToken(principalId),
      this.principalSessions?.dataDir(principalId),
    ]);
    if (!sessionToken) throw new ActualPrincipalSessionExpiredError();
    if (!dataDir) throw new Error("Actual account data directory is missing");
    if (activeActualSession === this.#sessionId) {
      await this.#shutdownActivePrincipal();
    }
    this.#config = {
      serverURL: this.#baseConfig.serverURL,
      dataDir,
      sessionToken,
    };
    this.#activePrincipalId = principalId;
  }

  async #shutdownActivePrincipal(): Promise<void> {
    await this.#withTimeout(() => this.client.shutdown());
    activeActualSession = undefined;
    this.#activePrincipalId = undefined;
    this.#loadedBudgetSyncId = undefined;
    this.#loadedBudgetWasOpenedWithPassword = false;
  }

  #attachSyncEventListener(initialized: unknown): void {
    if (this.#syncEventListenerAttached || !isRecord(initialized)) return;
    const on = initialized.on;
    if (typeof on !== "function") return;
    (on as (eventName: string, listener: (event: unknown) => void) => void)(
      "sync-event",
      (event) => {
        if (!isRecord(event) || typeof event.type !== "string") return;
        const tables =
          Array.isArray(event.tables) &&
          event.tables.every((table) => typeof table === "string")
            ? (event.tables as string[])
            : undefined;
        this.#lastSyncEvent = {
          type: event.type,
          ...(tables === undefined ? {} : { tables }),
        };
      },
    );
    this.#syncEventListenerAttached = true;
  }

  #readLastSyncEvent(): ActualSyncEvent | undefined {
    return this.#lastSyncEvent;
  }

  async #executeOperation<T>(
    operation: () => Promise<T>,
    synchronizeAfterWrite: BudgetOperationOptions["synchronizeAfterWrite"] = false,
    markDirty?: () => void,
  ): Promise<T> {
    let result: T;
    let operationFailed = false;
    let operationError: unknown;
    try {
      result = await operation();
    } catch (error) {
      operationFailed = true;
      operationError = this.#mapCredentialFailure(error);
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
      markDirty?.();
      try {
        await this.client.sync();
      } catch (syncError) {
        const reportedSyncError = this.#mapCredentialFailure(syncError);
        if (!operationFailed) {
          throw new ActualPostWriteSyncError(result!, reportedSyncError);
        }
        this.#reportSecondaryFailure({
          phase: "post-write-sync",
          primaryError: operationError,
          secondaryError: reportedSyncError,
        });
      }
    }

    if (operationFailed) {
      throw operationError;
    }
    return result!;
  }

  async #runClientOperation<T>(operation: () => Promise<T>): Promise<T> {
    try {
      return await operation();
    } catch (error) {
      throw this.#mapCredentialFailure(error);
    }
  }

  #mapCredentialFailure(error: unknown): unknown {
    if (
      this.#config.sessionToken === undefined ||
      !isActualAuthenticationFailure(error)
    ) {
      return error;
    }
    if (this.#activePrincipalId !== undefined) {
      return new ActualPrincipalSessionExpiredError();
    }
    this.#readinessErrorMessage = ACTUAL_SESSION_TOKEN_REJECTED_MESSAGE;
    if (!this.#sessionTokenErrorLogged) {
      this.#sessionTokenErrorLogged = true;
      console.error(ACTUAL_SESSION_TOKEN_REJECTED_MESSAGE);
    }
    return new ActualSessionTokenRejectedError();
  }

  #hasCredential(): boolean {
    return Boolean(this.#config.password || this.#config.sessionToken);
  }

  #clientConfig(): ActualConnectionConfig {
    if (this.#config.password && !this.#config.sessionToken) {
      return {
        serverURL: this.#config.serverURL,
        dataDir: this.#config.dataDir,
        ...(this.#config.verbose === undefined
          ? {}
          : { verbose: this.#config.verbose }),
        password: this.#config.password,
      };
    }
    if (this.#config.sessionToken && !this.#config.password) {
      return {
        serverURL: this.#config.serverURL,
        dataDir: this.#config.dataDir,
        ...(this.#config.verbose === undefined
          ? {}
          : { verbose: this.#config.verbose }),
        sessionToken: this.#config.sessionToken,
      };
    }
    throw new Error("Actual sign-in is required before accessing budgets");
  }

  #reportSecondaryFailure(failure: ActualSecondaryFailure): void {
    try {
      this.onSecondaryFailure(failure);
    } catch {
      // An observability callback must never replace the financial operation error.
    }
  }
}

function isActualAuthenticationFailure(error: unknown): boolean {
  if (!isRecord(error)) return false;
  const status = error.status ?? error.statusCode;
  const reason = error.reason ?? error.code;
  const message = typeof error.message === "string" ? error.message : "";
  const nested = isRecord(error.error) ? error.error : undefined;
  return (
    status === 401 ||
    status === 403 ||
    reason === "unauthorized" ||
    reason === "invalid-token" ||
    reason === "token-expired" ||
    nested?.reason === "unauthorized" ||
    /\b(unauthorized|token (expired|rejected|invalid))\b/i.test(message)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

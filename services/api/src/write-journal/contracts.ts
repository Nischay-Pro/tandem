export type JsonValue =
  null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

export interface WriteOperationInput {
  operationId: string;
  principalId: string;
  budgetId: string;
  idempotencyKey: string;
  entityType: string;
  action: string;
  entityId: string | null;
  baseRevision: string | null;
  baseSnapshot: JsonValue;
  patch: { [key: string]: JsonValue };
}

export type OperationStatus =
  "queued" | "applying" | "committed" | "conflict" | "uncertain" | "rejected";

export interface WriteOperation extends WriteOperationInput {
  requestHash: string;
  status: OperationStatus;
  attemptCount: number;
  errorCode: string | null;
  actualResult: JsonValue | null;
  createdAt: Date;
  updatedAt: Date;
  startedAt: Date | null;
  leaseExpiresAt: Date | null;
  completedAt: Date | null;
}

export interface EnqueueResult {
  operation: WriteOperation;
  replayed: boolean;
}

export interface ClaimResult {
  operation: WriteOperation;
  claimed: boolean;
}

export interface ConflictInput {
  conflictId: string;
  conflictType: string;
  baseSnapshot: JsonValue;
  localPatch: { [key: string]: JsonValue };
  remoteSnapshot: JsonValue;
  conflictingFields: string[];
}

export interface OperationConflict extends ConflictInput {
  operationId: string;
  createdAt: Date;
}

export interface DurableWriteJournal {
  enqueue(input: WriteOperationInput): Promise<EnqueueResult>;
  get(operationId: string): Promise<WriteOperation | undefined>;
  claim(operationId: string): Promise<ClaimResult>;
  renewLease(operationId: string): Promise<boolean>;
  markCommitted(
    operationId: string,
    actualResult: JsonValue,
  ): Promise<WriteOperation>;
  markUncertain(
    operationId: string,
    errorCode: string,
  ): Promise<WriteOperation>;
  recordConflict(
    operationId: string,
    conflict: ConflictInput,
  ): Promise<OperationConflict>;
  expireLeases(): Promise<number>;
}

export class IdempotencyKeyReuseError extends Error {
  constructor() {
    super("Idempotency key was already used for a different operation");
    this.name = "IdempotencyKeyReuseError";
  }
}

export class OperationIdReuseError extends Error {
  constructor() {
    super("Operation ID was already used by another request");
    this.name = "OperationIdReuseError";
  }
}

export class OperationNotFoundError extends Error {
  constructor(operationId: string) {
    super(`Operation ${operationId} does not exist`);
    this.name = "OperationNotFoundError";
  }
}

export class InvalidOperationTransitionError extends Error {
  constructor(operationId: string, status: OperationStatus, target: string) {
    super(
      `Operation ${operationId} cannot transition from ${status} to ${target}`,
    );
    this.name = "InvalidOperationTransitionError";
  }
}

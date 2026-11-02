import { createHash } from "node:crypto";
import type { Pool, PoolClient } from "pg";
import {
  IdempotencyKeyReuseError,
  IDEMPOTENCY_KEY_MAX_LENGTH,
  InvalidOperationInputError,
  InvalidOperationTransitionError,
  OperationIdReuseError,
  OperationNotFoundError,
  WRITE_OPERATION_LEASE_DURATION_SECONDS,
} from "./contracts.js";
import type {
  ActualWriteEvidence,
  ClaimResult,
  ConflictInput,
  DurableWriteJournal,
  EnqueueResult,
  JsonValue,
  OperationConflict,
  OperationStatus,
  WriteOperation,
  WriteOperationInput,
} from "./contracts.js";

interface OperationRow {
  operation_id: string;
  principal_id: string;
  budget_id: string;
  idempotency_key: string;
  request_hash: string;
  entity_type: string;
  action: string;
  entity_id: string | null;
  base_revision: string | null;
  base_snapshot: JsonValue;
  patch: { [key: string]: JsonValue };
  status: OperationStatus;
  attempt_count: number;
  error_code: string | null;
  actual_result: JsonValue | null;
  created_at: Date;
  updated_at: Date;
  started_at: Date | null;
  lease_expires_at: Date | null;
  completed_at: Date | null;
}

interface ConflictRow {
  conflict_id: string;
  operation_id: string;
  conflict_type: string;
  base_snapshot: JsonValue;
  local_patch: { [key: string]: JsonValue };
  remote_snapshot: JsonValue;
  conflicting_fields: string[];
  created_at: Date;
}

export class PostgresWriteJournal implements DurableWriteJournal {
  constructor(private readonly pool: Pool) {}

  async enqueue(input: WriteOperationInput): Promise<EnqueueResult> {
    validateOperationInput(input);
    const requestHash = hashJson({
      entityType: input.entityType,
      action: input.action,
      entityId: input.entityId,
      baseRevision: input.baseRevision,
      baseSnapshot: input.baseSnapshot,
      patch: input.patch,
    });

    return withTransaction(this.pool, async (client) => {
      const inserted = await client.query<OperationRow>(
        `INSERT INTO companion_operations (
          operation_id, principal_id, budget_id, idempotency_key, request_hash,
          entity_type, action, entity_id, base_revision, base_snapshot, patch, status
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10::jsonb, $11::jsonb, 'queued')
        ON CONFLICT DO NOTHING
        RETURNING *`,
        [
          input.operationId,
          input.principalId,
          input.budgetId,
          input.idempotencyKey,
          requestHash,
          input.entityType,
          input.action,
          input.entityId,
          input.baseRevision,
          JSON.stringify(input.baseSnapshot),
          JSON.stringify(input.patch),
        ],
      );
      const insertedOperation = inserted.rows[0];
      if (insertedOperation) {
        return { operation: mapOperation(insertedOperation), replayed: false };
      }

      const matches = await client.query<OperationRow>(
        `SELECT * FROM companion_operations
        WHERE (principal_id = $1 AND budget_id = $2 AND idempotency_key = $3)
          OR operation_id = $4
        FOR UPDATE`,
        [
          input.principalId,
          input.budgetId,
          input.idempotencyKey,
          input.operationId,
        ],
      );
      const byIdempotency = matches.rows.find(
        (row) =>
          row.principal_id === input.principalId &&
          row.budget_id === input.budgetId &&
          row.idempotency_key === input.idempotencyKey,
      );
      const byOperationId = matches.rows.find(
        (row) => row.operation_id === input.operationId,
      );

      if (
        byIdempotency &&
        byOperationId &&
        byIdempotency.operation_id !== byOperationId.operation_id
      ) {
        throw new OperationIdReuseError();
      }
      if (byIdempotency) {
        if (byIdempotency.request_hash !== requestHash) {
          throw new IdempotencyKeyReuseError();
        }
        return { operation: mapOperation(byIdempotency), replayed: true };
      }
      if (byOperationId) {
        throw new OperationIdReuseError();
      }
      throw new Error(
        "Operation uniqueness constraint failed without a matching row",
      );
    });
  }

  async get(operationId: string): Promise<WriteOperation | undefined> {
    validateUuid("operationId", operationId);
    const result = await this.pool.query<OperationRow>(
      "SELECT * FROM companion_operations WHERE operation_id = $1",
      [operationId],
    );
    const row = result.rows[0];
    return row ? mapOperation(row) : undefined;
  }

  async getConflict(
    operationId: string,
  ): Promise<OperationConflict | undefined> {
    validateUuid("operationId", operationId);
    const result = await this.pool.query<ConflictRow>(
      "SELECT * FROM companion_operation_conflicts WHERE operation_id = $1",
      [operationId],
    );
    const row = result.rows[0];
    return row ? mapConflict(row) : undefined;
  }

  async claim(operationId: string): Promise<ClaimResult> {
    validateUuid("operationId", operationId);
    return withTransaction(this.pool, async (client) => {
      const result = await client.query<OperationRow>(
        `UPDATE companion_operations
        SET status = 'applying',
            attempt_count = attempt_count + 1,
            error_code = NULL,
            started_at = clock_timestamp(),
            lease_expires_at = clock_timestamp() + ($2 * interval '1 second'),
            updated_at = clock_timestamp()
        WHERE operation_id = $1 AND status = 'queued'
        RETURNING *`,
        [operationId, WRITE_OPERATION_LEASE_DURATION_SECONDS],
      );
      const claimed = result.rows[0];
      if (claimed) {
        return { operation: mapOperation(claimed), claimed: true };
      }

      const existing = await selectOperation(client, operationId);
      if (!existing) {
        throw new OperationNotFoundError(operationId);
      }
      return { operation: mapOperation(existing), claimed: false };
    });
  }

  async renewLease(operationId: string): Promise<boolean> {
    validateUuid("operationId", operationId);
    const result = await this.pool.query(
      `UPDATE companion_operations
      SET lease_expires_at = clock_timestamp() + ($2 * interval '1 second'),
          updated_at = clock_timestamp()
      WHERE operation_id = $1 AND status = 'applying'
        AND lease_expires_at > clock_timestamp()`,
      [operationId, WRITE_OPERATION_LEASE_DURATION_SECONDS],
    );
    return result.rowCount === 1;
  }

  async releaseClaim(
    operationId: string,
    errorCode: string,
  ): Promise<WriteOperation> {
    validateUuid("operationId", operationId);
    validateErrorCode(errorCode);
    return withTransaction(this.pool, async (client) => {
      const result = await client.query<OperationRow>(
        `UPDATE companion_operations
        SET status = 'queued', error_code = $2, completed_at = NULL,
            lease_expires_at = NULL, updated_at = clock_timestamp()
        WHERE operation_id = $1 AND status = 'applying'
        RETURNING *`,
        [operationId, errorCode],
      );
      const released = result.rows[0];
      if (released) {
        return mapOperation(released);
      }

      const existing = await selectOperation(client, operationId);
      if (!existing) {
        throw new OperationNotFoundError(operationId);
      }
      const operation = mapOperation(existing);
      if (operation.status === "queued" && operation.errorCode === errorCode) {
        return operation;
      }
      throw new InvalidOperationTransitionError(
        operationId,
        operation.status,
        "queued",
      );
    });
  }

  async markRejected(
    operationId: string,
    errorCode: string,
  ): Promise<WriteOperation> {
    validateUuid("operationId", operationId);
    validateErrorCode(errorCode);
    return this.#finish(
      operationId,
      "rejected",
      async (client) => {
        const result = await client.query<OperationRow>(
          `UPDATE companion_operations
          SET status = 'rejected', error_code = $2,
              actual_result = NULL, completed_at = clock_timestamp(),
              updated_at = clock_timestamp(), lease_expires_at = NULL
          WHERE operation_id = $1 AND status = 'applying'
          RETURNING *`,
          [operationId, errorCode],
        );
        return result.rows[0];
      },
      (operation) => operation.errorCode === errorCode,
    );
  }

  async markCommitted(
    operationId: string,
    actualResult: JsonValue,
  ): Promise<WriteOperation> {
    validateUuid("operationId", operationId);
    const serializedResult = canonicalJson(actualResult);
    return this.#finish(
      operationId,
      "committed",
      async (client) => {
        const result = await client.query<OperationRow>(
          `UPDATE companion_operations
        SET status = 'committed', actual_result = $2::jsonb,
            error_code = NULL, completed_at = clock_timestamp(),
            updated_at = clock_timestamp(), lease_expires_at = NULL
        WHERE operation_id = $1 AND status = 'applying'
        RETURNING *`,
          [operationId, serializedResult],
        );
        return result.rows[0];
      },
      (operation) => canonicalJson(operation.actualResult) === serializedResult,
    );
  }

  async markUncertain(
    operationId: string,
    errorCode: string,
    writeEvidence?: ActualWriteEvidence,
  ): Promise<WriteOperation> {
    validateUuid("operationId", operationId);
    validateErrorCode(errorCode);
    const serializedEvidence =
      writeEvidence === undefined ? null : canonicalJson(writeEvidence);
    return this.#finish(
      operationId,
      "uncertain",
      async (client) => {
        const result = await client.query<OperationRow>(
          `UPDATE companion_operations
        SET status = 'uncertain', error_code = $2,
            actual_result = COALESCE($3::jsonb, actual_result),
            completed_at = clock_timestamp(), updated_at = clock_timestamp(),
            lease_expires_at = NULL
        WHERE operation_id = $1 AND status = 'applying'
        RETURNING *`,
          [operationId, errorCode, serializedEvidence],
        );
        return result.rows[0];
      },
      (operation) =>
        operation.errorCode === errorCode &&
        (writeEvidence === undefined ||
          canonicalJson(operation.writeEvidence) === serializedEvidence),
    );
  }

  async resolveUncertainAsCommitted(
    operationId: string,
    actualResult: JsonValue,
    writeEvidence?: ActualWriteEvidence,
  ): Promise<WriteOperation> {
    validateUuid("operationId", operationId);
    const serializedResult = canonicalJson(actualResult);
    return this.#finish(
      operationId,
      "committed",
      async (client) => {
        const existing = await selectOperation(client, operationId, true);
        if (!existing) {
          return undefined;
        }
        const operation = mapOperation(existing);
        if (operation.status === "uncertain") {
          const evidence = operation.writeEvidence ?? writeEvidence;
          if (!evidence) {
            throw new Error(
              "Cannot resolve uncertain operation without Actual write evidence",
            );
          }
          if (!writeEvidenceMatchesAction(operation.action, evidence)) {
            throw new Error(
              "Actual write evidence does not match the operation action",
            );
          }
          if (
            operation.writeEvidence &&
            writeEvidence &&
            canonicalJson(operation.writeEvidence) !==
              canonicalJson(writeEvidence)
          ) {
            throw new Error(
              "Actual write evidence does not match stored evidence",
            );
          }
        }
        const result = await client.query<OperationRow>(
          `UPDATE companion_operations
          SET status = 'committed', actual_result = $2::jsonb,
              error_code = NULL, completed_at = clock_timestamp(),
              updated_at = clock_timestamp(), lease_expires_at = NULL
          WHERE operation_id = $1 AND status = 'uncertain'
          RETURNING *`,
          [operationId, serializedResult],
        );
        return result.rows[0];
      },
      (operation) => canonicalJson(operation.actualResult) === serializedResult,
    );
  }

  async recordConflict(
    operationId: string,
    conflict: ConflictInput,
  ): Promise<OperationConflict> {
    validateUuid("operationId", operationId);
    validateConflictInput(conflict);

    return withTransaction(this.pool, async (client) => {
      const operation = await selectOperation(client, operationId, true);
      if (!operation) {
        throw new OperationNotFoundError(operationId);
      }
      if (operation.status !== "applying" && operation.status !== "conflict") {
        throw new InvalidOperationTransitionError(
          operationId,
          operation.status,
          "conflict",
        );
      }

      if (operation.status === "applying") {
        await client.query(
          `INSERT INTO companion_operation_conflicts (
            conflict_id, operation_id, conflict_type, base_snapshot,
            local_patch, remote_snapshot, conflicting_fields
          ) VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, $6::jsonb, $7)
          ON CONFLICT (operation_id) DO NOTHING`,
          [
            conflict.conflictId,
            operationId,
            conflict.conflictType,
            JSON.stringify(conflict.baseSnapshot),
            JSON.stringify(conflict.localPatch),
            JSON.stringify(conflict.remoteSnapshot),
            conflict.conflictingFields,
          ],
        );
      }

      const conflictResult = await client.query<ConflictRow>(
        "SELECT * FROM companion_operation_conflicts WHERE operation_id = $1 FOR UPDATE",
        [operationId],
      );
      const storedConflict = conflictResult.rows[0];
      if (!storedConflict) {
        throw new Error("Conflict record was not persisted");
      }
      if (!sameConflict(storedConflict, conflict)) {
        throw new Error("Operation already has a different conflict record");
      }

      if (operation.status === "applying") {
        await client.query(
          `UPDATE companion_operations
          SET status = 'conflict', error_code = 'conflict-detected',
              completed_at = clock_timestamp(), updated_at = clock_timestamp(),
              lease_expires_at = NULL
          WHERE operation_id = $1`,
          [operationId],
        );
      }
      return mapConflict(storedConflict);
    });
  }

  async expireLeases(): Promise<number> {
    const result = await this.pool.query(
      `UPDATE companion_operations
      SET status = 'uncertain', error_code = 'claim-lease-expired',
          completed_at = clock_timestamp(), updated_at = clock_timestamp(),
          lease_expires_at = NULL
      WHERE status = 'applying' AND lease_expires_at <= clock_timestamp()`,
    );
    return result.rowCount ?? 0;
  }

  async #finish(
    operationId: string,
    targetStatus: "committed" | "uncertain" | "rejected",
    transition: (client: PoolClient) => Promise<OperationRow | undefined>,
    isSameTerminalState: (operation: WriteOperation) => boolean,
  ): Promise<WriteOperation> {
    return withTransaction(this.pool, async (client) => {
      const updated = await transition(client);
      if (updated) {
        return mapOperation(updated);
      }

      const existing = await selectOperation(client, operationId);
      if (!existing) {
        throw new OperationNotFoundError(operationId);
      }
      const operation = mapOperation(existing);
      if (operation.status === targetStatus && isSameTerminalState(operation)) {
        return operation;
      }
      throw new InvalidOperationTransitionError(
        operationId,
        operation.status,
        targetStatus,
      );
    });
  }
}

async function withTransaction<T>(
  pool: Pool,
  work: (client: PoolClient) => Promise<T>,
): Promise<T> {
  const client = await pool.connect();
  let clientReleaseError: Error | true | undefined;
  try {
    await client.query("BEGIN");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (primaryError) {
    try {
      await client.query("ROLLBACK");
    } catch (rollbackError) {
      clientReleaseError =
        rollbackError instanceof Error ? rollbackError : true;
      reportRollbackFailure(rollbackError);
    }
    throw primaryError;
  } finally {
    client.release(clientReleaseError);
  }
}

function reportRollbackFailure(error: unknown): void {
  try {
    console.error("PostgreSQL transaction rollback failed; client discarded", {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // Reporting a cleanup failure must not replace the transaction error.
  }
}

async function selectOperation(
  client: PoolClient,
  operationId: string,
  forUpdate = false,
): Promise<OperationRow | undefined> {
  const result = await client.query<OperationRow>(
    `SELECT * FROM companion_operations WHERE operation_id = $1${forUpdate ? " FOR UPDATE" : ""}`,
    [operationId],
  );
  return result.rows[0];
}

function mapOperation(row: OperationRow): WriteOperation {
  // For uncertain rows, actual_result stores the tagged write receipt in the
  // existing JSON column so this point release does not add a migration.
  const writeEvidence =
    row.status === "uncertain" ? parseWriteEvidence(row.actual_result) : null;
  return {
    operationId: row.operation_id,
    principalId: row.principal_id,
    budgetId: row.budget_id,
    idempotencyKey: row.idempotency_key,
    requestHash: row.request_hash,
    entityType: row.entity_type,
    action: row.action,
    entityId: row.entity_id,
    baseRevision: row.base_revision,
    baseSnapshot: row.base_snapshot,
    patch: row.patch,
    status: row.status,
    attemptCount: row.attempt_count,
    errorCode: row.error_code,
    actualResult: writeEvidence ? null : row.actual_result,
    writeEvidence,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    startedAt: row.started_at,
    leaseExpiresAt: row.lease_expires_at,
    completedAt: row.completed_at,
  };
}

function writeEvidenceMatchesAction(
  action: string,
  evidence: ActualWriteEvidence,
): boolean {
  return (
    (action === "create" && evidence.kind === "transaction-create-returned") ||
    (action === "update" && evidence.kind === "transaction-update-returned") ||
    (action === "delete" && evidence.kind === "transaction-delete-returned")
  );
}

function parseWriteEvidence(
  value: JsonValue | null,
): ActualWriteEvidence | null {
  if (value === null || Array.isArray(value) || typeof value !== "object") {
    return null;
  }
  const record = value as { [key: string]: JsonValue };
  if (record.kind === "transaction-create-returned") {
    return { kind: "transaction-create-returned" };
  }
  if (record.kind === "transaction-delete-returned") {
    return { kind: "transaction-delete-returned" };
  }
  const appliedPatch = record.appliedPatch;
  if (
    record.kind === "transaction-update-returned" &&
    appliedPatch !== null &&
    !Array.isArray(appliedPatch) &&
    typeof appliedPatch === "object"
  ) {
    return {
      kind: "transaction-update-returned",
      appliedPatch: appliedPatch as { [key: string]: JsonValue },
    };
  }
  return null;
}

function mapConflict(row: ConflictRow): OperationConflict {
  return {
    conflictId: row.conflict_id,
    operationId: row.operation_id,
    conflictType: row.conflict_type,
    baseSnapshot: row.base_snapshot,
    localPatch: row.local_patch,
    remoteSnapshot: row.remote_snapshot,
    conflictingFields: row.conflicting_fields,
    createdAt: row.created_at,
  };
}

function validateOperationInput(input: WriteOperationInput): void {
  try {
    validateUuid("operationId", input.operationId);
    validateRequiredText("principalId", input.principalId, 200);
    validateRequiredText("budgetId", input.budgetId, 500);
    validateRequiredText(
      "idempotencyKey",
      input.idempotencyKey,
      IDEMPOTENCY_KEY_MAX_LENGTH,
    );
    validateRequiredText("entityType", input.entityType, 100);
    validateRequiredText("action", input.action, 100);
    if (input.entityId !== null) {
      validateRequiredText("entityId", input.entityId, 500);
    }
    if (input.baseRevision !== null) {
      validateRequiredText("baseRevision", input.baseRevision, 500);
    }
    assertJsonValue(input.baseSnapshot);
    validateJsonObject("patch", input.patch);
  } catch (error) {
    throw new InvalidOperationInputError(
      error instanceof Error
        ? error.message
        : "Write operation input is invalid",
      { cause: error },
    );
  }
}

function validateConflictInput(conflict: ConflictInput): void {
  validateUuid("conflictId", conflict.conflictId);
  validateRequiredText("conflictType", conflict.conflictType, 100);
  assertJsonValue(conflict.baseSnapshot);
  validateJsonObject("localPatch", conflict.localPatch);
  assertJsonValue(conflict.remoteSnapshot);
  if (!Array.isArray(conflict.conflictingFields)) {
    throw new Error("conflictingFields must be an array of field names");
  }
  for (const field of conflict.conflictingFields) {
    validateRequiredText("conflicting field", field, 200);
  }
}

function validateRequiredText(
  name: string,
  value: string,
  maxLength: number,
): void {
  if (value.includes("\u0000")) {
    throw new Error(`${name} must not contain NUL characters`);
  }
  if (value.trim().length === 0 || [...value].length > maxLength) {
    throw new Error(
      `${name} must contain between 1 and ${maxLength} characters`,
    );
  }
}

function validateUuid(name: string, value: string): void {
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    )
  ) {
    throw new Error(`${name} must be a UUID`);
  }
}

function validateErrorCode(value: string): void {
  if (!/^[a-z][a-z0-9-]{0,99}$/.test(value)) {
    throw new Error("errorCode must be a lowercase identifier");
  }
}

function validateJsonObject(
  name: string,
  value: { [key: string]: JsonValue },
): void {
  if (value === null || Array.isArray(value) || typeof value !== "object") {
    throw new Error(`${name} must be a JSON object`);
  }
  assertJsonValue(value);
}

function assertJsonValue(value: JsonValue): void {
  canonicalJson(value);
}

function hashJson(value: JsonValue): string {
  return createHash("sha256").update(canonicalJson(value)).digest("hex");
}

function canonicalJson(value: JsonValue): string {
  if (
    value === null ||
    typeof value === "boolean" ||
    typeof value === "string"
  ) {
    if (typeof value === "string" && value.includes("\u0000")) {
      throw new Error(
        "JSON strings and object keys must not contain NUL characters",
      );
    }
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      throw new Error("JSON numbers must be finite");
    }
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => canonicalJson(item)).join(",")}]`;
  }
  if (typeof value === "object") {
    const entries = Object.keys(value)
      .sort()
      .map((key) => {
        if (key.includes("\u0000")) {
          throw new Error(
            "JSON strings and object keys must not contain NUL characters",
          );
        }
        return `${JSON.stringify(key)}:${canonicalJson(value[key]!)}`;
      });
    return `{${entries.join(",")}}`;
  }
  throw new Error("Value is not valid JSON");
}

function sameConflict(row: ConflictRow, input: ConflictInput): boolean {
  return (
    row.conflict_type === input.conflictType &&
    canonicalJson(row.base_snapshot) === canonicalJson(input.baseSnapshot) &&
    canonicalJson(row.local_patch) === canonicalJson(input.localPatch) &&
    canonicalJson(row.remote_snapshot) ===
      canonicalJson(input.remoteSnapshot) &&
    canonicalJson(row.conflicting_fields) ===
      canonicalJson(input.conflictingFields)
  );
}

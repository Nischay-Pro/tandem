import { randomUUID } from "node:crypto";
import {
  BUDGET_ENTITY_TYPES,
  BudgetProjectionInputError,
  type BudgetEntityType,
  type BudgetProjectionChangePage,
  type BudgetProjectionRecord,
  type BudgetProjectionSnapshot,
  type BudgetProjectionSnapshotPage,
  type BudgetProjectionSnapshotPageRequest,
  type BudgetProjectionStore,
} from "./contracts.js";

const MAX_CACHED_BUDGETS = 2;
const MAX_PAGE_SIZE = 2_000;

interface CachedBudget {
  feedEpoch: string;
  generation: string;
  records: BudgetProjectionRecord[];
  refreshedAt: number;
}

/**
 * Keeps the latest Actual snapshot in process memory for bounded pagination.
 * A changed snapshot advances the generation, so phones replace their remote
 * rows from a fresh snapshot instead of relying on a second persisted change
 * feed. Restarting the API intentionally drops this cache and forces a resync.
 */
export class InMemoryBudgetProjectionStore implements BudgetProjectionStore {
  private readonly budgets = new Map<string, CachedBudget>();

  constructor(private readonly maxCachedBudgets = MAX_CACHED_BUDGETS) {
    if (!Number.isSafeInteger(maxCachedBudgets) || maxCachedBudgets < 1) {
      throw new Error("Cached budget count must be a positive integer");
    }
  }

  async isSnapshotFresh(
    budgetId: string,
    feedEpoch: string,
    maxAgeMs: number,
  ): Promise<boolean> {
    validateBudgetId(budgetId);
    validateFeedEpoch(feedEpoch);
    if (!Number.isSafeInteger(maxAgeMs) || maxAgeMs < 1) {
      throw new BudgetProjectionInputError(
        "maxAgeMs must be a positive integer",
      );
    }
    const cached = this.budgets.get(budgetId);
    return (
      cached !== undefined &&
      cached.feedEpoch === feedEpoch &&
      Date.now() - cached.refreshedAt < maxAgeMs
    );
  }

  async applySnapshot(
    budgetId: string,
    feedEpoch: string,
    records: readonly BudgetProjectionRecord[],
  ): Promise<BudgetProjectionSnapshot> {
    validateBudgetId(budgetId);
    validateFeedEpoch(feedEpoch);
    const sortedRecords = sortAndValidateRecords(records);
    const previous = this.budgets.get(budgetId);
    const unchanged =
      previous !== undefined &&
      previous.feedEpoch === feedEpoch &&
      sameRecords(previous.records, sortedRecords);
    const cached: CachedBudget = {
      feedEpoch,
      generation: unchanged && previous ? previous.generation : randomUUID(),
      records: unchanged && previous ? previous.records : sortedRecords,
      refreshedAt: Date.now(),
    };
    this.touch(budgetId, cached);
    return toSnapshot(budgetId, cached);
  }

  async readSnapshot(
    budgetId: string,
  ): Promise<BudgetProjectionSnapshot | undefined> {
    const cached = this.touchExisting(budgetId);
    return cached ? toSnapshot(budgetId, cached) : undefined;
  }

  async readSnapshotPage(
    request: BudgetProjectionSnapshotPageRequest,
  ): Promise<BudgetProjectionSnapshotPage | undefined> {
    validateBudgetId(request.budgetId);
    validateGeneration(request.generation);
    const cursor = parseCursor(request.cursor, "snapshot cursor");
    validatePageRequest(request);
    const cached = this.touchExisting(request.budgetId);
    if (!cached) return undefined;
    if (cached.generation !== request.generation || cursor !== 0) {
      return resetSnapshotPage(request.budgetId, cached);
    }

    const start =
      request.afterType === undefined
        ? 0
        : firstRecordAfter(cached.records, request.afterType, request.afterId!);
    const end = Math.min(start + request.limit, cached.records.length);
    const records = cached.records.slice(start, end).map(cloneRecord);
    const hasMore = end < cached.records.length;
    const last = records.at(-1);
    return {
      budgetId: request.budgetId,
      generation: cached.generation,
      cursor: "0",
      records,
      hasMore,
      resetRequired: false,
      nextAfterType: hasMore ? last!.type : null,
      nextAfterId: hasMore ? last!.id : null,
    };
  }

  async readChanges(input: {
    budgetId: string;
    generation: string;
    cursor: string;
    throughCursor?: string;
    limit: number;
  }): Promise<BudgetProjectionChangePage | undefined> {
    validateBudgetId(input.budgetId);
    validateGeneration(input.generation);
    const cursor = parseCursor(input.cursor, "change cursor");
    if (!Number.isSafeInteger(input.limit) || input.limit < 1) {
      throw new BudgetProjectionInputError("limit must be a positive integer");
    }
    const throughCursor =
      input.throughCursor === undefined
        ? undefined
        : parseCursor(input.throughCursor, "through cursor");
    if (throughCursor !== undefined && throughCursor < cursor) {
      throw new BudgetProjectionInputError(
        "throughCursor must be greater than or equal to cursor",
      );
    }

    const cached = this.touchExisting(input.budgetId);
    if (!cached) return undefined;
    if (
      cached.generation !== input.generation ||
      cursor !== 0 ||
      (throughCursor !== undefined && throughCursor !== 0)
    ) {
      return {
        budgetId: input.budgetId,
        generation: cached.generation,
        currentCursor: "0",
        throughCursor: "0",
        nextCursor: "0",
        hasMore: false,
        resetRequired: true,
        changes: [],
      };
    }

    return {
      budgetId: input.budgetId,
      generation: cached.generation,
      currentCursor: "0",
      throughCursor: "0",
      nextCursor: "0",
      hasMore: false,
      resetRequired: false,
      changes: [],
    };
  }

  private touchExisting(budgetId: string): CachedBudget | undefined {
    const cached = this.budgets.get(budgetId);
    if (!cached) return undefined;
    this.budgets.delete(budgetId);
    this.budgets.set(budgetId, cached);
    return cached;
  }

  private touch(budgetId: string, cached: CachedBudget): void {
    this.budgets.delete(budgetId);
    this.budgets.set(budgetId, cached);
    while (this.budgets.size > this.maxCachedBudgets) {
      const oldestBudgetId = this.budgets.keys().next().value!;
      this.budgets.delete(oldestBudgetId);
    }
  }
}

function validateBudgetId(budgetId: string): void {
  if (
    typeof budgetId !== "string" ||
    budgetId.length < 1 ||
    budgetId.length > 500
  ) {
    throw new BudgetProjectionInputError("budgetId is invalid");
  }
}

function validateFeedEpoch(feedEpoch: string): void {
  if (!/^[A-Za-z0-9._:-]{1,100}$/.test(feedEpoch)) {
    throw new BudgetProjectionInputError("feedEpoch is invalid");
  }
}

function validateGeneration(generation: string): void {
  if (
    typeof generation !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      generation,
    )
  ) {
    throw new BudgetProjectionInputError("generation is invalid");
  }
}

function parseCursor(cursor: string, label: string): number {
  if (!/^(0|[1-9][0-9]*)$/.test(cursor)) {
    throw new BudgetProjectionInputError(`${label} is invalid`);
  }
  const parsed = Number(cursor);
  if (!Number.isSafeInteger(parsed)) {
    throw new BudgetProjectionInputError(`${label} is invalid`);
  }
  return parsed;
}

function validatePageRequest(
  request: BudgetProjectionSnapshotPageRequest,
): void {
  if (
    !Number.isSafeInteger(request.limit) ||
    request.limit < 1 ||
    request.limit > MAX_PAGE_SIZE
  ) {
    throw new BudgetProjectionInputError(
      `limit must be between 1 and ${MAX_PAGE_SIZE}`,
    );
  }
  if ((request.afterType === undefined) !== (request.afterId === undefined)) {
    throw new BudgetProjectionInputError(
      "afterType and afterId must be provided together",
    );
  }
  if (request.afterType !== undefined) {
    if (
      !(BUDGET_ENTITY_TYPES as readonly string[]).includes(request.afterType)
    ) {
      throw new BudgetProjectionInputError("afterType is invalid");
    }
    if (request.afterId!.length < 1 || request.afterId!.length > 500) {
      throw new BudgetProjectionInputError("afterId is invalid");
    }
  }
}

function sortAndValidateRecords(
  records: readonly BudgetProjectionRecord[],
): BudgetProjectionRecord[] {
  const sorted = [...records].map(cloneRecord).sort(compareRecords);
  for (let index = 0; index < sorted.length; index += 1) {
    const record = sorted[index]!;
    if (
      !(BUDGET_ENTITY_TYPES as readonly string[]).includes(record.type) ||
      record.id.length < 1 ||
      record.id.length > 500 ||
      !/^[a-f0-9]{64}$/.test(record.revision) ||
      record.body === null ||
      typeof record.body !== "object" ||
      Array.isArray(record.body)
    ) {
      throw new BudgetProjectionInputError("snapshot record is invalid");
    }
    const previous = sorted[index - 1];
    if (previous && compareRecords(previous, record) === 0) {
      throw new BudgetProjectionInputError(
        "snapshot records must have unique type and id pairs",
      );
    }
  }
  return sorted;
}

function sameRecords(
  left: readonly BudgetProjectionRecord[],
  right: readonly BudgetProjectionRecord[],
): boolean {
  return (
    left.length === right.length &&
    left.every(
      (record, index) =>
        record.revision === right[index]?.revision &&
        compareRecords(record, right[index]!) === 0,
    )
  );
}

function compareRecords(
  left: Pick<BudgetProjectionRecord, "type" | "id">,
  right: Pick<BudgetProjectionRecord, "type" | "id">,
): number {
  const typeComparison = compareUtf8(left.type, right.type);
  return typeComparison === 0 ? compareUtf8(left.id, right.id) : typeComparison;
}

function compareUtf8(left: string, right: string): number {
  return Buffer.compare(Buffer.from(left, "utf8"), Buffer.from(right, "utf8"));
}

function firstRecordAfter(
  records: readonly BudgetProjectionRecord[],
  type: BudgetEntityType,
  id: string,
): number {
  let low = 0;
  let high = records.length;
  while (low < high) {
    const middle = low + Math.floor((high - low) / 2);
    if (compareRecords(records[middle]!, { type, id }) <= 0) {
      low = middle + 1;
    } else {
      high = middle;
    }
  }
  return low;
}

function cloneRecord(record: BudgetProjectionRecord): BudgetProjectionRecord {
  return structuredClone(record);
}

function toSnapshot(
  budgetId: string,
  cached: CachedBudget,
): BudgetProjectionSnapshot {
  return {
    budgetId,
    generation: cached.generation,
    cursor: "0",
    records: cached.records.map(cloneRecord),
  };
}

function resetSnapshotPage(
  budgetId: string,
  cached: CachedBudget,
): BudgetProjectionSnapshotPage {
  return {
    budgetId,
    generation: cached.generation,
    cursor: "0",
    records: [],
    hasMore: false,
    resetRequired: true,
    nextAfterType: null,
    nextAfterId: null,
  };
}

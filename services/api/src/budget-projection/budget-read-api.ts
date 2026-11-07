import type {
  BudgetDisplaySettings,
  BudgetLockPort,
  BudgetProjectionChangePage,
  BudgetProjectionRecord,
  BudgetProjectionSnapshot,
  BudgetProjectionSnapshotPage,
  BudgetProjectionStore,
  BudgetEntityType,
  BudgetSnapshotReader,
} from "./contracts.js";
import { BudgetProjectionInputError } from "./contracts.js";

export const DEFAULT_BUDGET_SNAPSHOT_FRESHNESS_MS = 15_000;

export interface BudgetChangeRequest {
  budgetId: string;
  generation: string;
  cursor: string;
  throughCursor?: string;
  limit: number;
  encryptionPassword?: string;
}

export interface BudgetSnapshotPageRequest {
  budgetId: string;
  generation?: string;
  cursor?: string;
  afterType?: BudgetEntityType;
  afterId?: string;
  limit: number;
  encryptionPassword?: string;
}

export interface BudgetCatalogEntry {
  budgetId: string;
  name: string;
  isEncrypted: boolean;
}

export interface BudgetReadApiPort {
  listBudgets?: (() => Promise<BudgetCatalogEntry[]>) | undefined;
  getSnapshot(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetProjectionSnapshot>;
  getChanges(request: BudgetChangeRequest): Promise<BudgetProjectionChangePage>;
  getSnapshotPage(
    request: BudgetSnapshotPageRequest,
  ): Promise<BudgetProjectionSnapshotPage>;
}

export class BudgetReadApi implements BudgetReadApiPort {
  readonly listBudgets: (() => Promise<BudgetCatalogEntry[]>) | undefined;

  constructor(
    private readonly locks: BudgetLockPort,
    private readonly reader: BudgetSnapshotReader,
    private readonly store: BudgetProjectionStore,
    private readonly feedEpoch: string,
    listBudgets?: () => Promise<BudgetCatalogEntry[]>,
    private readonly snapshotFreshnessMs = DEFAULT_BUDGET_SNAPSHOT_FRESHNESS_MS,
  ) {
    this.listBudgets = listBudgets;
    if (!/^[A-Za-z0-9._:-]{1,100}$/.test(feedEpoch)) {
      throw new Error("Change feed epoch is invalid");
    }
    if (!Number.isSafeInteger(snapshotFreshnessMs) || snapshotFreshnessMs < 1) {
      throw new Error("Snapshot freshness interval must be a positive integer");
    }
  }

  getSnapshot(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetProjectionSnapshot> {
    return this.locks.withBudgetLock(budgetId, async () => {
      let result = await this.readSnapshot(budgetId, encryptionPassword);
      if (result.unchanged) {
        const cached = await this.store.readSnapshot(budgetId);
        if (cached)
          return { ...cached, displaySettings: result.displaySettings };
        result = await this.readSnapshot(budgetId, encryptionPassword, true);
      }
      const snapshot = await this.store.applySnapshot(
        budgetId,
        this.feedEpoch,
        result.records,
      );
      return { ...snapshot, displaySettings: result.displaySettings };
    });
  }

  getSnapshotPage(
    request: BudgetSnapshotPageRequest,
  ): Promise<BudgetProjectionSnapshotPage> {
    return this.locks.withBudgetLock(request.budgetId, async () => {
      const hasGeneration = request.generation !== undefined;
      const hasCursor = request.cursor !== undefined;
      if (hasGeneration !== hasCursor) {
        throw new BudgetProjectionInputError(
          "generation and cursor must be provided together",
        );
      }
      if (
        (request.afterType === undefined) !==
        (request.afterId === undefined)
      ) {
        throw new BudgetProjectionInputError(
          "afterType and afterId must be provided together",
        );
      }

      let generation = request.generation;
      let cursor = request.cursor;
      let displaySettings: BudgetDisplaySettings | null = null;
      if (generation === undefined || cursor === undefined) {
        let result = await this.readSnapshot(
          request.budgetId,
          request.encryptionPassword,
        );
        displaySettings = result.displaySettings;
        const cached = result.unchanged
          ? await this.store.readSnapshot(request.budgetId)
          : undefined;
        if (result.unchanged && !cached) {
          result = await this.readSnapshot(
            request.budgetId,
            request.encryptionPassword,
            true,
          );
          displaySettings = result.displaySettings;
        }
        if (cached) {
          generation = cached.generation;
          cursor = cached.cursor;
        } else if (this.store.applySnapshotForRefresh) {
          const checkpoint = await this.store.applySnapshotForRefresh(
            request.budgetId,
            this.feedEpoch,
            result.records,
          );
          generation = checkpoint.generation;
          cursor = checkpoint.cursor;
        } else {
          const snapshot = await this.store.applySnapshot(
            request.budgetId,
            this.feedEpoch,
            result.records,
          );
          generation = snapshot.generation;
          cursor = snapshot.cursor;
        }
      }

      const page = await this.store.readSnapshotPage({
        budgetId: request.budgetId,
        generation,
        cursor,
        ...(request.afterType === undefined
          ? {}
          : { afterType: request.afterType }),
        ...(request.afterId === undefined ? {} : { afterId: request.afterId }),
        limit: request.limit,
      });
      if (!page) throw new Error("Budget projection state was not created");
      return { ...page, displaySettings };
    });
  }

  getChanges(
    request: BudgetChangeRequest,
  ): Promise<BudgetProjectionChangePage> {
    return this.locks.withBudgetLock(request.budgetId, async () => {
      let displaySettings: BudgetDisplaySettings | null = null;
      if (request.throughCursor === undefined) {
        const isFresh = await this.store.isSnapshotFresh(
          request.budgetId,
          this.feedEpoch,
          this.snapshotFreshnessMs,
        );
        if (!isFresh) {
          displaySettings = await this.refresh(
            request.budgetId,
            request.encryptionPassword,
          );
        }
      }

      let page = await this.store.readChanges(request);
      if (page === undefined) {
        displaySettings = await this.refresh(
          request.budgetId,
          request.encryptionPassword,
        );
        page = await this.store.readChanges(request);
      }
      if (!page) {
        throw new Error("Budget change feed state was not created");
      }
      return { ...page, displaySettings };
    });
  }

  private async refresh(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetDisplaySettings | null> {
    const result = await this.readSnapshot(budgetId, encryptionPassword);
    if (result.unchanged) {
      const cached = await this.store.readSnapshot(budgetId);
      if (cached) return result.displaySettings;
      const fullResult = await this.readSnapshot(
        budgetId,
        encryptionPassword,
        true,
      );
      if (this.store.applySnapshotForRefresh) {
        await this.store.applySnapshotForRefresh(
          budgetId,
          this.feedEpoch,
          fullResult.records,
        );
      } else {
        await this.store.applySnapshot(
          budgetId,
          this.feedEpoch,
          fullResult.records,
        );
      }
      return fullResult.displaySettings;
    }
    if (this.store.applySnapshotForRefresh) {
      await this.store.applySnapshotForRefresh(
        budgetId,
        this.feedEpoch,
        result.records,
      );
    } else {
      await this.store.applySnapshot(budgetId, this.feedEpoch, result.records);
    }
    return result.displaySettings;
  }

  private async readSnapshot(
    budgetId: string,
    encryptionPassword?: string,
    forceFull = false,
  ): Promise<{
    records: BudgetProjectionRecord[];
    displaySettings: BudgetDisplaySettings | null;
    unchanged?: boolean;
  }> {
    if (forceFull && this.reader.readFullWithSettings) {
      return this.reader.readFullWithSettings(budgetId, encryptionPassword);
    }
    if (this.reader.readWithSettings) {
      return this.reader.readWithSettings(budgetId, encryptionPassword);
    }
    return {
      records: await this.reader.read(budgetId, encryptionPassword),
      displaySettings: null,
    };
  }
}

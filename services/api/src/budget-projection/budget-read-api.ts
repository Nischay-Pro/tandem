import type {
  BudgetDisplaySettings,
  BudgetLockPort,
  BudgetProjectionChangePage,
  BudgetProjectionRecord,
  BudgetProjectionSnapshot,
  BudgetProjectionStore,
  BudgetSnapshotReader,
} from "./contracts.js";

export interface BudgetChangeRequest {
  budgetId: string;
  generation: string;
  cursor: string;
  throughCursor?: string;
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
}

export class BudgetReadApi implements BudgetReadApiPort {
  readonly listBudgets: (() => Promise<BudgetCatalogEntry[]>) | undefined;

  constructor(
    private readonly locks: BudgetLockPort,
    private readonly reader: BudgetSnapshotReader,
    private readonly store: BudgetProjectionStore,
    private readonly feedEpoch: string,
    listBudgets?: () => Promise<BudgetCatalogEntry[]>,
  ) {
    this.listBudgets = listBudgets;
    if (!/^[A-Za-z0-9._:-]{1,100}$/.test(feedEpoch)) {
      throw new Error("Change feed epoch is invalid");
    }
  }

  getSnapshot(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<BudgetProjectionSnapshot> {
    return this.locks.withBudgetLock(budgetId, async () => {
      const result = await this.readSnapshot(budgetId, encryptionPassword);
      const snapshot = await this.store.applySnapshot(
        budgetId,
        this.feedEpoch,
        result.records,
      );
      return { ...snapshot, displaySettings: result.displaySettings };
    });
  }

  getChanges(
    request: BudgetChangeRequest,
  ): Promise<BudgetProjectionChangePage> {
    return this.locks.withBudgetLock(request.budgetId, async () => {
      let displaySettings: BudgetDisplaySettings | null = null;
      if (request.throughCursor === undefined) {
        displaySettings = await this.refresh(
          request.budgetId,
          request.encryptionPassword,
        );
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
    await this.store.applySnapshot(budgetId, this.feedEpoch, result.records);
    return result.displaySettings;
  }

  private async readSnapshot(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<{
    records: BudgetProjectionRecord[];
    displaySettings: BudgetDisplaySettings | null;
  }> {
    if (this.reader.readWithSettings) {
      return this.reader.readWithSettings(budgetId, encryptionPassword);
    }
    return {
      records: await this.reader.read(budgetId, encryptionPassword),
      displaySettings: null,
    };
  }
}

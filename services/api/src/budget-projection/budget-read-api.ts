import type {
  BudgetLockPort,
  BudgetProjectionChangePage,
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
      const records = await this.reader.read(budgetId, encryptionPassword);
      return this.store.applySnapshot(budgetId, this.feedEpoch, records);
    });
  }

  getChanges(
    request: BudgetChangeRequest,
  ): Promise<BudgetProjectionChangePage> {
    return this.locks.withBudgetLock(request.budgetId, async () => {
      if (request.throughCursor === undefined) {
        await this.refresh(request.budgetId, request.encryptionPassword);
      }

      let page = await this.store.readChanges(request);
      if (page === undefined) {
        await this.refresh(request.budgetId, request.encryptionPassword);
        page = await this.store.readChanges(request);
      }
      if (!page) {
        throw new Error("Budget change feed state was not created");
      }
      return page;
    });
  }

  private async refresh(
    budgetId: string,
    encryptionPassword?: string,
  ): Promise<void> {
    const records = await this.reader.read(budgetId, encryptionPassword);
    await this.store.applySnapshot(budgetId, this.feedEpoch, records);
  }
}

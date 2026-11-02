import type { Pool as PoolType } from "pg";

const MAX_CONCURRENT_BUDGET_LOCKS = 4;
const budgetLockQueues = new Map<string, Promise<void>>();

export async function withBudgetLock<T>(
  pool: PoolType,
  budgetId: string,
  work: () => Promise<T>,
): Promise<T> {
  if (budgetId.trim().length === 0) {
    throw new Error("Budget ID is required for a write lock");
  }

  return withLocalBudgetQueue(budgetId, () =>
    budgetLockSemaphore.run(async () => {
      const client = await pool.connect();
      let lockAcquired = false;
      let workFailed = false;
      let workError: unknown;
      let result: T | undefined;
      let unlockFailed = false;
      let unlockError: unknown;
      let clientMustBeDestroyed = false;
      try {
        await client.query(
          "SELECT pg_advisory_lock(hashtextextended($1, 19753))",
          [`tandem-for-actual:${budgetId}`],
        );
        lockAcquired = true;
        result = await work();
      } catch (error) {
        workFailed = true;
        workError = error;
        if (!lockAcquired) {
          // The server may have granted the session lock before the connection failed.
          clientMustBeDestroyed = true;
        }
      }

      try {
        if (lockAcquired) {
          const unlock = await client.query<{ unlocked: boolean }>(
            "SELECT pg_advisory_unlock(hashtextextended($1, 19753)) AS unlocked",
            [`tandem-for-actual:${budgetId}`],
          );
          if (unlock.rows[0]?.unlocked !== true) {
            throw new Error(
              "Could not confirm release of the budget advisory lock",
            );
          }
        }
      } catch (error) {
        unlockFailed = true;
        unlockError = error;
        clientMustBeDestroyed = true;
      } finally {
        client.release(clientMustBeDestroyed ? true : undefined);
      }

      if (workFailed && unlockFailed) {
        throw new AggregateError(
          [workError, unlockError],
          "Budget operation failed and its PostgreSQL lock could not be released",
        );
      }
      if (workFailed) {
        throw workError;
      }
      if (unlockFailed) {
        throw unlockError;
      }
      return result as T;
    }),
  );
}

async function withLocalBudgetQueue<T>(
  budgetId: string,
  work: () => Promise<T>,
): Promise<T> {
  const previous = budgetLockQueues.get(budgetId) ?? Promise.resolve();
  let releaseTurn!: () => void;
  const turn = new Promise<void>((resolve) => {
    releaseTurn = resolve;
  });
  const tail = previous.then(() => turn);
  budgetLockQueues.set(budgetId, tail);
  await previous;

  try {
    return await work();
  } finally {
    releaseTurn();
    if (budgetLockQueues.get(budgetId) === tail) {
      budgetLockQueues.delete(budgetId);
    }
  }
}

class AsyncSemaphore {
  #active = 0;
  #waiters: Array<() => void> = [];

  constructor(private readonly capacity: number) {}

  async run<T>(work: () => Promise<T>): Promise<T> {
    if (this.#active < this.capacity) {
      this.#active += 1;
    } else {
      await new Promise<void>((resolve) => this.#waiters.push(resolve));
    }

    try {
      return await work();
    } finally {
      const next = this.#waiters.shift();
      if (next) {
        next();
      } else {
        this.#active -= 1;
      }
    }
  }
}

const budgetLockSemaphore = new AsyncSemaphore(MAX_CONCURRENT_BUDGET_LOCKS);

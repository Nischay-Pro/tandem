import type { DurableWriteJournal } from "./contracts.js";

export const WRITE_LEASE_SWEEP_INTERVAL_MS = 30_000;
export const WRITE_JOURNAL_COMPACTION_INTERVAL_MS = 60 * 60 * 1_000;

type JournalMaintenance = Pick<DurableWriteJournal, "expireLeases"> &
  Pick<DurableWriteJournal, "compactTerminalPayloads">;
type MaintenanceErrorReporter = (error: unknown, activity?: string) => void;

export class WriteLeaseReaper {
  #timer: ReturnType<typeof setInterval> | undefined;
  #inFlightSweep: Promise<void> | undefined;
  #lastCompactionAt: number | undefined;

  constructor(
    private readonly journal: JournalMaintenance,
    private readonly reportError: MaintenanceErrorReporter = reportSweepFailure,
  ) {}

  async start(): Promise<void> {
    if (this.#timer) {
      return;
    }

    await this.journal.expireLeases();
    await this.#compactIfDue();
    this.#timer = setInterval(() => {
      void this.#sweepPeriodically();
    }, WRITE_LEASE_SWEEP_INTERVAL_MS);
    this.#timer.unref?.();
  }

  async stop(): Promise<void> {
    if (this.#timer) {
      clearInterval(this.#timer);
      this.#timer = undefined;
    }
    await this.#inFlightSweep;
  }

  async #sweepPeriodically(): Promise<void> {
    if (this.#inFlightSweep) {
      return this.#inFlightSweep;
    }

    const sweep = Promise.resolve()
      .then(() => this.journal.expireLeases())
      .then(() => undefined)
      .catch((error: unknown) => {
        this.#report(error, "Expired write lease sweep");
      })
      .then(() => this.#compactIfDue())
      .finally(() => {
        this.#inFlightSweep = undefined;
      });
    this.#inFlightSweep = sweep;
    return sweep;
  }

  async #compactIfDue(): Promise<void> {
    const compact = this.journal.compactTerminalPayloads;
    if (!compact) return;

    const now = Date.now();
    if (
      this.#lastCompactionAt !== undefined &&
      now - this.#lastCompactionAt < WRITE_JOURNAL_COMPACTION_INTERVAL_MS
    ) {
      return;
    }
    this.#lastCompactionAt = now;

    try {
      await compact.call(this.journal);
    } catch (error) {
      this.#report(error, "Terminal journal payload compaction");
    }
  }

  #report(error: unknown, activity: string): void {
    try {
      this.reportError(error, activity);
    } catch {
      // A logging failure must not create an unhandled timer rejection.
    }
  }
}

function reportSweepFailure(
  error: unknown,
  activity = "Expired write lease sweep",
): void {
  try {
    console.error(`${activity} failed`, {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // Reporting a periodic maintenance failure is best-effort.
  }
}

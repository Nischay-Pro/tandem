import type { DurableWriteJournal } from "./contracts.js";

export const WRITE_LEASE_SWEEP_INTERVAL_MS = 30_000;

export class WriteLeaseReaper {
  #timer: ReturnType<typeof setInterval> | undefined;
  #inFlightSweep: Promise<void> | undefined;

  constructor(
    private readonly journal: Pick<DurableWriteJournal, "expireLeases">,
    private readonly reportError: (error: unknown) => void = reportSweepFailure,
  ) {}

  async start(): Promise<void> {
    if (this.#timer) {
      return;
    }

    await this.journal.expireLeases();
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
        try {
          this.reportError(error);
        } catch {
          // A logging failure must not create an unhandled timer rejection.
        }
      })
      .finally(() => {
        this.#inFlightSweep = undefined;
      });
    this.#inFlightSweep = sweep;
    return sweep;
  }
}

function reportSweepFailure(error: unknown): void {
  try {
    console.error("Expired write lease sweep failed", {
      errorType: error instanceof Error ? error.name : typeof error,
    });
  } catch {
    // Reporting a periodic maintenance failure is best-effort.
  }
}

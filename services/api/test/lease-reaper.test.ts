import { afterEach, describe, expect, it, vi } from "vitest";
import {
  WRITE_JOURNAL_COMPACTION_INTERVAL_MS,
  WRITE_LEASE_SWEEP_INTERVAL_MS,
  WriteLeaseReaper,
} from "../src/write-journal/lease-reaper.js";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("write lease reaper", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("expires leases immediately, then periodically, and stops cleanly", async () => {
    vi.useFakeTimers();
    const expireLeases = vi.fn<() => Promise<number>>().mockResolvedValue(0);
    const reaper = new WriteLeaseReaper({ expireLeases });

    await reaper.start();
    await reaper.start();
    expect(expireLeases).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(expireLeases).toHaveBeenCalledTimes(2);

    await reaper.stop();
    await reaper.stop();
    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(expireLeases).toHaveBeenCalledTimes(2);
  });

  it("compacts terminal operation payloads on start and once per hour", async () => {
    vi.useFakeTimers();
    const expireLeases = vi.fn<() => Promise<number>>().mockResolvedValue(0);
    const compactTerminalPayloads = vi
      .fn<() => Promise<number>>()
      .mockResolvedValue(0);
    const reaper = new WriteLeaseReaper({
      expireLeases,
      compactTerminalPayloads,
    });

    await reaper.start();
    expect(compactTerminalPayloads).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(
      WRITE_JOURNAL_COMPACTION_INTERVAL_MS - WRITE_LEASE_SWEEP_INTERVAL_MS,
    );
    expect(compactTerminalPayloads).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(compactTerminalPayloads).toHaveBeenCalledTimes(2);
    await reaper.stop();
  });

  it("reports compaction errors without exposing their message", async () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const reaper = new WriteLeaseReaper({
      expireLeases: vi.fn<() => Promise<number>>().mockResolvedValue(0),
      compactTerminalPayloads: vi
        .fn<() => Promise<number>>()
        .mockRejectedValue(new Error("synthetic database detail")),
    });

    await expect(reaper.start()).resolves.toBeUndefined();
    expect(consoleError).toHaveBeenCalledWith(
      "Terminal journal payload compaction failed",
      { errorType: "Error" },
    );
    expect(consoleError.mock.calls[0]?.[1]).not.toHaveProperty("message");
    await reaper.stop();
  });

  it("coalesces overlapping sweeps and waits for one in flight during stop", async () => {
    vi.useFakeTimers();
    const pendingSweep = deferred<number>();
    const expireLeases = vi
      .fn<() => Promise<number>>()
      .mockResolvedValueOnce(0)
      .mockReturnValueOnce(pendingSweep.promise);
    const reaper = new WriteLeaseReaper({ expireLeases });
    await reaper.start();

    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(expireLeases).toHaveBeenCalledTimes(2);

    let stopped = false;
    const stopping = reaper.stop().then(() => {
      stopped = true;
    });
    await Promise.resolve();
    expect(stopped).toBe(false);

    pendingSweep.resolve(1);
    await stopping;
    expect(stopped).toBe(true);
  });

  it.each([
    { error: new Error("database unavailable"), errorType: "Error" },
    { error: "database unavailable", errorType: "string" },
  ])(
    "reports periodic sweep errors without exposing their message",
    async ({ error, errorType }) => {
      vi.useFakeTimers();
      const consoleError = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const expireLeases = vi
        .fn<() => Promise<number>>()
        .mockResolvedValueOnce(0)
        .mockRejectedValueOnce(error);
      const reaper = new WriteLeaseReaper({ expireLeases });
      await reaper.start();

      await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);

      expect(consoleError).toHaveBeenCalledWith(
        "Expired write lease sweep failed",
        {
          errorType,
        },
      );
      expect(consoleError.mock.calls[0]?.[1]).not.toHaveProperty("message");
      await reaper.stop();
    },
  );

  it("swallows a periodic error-reporting failure", async () => {
    vi.useFakeTimers();
    const expireLeases = vi
      .fn<() => Promise<number>>()
      .mockResolvedValueOnce(0)
      .mockRejectedValueOnce(new Error("database unavailable"));
    const reportError = vi.fn(() => {
      throw new Error("logger unavailable");
    });
    const reaper = new WriteLeaseReaper({ expireLeases }, reportError);
    await reaper.start();

    await vi.advanceTimersByTimeAsync(WRITE_LEASE_SWEEP_INTERVAL_MS);
    expect(reportError).toHaveBeenCalledOnce();
    await reaper.stop();
  });

  it("fails startup when the initial recovery sweep cannot run", async () => {
    vi.useFakeTimers();
    const recoveryError = new Error("database unavailable");
    const expireLeases = vi
      .fn<() => Promise<number>>()
      .mockRejectedValue(recoveryError);
    const reaper = new WriteLeaseReaper({ expireLeases });

    await expect(reaper.start()).rejects.toBe(recoveryError);
    expect(vi.getTimerCount()).toBe(0);
    await reaper.stop();
  });
});

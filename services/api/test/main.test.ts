import { describe, expect, it, vi } from "vitest";
import { productionStartupExitCode, runProductionApi } from "../src/main.js";
import { OperatorMessageError } from "../src/operator-message-error.js";

describe("API process startup", () => {
  it("maps startup failures to stable process exit codes", () => {
    expect(
      productionStartupExitCode(new OperatorMessageError("bad config")),
    ).toBe(2);
    expect(productionStartupExitCode(new Error("startup failure"))).toBe(1);
  });

  it("uses a distinct exit code for configuration failures", async () => {
    const signals = {
      exitCode: undefined as number | undefined,
      once: vi.fn(),
    } as unknown as NodeJS.Process;

    const failure = await runProductionApi({}, signals).catch(
      (error: unknown) => error,
    );

    expect(failure).toBeInstanceOf(OperatorMessageError);
    expect(signals.exitCode).toBe(2);
  });
});

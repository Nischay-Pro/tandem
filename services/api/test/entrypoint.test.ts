import { beforeEach, describe, expect, it, vi } from "vitest";

const { runProductionApi, reportProcessFailure, processLike } = vi.hoisted(
  () => ({
    runProductionApi: vi.fn(),
    reportProcessFailure: vi.fn(),
    processLike: {
      env: { DATABASE_URL: "postgres://test/tandem" },
      exitCode: undefined as number | undefined,
    },
  }),
);

vi.mock("../src/main.js", () => ({
  reportProcessFailure,
  runProductionApi,
}));
vi.mock("node:process", () => ({ default: processLike }));

describe("API entrypoint", () => {
  beforeEach(() => {
    vi.resetModules();
    runProductionApi.mockClear();
    reportProcessFailure.mockClear();
    processLike.exitCode = undefined;
  });

  it("starts the production API with the process environment and signals", async () => {
    runProductionApi.mockResolvedValue(undefined);
    await import("../src/entrypoint.js");

    expect(runProductionApi).toHaveBeenCalledOnce();
    expect(runProductionApi).toHaveBeenCalledWith(processLike.env, processLike);
  });

  it("handles startup failures and sets a failing exit code", async () => {
    const startupError = new Error("synthetic startup failure");
    const startup = Promise.reject(startupError);
    const catchSpy = vi.spyOn(startup, "catch");
    startup.catch(() => {});
    catchSpy.mockClear();
    runProductionApi.mockReturnValue(startup);

    await import("../src/entrypoint.js");

    expect(catchSpy).toHaveBeenCalledOnce();
    await vi.waitFor(() => expect(processLike.exitCode).toBe(1));
    expect(reportProcessFailure).toHaveBeenCalledWith(
      "API startup failed",
      startupError,
    );
  });

  it("handles non-Error startup failures", async () => {
    const startup = Promise.reject("synthetic startup failure");
    const catchSpy = vi.spyOn(startup, "catch");
    startup.catch(() => {});
    catchSpy.mockClear();
    runProductionApi.mockReturnValue(startup);

    await import("../src/entrypoint.js");

    expect(catchSpy).toHaveBeenCalledOnce();
    await vi.waitFor(() => expect(processLike.exitCode).toBe(1));
    expect(reportProcessFailure).toHaveBeenCalledWith(
      "API startup failed",
      "synthetic startup failure",
    );
  });
});

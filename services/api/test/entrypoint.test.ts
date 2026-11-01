import process from "node:process";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { runProductionApi } = vi.hoisted(() => ({ runProductionApi: vi.fn() }));

vi.mock("../src/main.js", () => ({ runProductionApi }));

describe("API entrypoint", () => {
  beforeEach(() => {
    vi.resetModules();
    runProductionApi.mockClear();
  });

  it("starts the production API with the process environment and signals", async () => {
    await import("../src/entrypoint.js");

    expect(runProductionApi).toHaveBeenCalledOnce();
    expect(runProductionApi).toHaveBeenCalledWith(process.env, process);
  });
});

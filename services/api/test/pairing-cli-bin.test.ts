import { describe, expect, it, vi } from "vitest";

const { runPairingCommand, runPairingCli } = vi.hoisted(() => ({
  runPairingCommand: vi.fn(),
  runPairingCli: vi.fn(async () => undefined),
}));

vi.mock("../src/auth/pairing-cli.js", () => ({ runPairingCommand }));
vi.mock("../src/auth/pairing-cli-entrypoint.js", () => ({ runPairingCli }));

describe("pairing CLI executable", () => {
  it("passes process arguments, environment, and command to its entrypoint", async () => {
    await import("../bin/pairing-cli.js");

    expect(runPairingCli).toHaveBeenCalledWith(
      process.argv.slice(2),
      process.env,
      runPairingCommand,
    );
  });
});

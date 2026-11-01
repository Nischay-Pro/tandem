import { afterEach, describe, expect, it, vi } from "vitest";
import { PairingCliUsageError } from "../src/auth/pairing-cli.js";
import {
  runPairingCli,
  type PairingCommand,
} from "../src/auth/pairing-cli-entrypoint.js";

const pairing = {
  code: "A7D2-X9P4",
  expiresAt: new Date("2026-11-01T10:05:00.000Z"),
  principalId: "owner",
  budgetIds: ["budget-one"],
};

afterEach(() => {
  vi.restoreAllMocks();
  process.exitCode = undefined;
});

describe("pairing CLI entrypoint", () => {
  it("prints the pairing code as one JSON line", async () => {
    const runCommand: PairingCommand = vi.fn(async () => pairing);
    const write = vi
      .spyOn(process.stdout, "write")
      .mockImplementation(() => true);

    await runPairingCli(["--budget-id", "budget-one"], {}, runCommand);

    expect(runCommand).toHaveBeenCalledWith(["--budget-id", "budget-one"], {});
    expect(write).toHaveBeenCalledWith(`${JSON.stringify(pairing)}\n`);
    expect(process.exitCode).toBeUndefined();
  });

  it("prints usage errors and sets a failing exit code", async () => {
    const runCommand: PairingCommand = vi.fn(async () => {
      throw new PairingCliUsageError();
    });
    const write = vi
      .spyOn(process.stderr, "write")
      .mockImplementation(() => true);

    await runPairingCli([], {}, runCommand);

    expect(write).toHaveBeenCalledWith(
      `${new PairingCliUsageError().message}\n`,
    );
    expect(process.exitCode).toBe(1);
  });

  it("does not expose internal failures in terminal output", async () => {
    const runCommand: PairingCommand = vi.fn(async () => {
      throw new Error("sensitive database detail");
    });
    const write = vi
      .spyOn(process.stderr, "write")
      .mockImplementation(() => true);

    await runPairingCli([], {}, runCommand);

    expect(write).toHaveBeenCalledWith("Pairing code generation failed\n");
    expect(process.exitCode).toBe(1);
  });
});

import type { Environment } from "../config.js";
import type { PairingCode } from "./device-auth.js";
import { PairingCliUsageError } from "./pairing-cli.js";

export type PairingCommand = (
  args: readonly string[],
  environment: Environment,
) => Promise<PairingCode>;

export async function runPairingCli(
  args: readonly string[],
  environment: Environment,
  runCommand: PairingCommand,
): Promise<void> {
  try {
    const pairing = await runCommand(args, environment);
    process.stdout.write(`${JSON.stringify(pairing)}\n`);
  } catch (error) {
    process.stderr.write(
      `${error instanceof PairingCliUsageError ? error.message : "Pairing code generation failed"}\n`,
    );
    process.exitCode = 1;
  }
}

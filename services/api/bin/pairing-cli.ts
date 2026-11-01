import { runPairingCommand } from "../src/auth/pairing-cli.js";
import { runPairingCli } from "../src/auth/pairing-cli-entrypoint.js";

await runPairingCli(process.argv.slice(2), process.env, runPairingCommand);

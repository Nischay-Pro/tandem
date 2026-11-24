import { run } from "./main.js";
import process from "node:process";

run(process.env, (signal, handler) => process.once(signal, handler)).catch(
  (error: unknown) => {
    process.stderr.write(
      `Extension sync failed to start: ${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  },
);

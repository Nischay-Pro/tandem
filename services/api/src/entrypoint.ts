import process from "node:process";
import { reportProcessFailure, runProductionApi } from "./main.js";

void runProductionApi(process.env, process).catch((error: unknown) => {
  reportProcessFailure("API startup failed", error);
  process.exitCode = 1;
});

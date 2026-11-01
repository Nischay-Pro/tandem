import process from "node:process";
import { runProductionApi } from "./main.js";

void runProductionApi(process.env, process);

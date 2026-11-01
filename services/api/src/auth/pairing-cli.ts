import { createPostgresDatabase } from "../database.js";
import { parseApiConfig, type Environment } from "../config.js";
import type { DeviceAuthorization, PairingCode } from "./device-auth.js";

export interface PairingOptions {
  principalId: string;
  budgetIds: string[];
}

export interface PairingCliDatabase {
  migrate(): Promise<void>;
  close(): Promise<void>;
  deviceAuth: Pick<DeviceAuthorization, "createPairingCode">;
}

export class PairingCliUsageError extends Error {
  constructor() {
    super(
      "Usage: pair --budget-id <sync-id> [--budget-id <sync-id> ...] [--principal-id <id>] | --all-budgets [--principal-id <id>]",
    );
    this.name = "PairingCliUsageError";
  }
}

export function parsePairingOptions(args: readonly string[]): PairingOptions {
  let principalId = "owner";
  const budgetIds: string[] = [];
  let allBudgets = false;

  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    const value = args[index + 1];
    if (argument === "--principal-id") {
      if (!value || value.startsWith("--")) {
        throw new PairingCliUsageError();
      }
      principalId = value;
      index += 1;
      continue;
    }
    if (argument === "--budget-id") {
      if (!value || value.startsWith("--")) {
        throw new PairingCliUsageError();
      }
      budgetIds.push(value);
      index += 1;
      continue;
    }
    if (argument === "--all-budgets" && !allBudgets) {
      allBudgets = true;
      continue;
    }
    throw new PairingCliUsageError();
  }

  if (allBudgets && budgetIds.length > 0) {
    throw new PairingCliUsageError();
  }
  if (allBudgets) {
    return { principalId, budgetIds: ["*"] };
  }
  if (budgetIds.length === 0) {
    throw new PairingCliUsageError();
  }
  return { principalId, budgetIds };
}

export async function runPairingCommand(
  args: readonly string[],
  environment: Environment,
  createDatabase: (
    connectionString: string,
  ) => PairingCliDatabase = createPostgresDatabase,
): Promise<PairingCode> {
  const options = parsePairingOptions(args);
  const config = parseApiConfig(environment);
  const database = createDatabase(config.databaseUrl);
  let pairing: PairingCode;
  try {
    await database.migrate();
    pairing = await database.deviceAuth.createPairingCode(options);
  } catch (primaryError) {
    await closeAfterFailure(database, primaryError);
    throw primaryError;
  }
  await database.close();
  return pairing;
}

async function closeAfterFailure(
  database: PairingCliDatabase,
  primaryError: unknown,
): Promise<void> {
  try {
    await database.close();
  } catch (cleanupError) {
    try {
      console.error("Pairing command cleanup also failed", {
        primaryErrorType:
          primaryError instanceof Error
            ? primaryError.name
            : typeof primaryError,
        cleanupErrorType:
          cleanupError instanceof Error
            ? cleanupError.name
            : typeof cleanupError,
      });
    } catch {
      // Cleanup reporting must not replace the original pairing error.
    }
  }
}

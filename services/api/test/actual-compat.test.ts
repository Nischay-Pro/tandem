import * as Actual from "@actual-app/api";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { createActualSession } from "../src/actual/actual-session.js";

const serverURL = process.env.ACTUAL_SERVER_URL;
const serverPassword = process.env.ACTUAL_SERVER_PASSWORD;
const firstDataDir = process.env.ACTUAL_API_DATA_DIR;
const compatibilityTestEnabled = Boolean(
  serverURL && serverPassword && firstDataDir,
);

interface BootstrapResponse {
  status?: string;
  data?: { bootstrapped?: boolean };
}

async function bootstrapDisposableServer(
  url: string,
  password: string,
): Promise<void> {
  const statusResponse = await fetch(new URL("/account/needs-bootstrap", url));
  if (!statusResponse.ok) {
    throw new Error("Could not check disposable Actual server bootstrap state");
  }

  const status = (await statusResponse.json()) as BootstrapResponse;
  if (status.data?.bootstrapped) {
    return;
  }

  const bootstrapResponse = await fetch(new URL("/account/bootstrap", url), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ password }),
  });
  if (!bootstrapResponse.ok) {
    throw new Error("Could not initialize disposable Actual server password");
  }

  const result = (await bootstrapResponse.json()) as BootstrapResponse;
  if (result.status !== "ok") {
    throw new Error("Disposable Actual server bootstrap was rejected");
  }
}

describe.skipIf(!compatibilityTestEnabled)(
  "Actual Budget 26.10.0 compatibility",
  () => {
    it("creates, synchronizes, and independently downloads synthetic budget data", async () => {
      if (!serverURL || !serverPassword || !firstDataDir) {
        throw new Error("Actual compatibility test configuration is missing");
      }

      await mkdir(firstDataDir, { recursive: true });
      await bootstrapDisposableServer(serverURL, serverPassword);

      const firstDevice = createActualSession({
        serverURL,
        dataDir: firstDataDir,
        password: serverPassword,
      });

      const budgetName = "ABC synthetic compatibility budget";
      const accountName = "ABC synthetic checking";
      const transactionNotes = "Synthetic compatibility transaction";
      const transactionDate = "2026-10-31";

      try {
        await firstDevice.execute(
          async () => {
            await Actual.runImport(budgetName, async () => {
              const accountId = await Actual.createAccount(
                { name: accountName, offbudget: false },
                0,
              );
              await Actual.addTransactions(accountId, [
                {
                  date: transactionDate,
                  amount: -1500,
                  notes: transactionNotes,
                },
              ]);
            });
          },
          { synchronizeAfterWrite: true },
        );

        const budgetFiles = await firstDevice.execute(() =>
          Actual.getBudgets(),
        );
        console.info(
          "Synthetic Actual files:",
          budgetFiles.map(({ id, name, groupId, cloudFileId }) => ({
            id,
            name,
            groupId,
            cloudFileId,
          })),
        );
        const budget = budgetFiles.find(
          (file) => file.name === budgetName && file.groupId,
        );
        expect(budget?.groupId).toBeTruthy();
        if (!budget?.groupId) {
          throw new Error("Actual did not publish the synthetic test budget");
        }

        const initialData = await firstDevice.runBudget(
          budget.groupId,
          async () => {
            const accounts = await Actual.getAccounts();
            const account = accounts.find((item) => item.name === accountName);
            if (!account) {
              throw new Error("Actual did not return the synthetic account");
            }

            const transactions = await Actual.getTransactions(
              account.id,
              transactionDate,
              transactionDate,
            );
            return { accountId: account.id, transactions };
          },
        );

        expect(initialData.transactions).toHaveLength(1);
        const transaction = initialData.transactions[0];
        expect(transaction).toMatchObject({
          amount: -1500,
          date: transactionDate,
          notes: transactionNotes,
        });
        if (!transaction) {
          throw new Error("Actual did not return the synthetic transaction");
        }

        await firstDevice.runBudget(
          budget.groupId,
          async () =>
            Actual.updateTransaction(transaction.id, {
              amount: -1200,
              notes: "Updated synthetic compatibility transaction",
            }),
          { synchronizeAfterWrite: true },
        );
        await firstDevice.close();

        const secondDataDir = path.join(firstDataDir, "device-two");
        await mkdir(secondDataDir, { recursive: true });
        const secondDevice = createActualSession({
          serverURL,
          dataDir: secondDataDir,
          password: serverPassword,
        });

        try {
          const downloadedTransactions = await secondDevice.runBudget(
            budget.groupId,
            () =>
              Actual.getTransactions(
                initialData.accountId,
                transactionDate,
                transactionDate,
              ),
          );

          expect(downloadedTransactions).toEqual([
            expect.objectContaining({
              id: transaction.id,
              amount: -1200,
              notes: "Updated synthetic compatibility transaction",
            }),
          ]);
        } finally {
          await secondDevice.close();
        }
      } finally {
        await firstDevice.close();
      }
    }, 60_000);
  },
);

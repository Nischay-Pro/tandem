import * as Actual from "@actual-app/api";
import { randomUUID } from "node:crypto";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { createActualSession } from "../src/actual/actual-session.js";
import {
  createTransactionRevision,
  snapshotActualTransaction,
} from "../src/actual/transaction-reconciliation.js";
import { createActualTransactionStore } from "../src/actual/transaction-store.js";

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

      const budgetName = `ABC synthetic compatibility budget ${randomUUID()}`;
      const accountName = "ABC synthetic checking";
      const transferAccountName = "ABC synthetic savings";
      const transactionNotes = "Synthetic compatibility transaction";
      const transactionDate = "2026-10-31";
      const transactionStore = createActualTransactionStore();
      let transferAccountId = "";

      try {
        await firstDevice.execute(
          async () => {
            await Actual.runImport(budgetName, async () => {
              const accountId = await Actual.createAccount(
                { name: accountName, offbudget: false },
                0,
              );
              transferAccountId = await Actual.createAccount(
                { name: transferAccountName, offbudget: false },
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

        const transactionByAql = await firstDevice.runBudget(
          budget.groupId,
          () => transactionStore.read(transaction.id),
        );
        expect(transactionByAql).toMatchObject({
          id: transaction.id,
          account: initialData.accountId,
          amount: -1500,
          date: transactionDate,
        });

        await firstDevice.runBudget(
          budget.groupId,
          async () => {
            await Actual.addTransactions(initialData.accountId, [
              {
                date: transactionDate,
                amount: -500,
                notes: "Synthetic split parent",
                subtransactions: [
                  { amount: -200, notes: "Synthetic split child one" },
                  { amount: -300, notes: "Synthetic split child two" },
                ],
              },
            ]);
          },
          { synchronizeAfterWrite: true },
        );

        const splitTransactions = await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.getTransactions(
              initialData.accountId,
              transactionDate,
              transactionDate,
            ),
        );
        const splitParent = splitTransactions.find(
          (item) => item.notes === "Synthetic split parent",
        );
        expect(splitParent?.is_parent).toBe(true);
        expect(splitParent?.subtransactions).toHaveLength(2);
        const splitParentSnapshot = snapshotActualTransaction(splitParent);
        expect(splitParentSnapshot.subtransactions).toHaveLength(2);
        expect(
          createTransactionRevision({
            ...splitParentSnapshot,
            subtransactions: splitParentSnapshot.subtransactions.map(
              (child, index) =>
                index === 0 ? { ...child, amount: child.amount - 1 } : child,
            ),
          }),
        ).not.toBe(createTransactionRevision(splitParentSnapshot));
        const splitChild = splitParent?.subtransactions?.[0];
        if (!splitChild) {
          throw new Error("Actual did not return the synthetic split child");
        }

        const childByAql = await firstDevice.runBudget(budget.groupId, () =>
          transactionStore.read(splitChild.id),
        );
        expect(childByAql).toMatchObject({
          id: splitChild.id,
          is_child: true,
          parent_id: splitParent.id,
          amount: -200,
        });

        const transferPayeeId = await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.createPayee({
              name: `Transfer: ${transferAccountName}`,
            }),
        );
        await firstDevice.runBudget(budget.groupId, () =>
          Actual.updatePayee(transferPayeeId, {
            transfer_acct: transferAccountId,
          }),
        );
        await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.addTransactions(
              initialData.accountId,
              [
                {
                  date: transactionDate,
                  amount: -425,
                  notes: "Synthetic transfer",
                  payee: transferPayeeId,
                },
              ],
              { runTransfers: true },
            ),
          { synchronizeAfterWrite: true },
        );

        const transferLegs = await firstDevice.runBudget(
          budget.groupId,
          async () => {
            const outgoing = await Actual.getTransactions(
              initialData.accountId,
              transactionDate,
              transactionDate,
            );
            const incoming = await Actual.getTransactions(
              transferAccountId,
              transactionDate,
              transactionDate,
            );
            return {
              outgoing: outgoing.find(
                (item) => item.notes === "Synthetic transfer",
              ),
              incoming: incoming.find(
                (item) => item.transfer_id !== null && item.amount === 425,
              ),
            };
          },
        );
        expect(transferLegs.outgoing).toMatchObject({
          amount: -425,
          transfer_id: transferLegs.incoming?.id,
        });
        expect(transferLegs.incoming).toMatchObject({
          amount: 425,
          transfer_id: transferLegs.outgoing?.id,
        });
        const transferSnapshot = snapshotActualTransaction(
          transferLegs.outgoing,
        );
        expect(transferSnapshot.transferId).toBe(transferLegs.incoming?.id);

        await firstDevice.runBudget(
          budget.groupId,
          async () =>
            transactionStore.update(transaction.id, {
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

          const downloadedTransaction = downloadedTransactions.find(
            (item) => item.id === transaction.id,
          );
          expect(downloadedTransaction).toMatchObject({
            id: transaction.id,
            amount: -1200,
            notes: "Updated synthetic compatibility transaction",
          });
          const downloadedSplit = downloadedTransactions.find(
            (item) => item.id === splitParent?.id,
          );
          expect(downloadedSplit?.subtransactions?.map(({ id }) => id)).toEqual(
            splitParent?.subtransactions?.map(({ id }) => id),
          );
          const downloadedTransfer = downloadedTransactions.find(
            (item) => item.id === transferLegs.outgoing?.id,
          );
          expect(downloadedTransfer?.transfer_id).toBe(
            transferLegs.incoming?.id,
          );

          await secondDevice.runBudget(
            budget.groupId,
            () => transactionStore.delete(transaction.id),
            { synchronizeAfterWrite: true },
          );
        } finally {
          await secondDevice.close();
        }

        const thirdDataDir = path.join(firstDataDir, "device-three");
        await mkdir(thirdDataDir, { recursive: true });
        const thirdDevice = createActualSession({
          serverURL,
          dataDir: thirdDataDir,
          password: serverPassword,
        });
        try {
          await expect(
            thirdDevice.runBudget(budget.groupId, () =>
              transactionStore.read(transaction.id),
            ),
          ).resolves.toBeUndefined();
        } finally {
          await thirdDevice.close();
        }
      } finally {
        await firstDevice.close();
      }
    }, 60_000);
  },
);

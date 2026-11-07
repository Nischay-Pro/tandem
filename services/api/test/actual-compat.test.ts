import * as Actual from "@actual-app/api";
import { randomUUID } from "node:crypto";
import { mkdir } from "node:fs/promises";
import { createServer } from "node:net";
import path from "node:path";
import { Pool } from "pg";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { buildApiApp } from "../src/app.js";
import type { Database } from "../src/database.js";
import type {
  DeviceAuthorization,
  DeviceRecord,
} from "../src/auth/device-auth.js";
import { createActualSession } from "../src/actual/actual-session.js";
import { ActualBudgetSnapshotReader } from "../src/actual/budget-snapshot-reader.js";
import { createBudgetMaintenanceApi } from "../src/actual/budget-maintenance-api.js";
import { createTransactionApi } from "../src/actual/transaction-api.js";
import { TransactionReconciler } from "../src/actual/transaction-reconciler.js";
import {
  MAX_TRANSACTION_AMOUNT,
  createTransactionRevision,
  snapshotActualTransaction,
  type TransactionSnapshot,
} from "../src/actual/transaction-reconciliation.js";
import { createActualTransactionStore } from "../src/actual/transaction-store.js";
import { createPostgresDatabase } from "../src/database.js";
import { BudgetReadApi } from "../src/budget-projection/budget-read-api.js";
import { runApi } from "../src/main.js";
import type { ShutdownSignal } from "../src/server.js";

const serverURL = process.env.ACTUAL_SERVER_URL;
const serverPassword = process.env.ACTUAL_SERVER_PASSWORD;
const firstDataDir = process.env.ACTUAL_API_DATA_DIR;
const databaseUrl = process.env.DATABASE_URL;
const pairingCodeHashKey =
  process.env.PAIRING_CODE_HASH_KEY ??
  "synthetic-pairing-code-hash-key-32-bytes";
const compatibilityTestEnabled = Boolean(
  serverURL && serverPassword && firstDataDir && databaseUrl,
);

let database: Database | undefined;
let adminPool: Pool | undefined;
let compatibilitySchema: string | undefined;
let compatibilityDatabaseUrl: string | undefined;
let encryptedCompatibilityFixture:
  { budgetId: string; budgetName: string; transactionId: string } | undefined;

async function findAvailablePort(): Promise<number> {
  const probe = createServer();
  await new Promise<void>((resolve, reject) => {
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", resolve);
  });
  const address = probe.address();
  if (!address || typeof address === "string") {
    throw new Error("Could not determine an available TCP port");
  }
  await new Promise<void>((resolve, reject) => {
    probe.close((error) => (error ? reject(error) : resolve()));
  });
  return address.port;
}

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
  "Actual Budget 26.11.0 compatibility",
  () => {
    beforeAll(async () => {
      if (!databaseUrl) {
        throw new Error(
          "Actual compatibility database configuration is missing",
        );
      }
      compatibilitySchema = `actual_compat_${randomUUID().replaceAll("-", "")}`;
      adminPool = new Pool({ connectionString: databaseUrl, max: 2 });
      await adminPool.query(`CREATE SCHEMA ${compatibilitySchema}`);
      const isolatedUrl = new URL(databaseUrl);
      isolatedUrl.searchParams.set(
        "options",
        `-c search_path=${compatibilitySchema}`,
      );
      compatibilityDatabaseUrl = isolatedUrl.toString();
      database = createPostgresDatabase(
        isolatedUrl.toString(),
        pairingCodeHashKey,
      );
      await database.migrate();
    }, 30_000);

    afterAll(async () => {
      await database?.close();
      if (compatibilitySchema) {
        await adminPool?.query(`DROP SCHEMA ${compatibilitySchema} CASCADE`);
      }
      await adminPool?.end();
    });

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
      let categoryId = "";
      let hiddenCategoryId = "";
      let transferAccountId = "";
      let scheduleId = "";
      let schedulePayeeId = "";

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

        await firstDevice.runBudget(
          budget.groupId,
          async () => {
            const categoryGroupId = await Actual.createCategoryGroup({
              name: "ABC synthetic category group",
            });
            categoryId = await Actual.createCategory({
              name: "ABC synthetic category",
              group_id: categoryGroupId,
            });
            hiddenCategoryId = await Actual.createCategory({
              name: "ABC synthetic hidden category",
              group_id: categoryGroupId,
              hidden: true,
            });
          },
          { synchronizeAfterWrite: true },
        );

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

        scheduleId = await firstDevice.runBudget(
          budget.groupId,
          async () => {
            schedulePayeeId = await Actual.createPayee({
              name: "ABC synthetic recurring payee",
            });
            return Actual.createSchedule({
              name: "ABC synthetic monthly schedule",
              posts_transaction: false,
              payee: schedulePayeeId,
              account: initialData.accountId,
              amount: -15_000,
              amountOp: "is",
              date: {
                frequency: "monthly",
                interval: 1,
                patterns: [{ type: "day", value: 1 }],
                start: transactionDate,
                skipWeekend: false,
              },
            });
          },
          { synchronizeAfterWrite: true },
        );

        await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.updateTransaction(transaction.id, { category: categoryId }),
          { synchronizeAfterWrite: true },
        );

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

        if (!database) {
          throw new Error("Actual compatibility database was not initialized");
        }
        const compatibilityToken = "R".repeat(43);
        const compatibilityDevice: DeviceRecord = {
          deviceId: randomUUID(),
          principalId: "actual-compatibility-owner",
          name: "Synthetic compatibility client",
          budgetIds: [budget.groupId],
          createdAt: new Date(),
          lastSeenAt: null,
          revokedAt: null,
        };
        const deviceAuth = {
          authenticate: vi.fn(async (token?: string) =>
            token === compatibilityToken ? compatibilityDevice : undefined,
          ),
        } as unknown as DeviceAuthorization;
        const reconciler = new TransactionReconciler(
          database,
          database.journal,
          firstDevice,
          transactionStore,
        );
        const transactionApi = createTransactionApi(
          firstDevice,
          transactionStore,
          reconciler,
          database.journal,
        );
        const budgetReadApi = new BudgetReadApi(
          database,
          new ActualBudgetSnapshotReader(firstDevice),
          database.budgetProjection,
          "actual-compatibility",
        );
        const reconciliationApp = await buildApiApp(
          { ping: () => database!.ping(), close: async () => undefined },
          {
            deviceAuth,
            transactionApi,
            budgetReadApi,
            budgetMaintenanceApi: createBudgetMaintenanceApi(
              database,
              firstDevice,
            ),
            maintenanceJournal: database.journal,
          },
        );
        const authorizationHeaders = {
          authorization: `Bearer ${compatibilityToken}`,
        };
        const companionTransactionId = `tandem:${randomUUID()}`;
        const maxAmountTransactionId = `tandem:${randomUUID()}`;
        const followupTransactionId = `tandem:${randomUUID()}`;
        const minimumDateTransactionId = `tandem:${randomUUID()}`;
        const createOperationId = randomUUID();
        const companionTransactionPath = `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(companionTransactionId)}`;
        try {
          const scheduleEditResponse = await reconciliationApp.inject({
            method: "POST",
            url: `/api/v1/budgets/${budget.groupId}/schedules/${encodeURIComponent(scheduleId)}/actions`,
            headers: {
              ...authorizationHeaders,
              "idempotency-key": `compat-schedule-edit-${randomUUID()}`,
            },
            payload: {
              action: "edit",
              name: "ABC edited monthly schedule",
              accountId: transferAccountId,
              payeeId: schedulePayeeId,
              amountMinor: -18_000,
              amountOp: "is",
              date: {
                frequency: "monthly",
                interval: 2,
                patterns: [{ type: "day", value: 15 }],
                start: "2026-11-15",
                skipWeekend: true,
                weekendSolveMode: "before",
                endMode: "never",
              },
            },
          });
          expect(scheduleEditResponse.statusCode).toBe(200);
          const editedSchedule = await firstDevice.runBudget(
            budget.groupId,
            async () => (await Actual.getSchedules()).find(({ id }) => id === scheduleId),
          );
          expect(editedSchedule).toMatchObject({
            name: "ABC edited monthly schedule",
            account: transferAccountId,
            amount: -18_000,
            amountOp: "is",
            date: {
              frequency: "monthly",
              interval: 2,
              patterns: [{ type: "day", value: 15 }],
              start: "2026-11-15",
              skipWeekend: true,
              weekendSolveMode: "before",
              endMode: "never",
            },
          });
          const scheduleSnapshot = await budgetReadApi.getSnapshot(budget.groupId);
          expect(
            scheduleSnapshot.records.some(
              (record) => record.type === "schedule" && record.id === scheduleId,
            ),
          ).toBe(true);

          const createResponse = await reconciliationApp.inject({
            method: "POST",
            url: `${companionTransactionPath}/operations`,
            headers: {
              authorization: `Bearer ${compatibilityToken}`,
              "idempotency-key": `compat-create-${createOperationId}`,
            },
            payload: {
              operationId: createOperationId,
              action: "create",
              patch: {
                account: initialData.accountId,
                amount: -777,
                date: transactionDate,
                payee: null,
                category: categoryId,
                notes: "Created through Companion",
                cleared: false,
              },
            },
          });
          expect(createResponse.statusCode).toBe(200);
          expect(createResponse.json().operation).toMatchObject({
            action: "create",
            transactionId: companionTransactionId,
            status: "committed",
          });

          const companionRead = await reconciliationApp.inject({
            method: "GET",
            url: companionTransactionPath,
            headers: authorizationHeaders,
          });
          expect(companionRead.statusCode).toBe(200);
          expect(companionRead.json().transaction).toMatchObject({
            id: companionTransactionId,
            account: initialData.accountId,
            amount: -777,
            date: transactionDate,
            category: categoryId,
            notes: "Created through Companion",
            importedId: null,
          });

          const createdSnapshot = await reconciliationApp.inject({
            method: "GET",
            url: `/api/v1/budgets/${budget.groupId}/snapshot`,
            headers: authorizationHeaders,
          });
          expect(
            createdSnapshot.statusCode,
            JSON.stringify(createdSnapshot.json()),
          ).toBe(200);
          expect(
            createdSnapshot
              .json()
              .records.find(
                (record: { type: string; id: string }) =>
                  record.type === "transaction" &&
                  record.id === companionTransactionId,
              ),
          ).toMatchObject({
            type: "transaction",
            id: companionTransactionId,
            body: { id: companionTransactionId, importedId: null },
          });

          const submitCreate = async (
            stableId: string,
            patch: Record<string, unknown>,
          ) => {
            const operationId = randomUUID();
            const response = await reconciliationApp.inject({
              method: "POST",
              url: `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(stableId)}/operations`,
              headers: {
                ...authorizationHeaders,
                "idempotency-key": `compat-create-${operationId}`,
              },
              payload: { operationId, action: "create", patch },
            });
            return { operationId, response };
          };

          const largeAmountCreate = await submitCreate(maxAmountTransactionId, {
            account: initialData.accountId,
            amount: MAX_TRANSACTION_AMOUNT,
            date: transactionDate,
            payee: null,
            category: categoryId,
            notes: "Maximum supported Actual amount",
            cleared: false,
          });
          expect(largeAmountCreate.response.statusCode).toBe(200);
          expect(largeAmountCreate.response.json().operation.status).toBe(
            "committed",
          );

          const followupCreate = await submitCreate(followupTransactionId, {
            account: initialData.accountId,
            amount: -1234,
            date: transactionDate,
            payee: null,
            category: categoryId,
            notes: "Normal create after maximum supported amount",
            cleared: false,
          });
          expect(followupCreate.response.statusCode).toBe(200);
          expect(followupCreate.response.json().operation.status).toBe(
            "committed",
          );
          const followupRead = await reconciliationApp.inject({
            method: "GET",
            url: `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(followupTransactionId)}`,
            headers: authorizationHeaders,
          });
          expect(followupRead.statusCode).toBe(200);
          expect(followupRead.json().transaction).toMatchObject({
            id: followupTransactionId,
            amount: -1234,
            notes: "Normal create after maximum supported amount",
          });

          const minimumDateCreate = await submitCreate(
            minimumDateTransactionId,
            {
              account: initialData.accountId,
              amount: -10,
              date: "1995-01-01",
              payee: null,
              category: null,
              notes: "Minimum supported Actual date",
              cleared: false,
            },
          );
          expect(minimumDateCreate.response.statusCode).toBe(200);
          expect(minimumDateCreate.response.json().operation.status).toBe(
            "committed",
          );
          const minimumDateRead = await reconciliationApp.inject({
            method: "GET",
            url: `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(minimumDateTransactionId)}`,
            headers: authorizationHeaders,
          });
          expect(minimumDateRead.statusCode).toBe(200);
          expect(minimumDateRead.json().transaction).toMatchObject({
            id: minimumDateTransactionId,
            date: "1995-01-01",
            notes: "Minimum supported Actual date",
          });

          const invalidReferenceCases = [
            {
              code: "account-not-found",
              patch: {
                account: "no-such-account",
                amount: -10,
                date: transactionDate,
                payee: null,
                category: null,
                notes: "Invalid account",
                cleared: false,
              },
            },
            {
              code: "payee-not-found",
              patch: {
                account: initialData.accountId,
                amount: -10,
                date: transactionDate,
                payee: "no-such-payee",
                category: null,
                notes: "Invalid payee",
                cleared: false,
              },
            },
            {
              code: "category-not-found",
              patch: {
                account: initialData.accountId,
                amount: -10,
                date: transactionDate,
                payee: null,
                category: "no-such-category",
                notes: "Invalid category",
                cleared: false,
              },
            },
          ];
          for (const testCase of invalidReferenceCases) {
            const invalidId = `tandem:${randomUUID()}`;
            const invalidCreate = await submitCreate(invalidId, testCase.patch);
            expect(invalidCreate.response.statusCode).toBe(400);
            expect(invalidCreate.response.json().error.code).toBe(
              testCase.code,
            );
            await expect(
              database.journal.get(invalidCreate.operationId),
            ).resolves.toMatchObject({ status: "rejected" });
            await expect(
              firstDevice.runBudget(budget.groupId, () =>
                transactionStore.read(invalidId),
              ),
            ).resolves.toBeUndefined();
          }

          const invalidDate = await submitCreate(`tandem:${randomUUID()}`, {
            account: initialData.accountId,
            amount: -10,
            date: "1994-12-31",
            payee: null,
            category: null,
            notes: "Date before supported range",
            cleared: false,
          });
          expect(invalidDate.response.statusCode).toBe(400);
          expect(invalidDate.response.json().error).toMatchObject({
            code: "invalid_request",
            message: "date must be on or after 1995-01-01",
          });
          await expect(
            database.journal.get(invalidDate.operationId),
          ).resolves.toBeUndefined();
        } finally {
          await firstDevice
            .runBudget(
              budget.groupId,
              async () => {
                for (const stableId of [
                  companionTransactionId,
                  maxAmountTransactionId,
                  followupTransactionId,
                  minimumDateTransactionId,
                ]) {
                  if (await transactionStore.read(stableId)) {
                    await transactionStore.delete(stableId);
                  }
                }
              },
              { synchronizeAfterWrite: true },
            )
            .catch(() => undefined);
        }
        const transactionPath = `/api/v1/budgets/${budget.groupId}/transactions/${transaction.id}`;
        const firstRead = await reconciliationApp.inject({
          method: "GET",
          url: transactionPath,
          headers: authorizationHeaders,
        });
        expect(firstRead.statusCode).toBe(200);
        const base = firstRead.json() as {
          transaction: TransactionSnapshot;
          revision: string;
        };

        await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.updateTransaction(transaction.id, {
              amount: -1300,
              notes: "Updated through Actual web interface",
            }),
          { synchronizeAfterWrite: true },
        );

        const staleOperationId = randomUUID();
        const staleWrite = await reconciliationApp.inject({
          method: "POST",
          url: `${transactionPath}/operations`,
          headers: {
            ...authorizationHeaders,
            "idempotency-key": `compat-stale-${staleOperationId}`,
          },
          payload: {
            operationId: staleOperationId,
            action: "update",
            baseRevision: base.revision,
            baseSnapshot: base.transaction,
            patch: { amount: -1200 },
          },
        });
        expect(staleWrite.statusCode).toBe(200);
        expect(staleWrite.json().operation.status).toBe("conflict");
        expect(staleWrite.json().conflict).toMatchObject({
          conflictType: "same-field-edit",
          conflictingFields: ["amount"],
          remoteSnapshot: {
            amount: -1300,
            notes: "Updated through Actual web interface",
          },
        });

        const latestRead = await reconciliationApp.inject({
          method: "GET",
          url: transactionPath,
          headers: authorizationHeaders,
        });
        const latest = latestRead.json() as {
          transaction: TransactionSnapshot;
          revision: string;
        };
        const resolutionOperationId = randomUUID();
        const resolution = await reconciliationApp.inject({
          method: "POST",
          url: `${transactionPath}/operations`,
          headers: {
            ...authorizationHeaders,
            "idempotency-key": `compat-resolution-${resolutionOperationId}`,
          },
          payload: {
            operationId: resolutionOperationId,
            action: "update",
            baseRevision: latest.revision,
            baseSnapshot: latest.transaction,
            patch: { amount: -1200 },
          },
        });
        expect(resolution.statusCode).toBe(200);
        expect(resolution.json().operation.status).toBe("committed");
        const operationStatus = await reconciliationApp.inject({
          method: "GET",
          url: `/api/v1/operations/${resolutionOperationId}`,
          headers: authorizationHeaders,
        });
        expect(operationStatus.statusCode).toBe(200);
        expect(operationStatus.json().operation.status).toBe("committed");
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
        if (!splitParent) {
          throw new Error("Actual did not return the synthetic split parent");
        }
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

        await firstDevice.runBudget(
          budget.groupId,
          () =>
            Actual.addTransactions(initialData.accountId, [
              {
                date: transactionDate,
                amount: -700,
                notes: "Synthetic split mutation parent",
                subtransactions: [
                  { amount: -250, notes: "Synthetic mutation child one" },
                  { amount: -450, notes: "Synthetic mutation child two" },
                ],
              },
            ]),
          { synchronizeAfterWrite: true },
        );
        const splitMutationParent = await firstDevice.runBudget(
          budget.groupId,
          async () => {
            const transactions = await Actual.getTransactions(
              initialData.accountId,
              transactionDate,
              transactionDate,
            );
            return transactions.find(
              (item) => item.notes === "Synthetic split mutation parent",
            );
          },
        );
        if (!splitMutationParent?.subtransactions?.length) {
          throw new Error("Actual did not return the split mutation fixture");
        }
        const splitMutationChild = splitMutationParent.subtransactions[0];
        if (!splitMutationChild) {
          throw new Error("Actual did not return a split mutation child");
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

        const transferOutgoingId = transferLegs.outgoing?.id;
        const transferIncomingId = transferLegs.incoming?.id;
        if (!transferOutgoingId || !transferIncomingId) {
          throw new Error("Actual did not return both synthetic transfer legs");
        }

        const submitSplitWrite = async (
          transactionId: string,
          action: "update" | "delete",
          patch: Record<string, unknown> = {},
        ) => {
          const transactionPath = `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(transactionId)}`;
          const readResponse = await reconciliationApp.inject({
            method: "GET",
            url: transactionPath,
            headers: authorizationHeaders,
          });
          expect(readResponse.statusCode).toBe(200);
          const base = readResponse.json() as {
            transaction: TransactionSnapshot;
            revision: string;
          };
          const operationId = randomUUID();
          const writeResponse = await reconciliationApp.inject({
            method: "POST",
            url: `${transactionPath}/operations`,
            headers: {
              ...authorizationHeaders,
              "idempotency-key": `compat-structured-${operationId}`,
            },
            payload: {
              operationId,
              action,
              baseRevision: base.revision,
              baseSnapshot: base.transaction,
              patch,
            },
          });
          expect(writeResponse.statusCode).toBe(200);
          expect(writeResponse.json()).toMatchObject({
            operation: { action, status: "committed" },
            conflict: null,
          });
        };

        await submitSplitWrite(splitMutationParent.id, "update", {
          notes: "Updated synthetic split parent",
        });
        const updatedSplitParent = await firstDevice.runBudget(
          budget.groupId,
          () => transactionStore.read(splitMutationParent.id),
        );
        expect(updatedSplitParent).toMatchObject({
          notes: "Updated synthetic split parent",
          is_parent: true,
        });

        await submitSplitWrite(splitMutationChild.id, "update", {
          notes: "Updated synthetic split child",
        });
        const updatedSplitChild = await firstDevice.runBudget(
          budget.groupId,
          () => transactionStore.read(splitMutationChild.id),
        );
        expect(updatedSplitChild).toMatchObject({
          notes: "Updated synthetic split child",
          is_child: true,
          parent_id: splitMutationParent.id,
        });

        await submitSplitWrite(splitMutationChild.id, "delete");
        const afterSplitChildDelete = await firstDevice.runBudget(
          budget.groupId,
          async () => ({
            parent: await transactionStore.read(splitMutationParent.id),
            child: await transactionStore.read(splitMutationChild.id),
          }),
        );
        expect(afterSplitChildDelete.child).toBeUndefined();
        expect(afterSplitChildDelete.parent).toMatchObject({
          is_parent: true,
          subtransactions: [{ is_child: true }],
        });

        const remainingSplitChild = (
          afterSplitChildDelete.parent as {
            subtransactions: Array<{ id: string }>;
          }
        ).subtransactions[0]?.id;
        expect(remainingSplitChild).toBeDefined();
        await submitSplitWrite(splitMutationParent.id, "delete");
        const afterSplitParentDelete = await firstDevice.runBudget(
          budget.groupId,
          async () => ({
            parent: await transactionStore.read(splitMutationParent.id),
            child: remainingSplitChild
              ? await transactionStore.read(remainingSplitChild)
              : undefined,
          }),
        );
        expect(afterSplitParentDelete).toEqual({
          parent: undefined,
          child: undefined,
        });

        const readTransfer = async (transactionId: string) => {
          const response = await reconciliationApp.inject({
            method: "GET",
            url: `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(transactionId)}`,
            headers: authorizationHeaders,
          });
          expect(response.statusCode).toBe(200);
          return response.json() as {
            transaction: TransactionSnapshot;
            revision: string;
          };
        };
        const submitTransferWrite = async (
          transactionId: string,
          action: "update" | "delete",
          patch: Record<string, unknown>,
        ) => {
          const base = await readTransfer(transactionId);
          const operationId = randomUUID();
          return reconciliationApp.inject({
            method: "POST",
            url: `/api/v1/budgets/${budget.groupId}/transactions/${encodeURIComponent(transactionId)}/operations`,
            headers: {
              ...authorizationHeaders,
              "idempotency-key": `compat-transfer-${operationId}`,
            },
            payload: {
              operationId,
              action,
              baseRevision: base.revision,
              baseSnapshot: base.transaction,
              patch,
            },
          });
        };
        const transferUpdate = await submitTransferWrite(
          transferOutgoingId,
          "update",
          { amount: -500, notes: "Updated synthetic transfer" },
        );
        expect(transferUpdate.statusCode).toBe(200);
        expect(transferUpdate.json().operation.status).toBe("committed");
        const updatedLegs = await firstDevice.runBudget(
          budget.groupId,
          async () => {
            const outgoing = await transactionStore.read(transferOutgoingId);
            const incoming = await transactionStore.read(transferIncomingId);
            return { outgoing, incoming };
          },
        );
        expect(updatedLegs.outgoing).toMatchObject({
          id: transferOutgoingId,
          amount: -500,
          notes: "Updated synthetic transfer",
          transfer_id: transferIncomingId,
        });
        expect(updatedLegs.incoming).toMatchObject({
          id: transferIncomingId,
          amount: 500,
          notes: "Updated synthetic transfer",
          transfer_id: transferOutgoingId,
        });

        const transferDelete = await submitTransferWrite(
          transferIncomingId,
          "delete",
          {},
        );
        expect(transferDelete.statusCode).toBe(200);
        expect(transferDelete.json().operation.status).toBe("committed");
        const deletedLegs = await firstDevice.runBudget(
          budget.groupId,
          async () => ({
            outgoing: await transactionStore.read(transferOutgoingId),
            incoming: await transactionStore.read(transferIncomingId),
          }),
        );
        expect(deletedLegs).toEqual({
          outgoing: undefined,
          incoming: undefined,
        });

        await reconciliationApp.close();

        await firstDevice.close();

        if (!compatibilityDatabaseUrl) {
          throw new Error("Isolated database URL was not initialized");
        }
        const runtimePairing = await database.deviceAuth.createPairingCode({
          principalId: "actual-production-runtime-owner",
          budgetIds: [budget.groupId],
        });
        const runtimeDevice = await database.deviceAuth.pair({
          code: runtimePairing.code,
          deviceName: "Synthetic runtime client",
        });
        if (!runtimeDevice) {
          throw new Error("Could not pair the production runtime test device");
        }
        const runtimeAuthorizationHeaders = {
          authorization: `Bearer ${runtimeDevice.accessToken}`,
        };
        const runtimeDataDir = path.join(firstDataDir, "api-runtime");
        await mkdir(runtimeDataDir, { recursive: true });
        const signalHandlers = new Map<ShutdownSignal, () => Promise<void>>();
        const productionApp = await runApi(
          {
            DATABASE_URL: compatibilityDatabaseUrl,
            PAIRING_CODE_HASH_KEY: pairingCodeHashKey,
            ACTUAL_SERVER_URL: serverURL,
            ACTUAL_SERVER_PASSWORD: serverPassword,
            ACTUAL_API_DATA_DIR: runtimeDataDir,
            HOST: "127.0.0.1",
            PORT: String(await findAvailablePort()),
          },
          createPostgresDatabase,
          (signal, handler) => signalHandlers.set(signal, handler),
        );
        const runtimeCatalogResponse = await productionApp.inject({
          method: "GET",
          url: "/api/v1/budgets",
          headers: runtimeAuthorizationHeaders,
        });
        expect(runtimeCatalogResponse.statusCode).toBe(200);
        expect(runtimeCatalogResponse.json()).toMatchObject({
          budgets: [
            {
              budgetId: budget.groupId,
              name: budget.name,
              isEncrypted: Boolean(budget.encryptKeyId),
            },
          ],
        });
        const runtimeRead = await productionApp.inject({
          method: "GET",
          url: `/api/v1/budgets/${budget.groupId}/transactions/${transaction.id}`,
          headers: runtimeAuthorizationHeaders,
        });
        expect(runtimeRead.statusCode).toBe(200);
        const runtimeTransaction = runtimeRead.json() as {
          transaction: Record<string, unknown>;
          revision: string;
        };
        expect(runtimeTransaction.transaction).toMatchObject({
          amount: -1200,
          notes: "Updated through Actual web interface",
        });
        const runtimeSnapshotResponse = await productionApp.inject({
          method: "GET",
          url: `/api/v1/budgets/${budget.groupId}/snapshot`,
          headers: runtimeAuthorizationHeaders,
        });
        expect(runtimeSnapshotResponse.statusCode).toBe(200);
        const runtimeSnapshot = runtimeSnapshotResponse.json() as {
          generation: string;
          cursor: string;
          records: Array<{
            type: string;
            id: string;
            revision: string;
            body: Record<string, unknown>;
          }>;
        };
        expect(runtimeSnapshot.generation).toMatch(
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
        );
        const findRuntimeRecord = (type: string, id?: string) =>
          runtimeSnapshot.records.find(
            (record) =>
              record.type === type && (id === undefined || record.id === id),
          );
        const runtimeAccount = findRuntimeRecord(
          "account",
          initialData.accountId,
        );
        const feedTransaction = findRuntimeRecord(
          "transaction",
          transaction.id,
        );
        expect(feedTransaction?.body).toEqual(runtimeTransaction.transaction);
        expect(feedTransaction?.revision).toBe(runtimeTransaction.revision);
        expect(typeof runtimeAccount?.body.balance_current).toBe("number");
        expect(findRuntimeRecord("category", categoryId)).toBeDefined();
        expect(findRuntimeRecord("category", hiddenCategoryId)).toBeDefined();
        expect(findRuntimeRecord("category-group")).toBeDefined();
        expect(findRuntimeRecord("payee", transferPayeeId)).toBeDefined();
        expect(typeof findRuntimeRecord("budget-month")?.body.month).toBe(
          "string",
        );
        expect(findRuntimeRecord("transaction", transaction.id)).toBeDefined();
        expect(findRuntimeRecord("transaction", splitChild.id)).toBeDefined();
        expect(
          findRuntimeRecord("transaction", transferLegs.outgoing?.id),
        ).toBeUndefined();
        const firstChangePageResponse = await productionApp.inject({
          method: "GET",
          url: `/api/v1/budgets/${budget.groupId}/changes?generation=${runtimeSnapshot.generation}&cursor=0&limit=2`,
          headers: runtimeAuthorizationHeaders,
        });
        expect(firstChangePageResponse.statusCode).toBe(200);
        const firstChangePage = firstChangePageResponse.json() as {
          throughCursor: string;
          nextCursor: string;
          hasMore: boolean;
          resetRequired: boolean;
          changes: unknown[];
        };
        expect(firstChangePage).toMatchObject({
          throughCursor: runtimeSnapshot.cursor,
          hasMore: true,
          resetRequired: false,
        });
        expect(firstChangePage.changes).toHaveLength(2);
        const nextChangePageResponse = await productionApp.inject({
          method: "GET",
          url: `/api/v1/budgets/${budget.groupId}/changes?generation=${runtimeSnapshot.generation}&cursor=${firstChangePage.nextCursor}&throughCursor=${firstChangePage.throughCursor}&limit=2`,
          headers: runtimeAuthorizationHeaders,
        });
        expect(nextChangePageResponse.statusCode).toBe(200);
        expect(nextChangePageResponse.json().resetRequired).toBe(false);
        await signalHandlers.get("SIGTERM")?.();

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
            notes: "Updated through Actual web interface",
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
          expect(downloadedTransfer).toBeUndefined();

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
        encryptedCompatibilityFixture = {
          budgetId: budget.groupId,
          budgetName: budget.name,
          transactionId: splitParent.id,
        };
      } finally {
        await firstDevice.close();
      }
    }, 60_000);

    it("reads an end-to-end encrypted synthetic budget through the API adapter", async () => {
      if (!serverURL || !serverPassword || !firstDataDir) {
        throw new Error("Actual compatibility test configuration is missing");
      }
      if (!database || !compatibilityDatabaseUrl) {
        throw new Error("Actual compatibility database is not initialized");
      }

      const fixture = encryptedCompatibilityFixture;
      if (!fixture) {
        throw new Error("The synthetic Actual budget fixture was not captured");
      }
      const encryptedBudgetPassword = "synthetic-encryption-test-password";
      const ownerDataDir = path.join(
        firstDataDir,
        `encrypted-owner-${randomUUID()}`,
      );
      await mkdir(ownerDataDir, { recursive: true });

      const encryptedOwner = createActualSession({
        serverURL,
        password: serverPassword,
        dataDir: ownerDataDir,
      });
      let encryptedBudgetId: string | undefined;
      try {
        await encryptedOwner.runBudget(
          fixture.budgetId,
          async () => {
            if (!Actual.internal) {
              throw new Error("Actual encryption handler is unavailable");
            }
            const result = await Actual.internal.send("key-make", {
              password: encryptedBudgetPassword,
            });
            if (result.error) {
              throw new Error("Actual could not encrypt the synthetic budget");
            }
          },
          { synchronizeAfterWrite: true },
        );

        const budgets = await encryptedOwner.execute(() => Actual.getBudgets());
        const encrypted = budgets.find(
          (item) =>
            item.name === fixture.budgetName && Boolean(item.encryptKeyId),
        );
        if (!encrypted?.groupId) {
          throw new Error(
            "Encrypted Actual budget was not registered for sync",
          );
        }
        encryptedBudgetId = encrypted.groupId;
      } finally {
        await encryptedOwner.close();
      }
      if (!encryptedBudgetId) {
        throw new Error("Encrypted Actual budget ID is unavailable");
      }

      const pairing = await database.deviceAuth.createPairingCode({
        principalId: `encrypted-owner-${randomUUID()}`,
        budgetIds: [encryptedBudgetId],
      });
      const device = await database.deviceAuth.pair({
        code: pairing.code,
        deviceName: "Synthetic encrypted compatibility client",
      });
      if (!device) {
        throw new Error(
          "Could not pair the encrypted compatibility test client",
        );
      }

      const apiDataDir = path.join(
        firstDataDir,
        `encrypted-api-${randomUUID()}`,
      );
      await mkdir(apiDataDir, { recursive: true });
      const encryptedApp = await runApi(
        {
          DATABASE_URL: compatibilityDatabaseUrl,
          PAIRING_CODE_HASH_KEY: pairingCodeHashKey,
          ACTUAL_SERVER_URL: serverURL,
          ACTUAL_SERVER_PASSWORD: serverPassword,
          ACTUAL_API_DATA_DIR: apiDataDir,
          HOST: "127.0.0.1",
          PORT: String(await findAvailablePort()),
        },
        createPostgresDatabase,
        () => undefined,
      );

      try {
        const authorizationHeaders = {
          authorization: `Bearer ${device.accessToken}`,
        };
        const catalog = await encryptedApp.inject({
          method: "GET",
          url: "/api/v1/budgets",
          headers: authorizationHeaders,
        });
        expect(catalog.statusCode).toBe(200);
        expect(catalog.json().budgets).toMatchObject([
          {
            budgetId: encryptedBudgetId,
            name: fixture.budgetName,
            isEncrypted: true,
          },
        ]);

        const snapshotPath = `/api/v1/budgets/${encryptedBudgetId}/snapshot`;
        const missingPassword = await encryptedApp.inject({
          method: "GET",
          url: snapshotPath,
          headers: authorizationHeaders,
        });
        expect(missingPassword.statusCode).toBe(502);

        const snapshot = await encryptedApp.inject({
          method: "GET",
          url: snapshotPath,
          headers: {
            ...authorizationHeaders,
            "x-budget-encryption-password": encryptedBudgetPassword,
          },
        });
        expect(snapshot.statusCode).toBe(200);
        expect(
          snapshot
            .json()
            .records.find(
              (record: { type: string; id: string }) =>
                record.type === "transaction" &&
                record.id === fixture.transactionId,
            ),
        ).toBeDefined();

        const transactionRead = await encryptedApp.inject({
          method: "GET",
          url: `/api/v1/budgets/${encryptedBudgetId}/transactions/${fixture.transactionId}`,
          headers: {
            ...authorizationHeaders,
            "x-budget-encryption-password": encryptedBudgetPassword,
          },
        });
        expect(transactionRead.statusCode).toBe(200);
        expect(transactionRead.json().transaction).toMatchObject({
          id: fixture.transactionId,
        });
      } finally {
        await encryptedApp.close();
      }
    }, 60_000);
  },
);

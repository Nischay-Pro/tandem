import {
  BudgetMaintenanceInputError,
  BudgetScheduleChangedError,
  type BudgetMaintenanceApiDependencies,
  type BudgetMaintenanceApiPort,
  validateBudgetScheduleEdit,
  writeOptions,
} from "./budget-maintenance-contracts.js";

export function createScheduleOperations({
  locks,
  session,
  client,
}: BudgetMaintenanceApiDependencies): Pick<
  BudgetMaintenanceApiPort,
  | "renameSchedule"
  | "editSchedule"
  | "skipSchedule"
  | "postSchedule"
  | "deleteSchedule"
> {
  return {
    async renameSchedule(budgetId, scheduleId, name, encryptionPassword) {
      const normalizedName = name.trim();
      if (!normalizedName || normalizedName.length > 100) {
        throw new BudgetMaintenanceInputError(
          "Schedule names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            const schedule = schedules.find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
            }
            const duplicate = schedules.some(
              (candidate) =>
                candidate.id !== scheduleId &&
                candidate.name?.trim() === normalizedName,
            );
            if (duplicate) {
              throw new BudgetMaintenanceInputError(
                "A schedule with this name already exists",
              );
            }
            // A name-only edit must keep an occurrence that was skipped.
            await client.updateSchedule(
              scheduleId,
              { name: normalizedName },
              false,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    async editSchedule(budgetId, scheduleId, edit, encryptionPassword) {
      validateBudgetScheduleEdit(edit);
      const normalizedName = edit.name?.trim();
      if (
        edit.name !== undefined &&
        (!normalizedName || normalizedName.length > 100)
      ) {
        throw new BudgetMaintenanceInputError(
          "Schedule names must be between 1 and 100 characters",
        );
      }
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            const schedule = schedules.find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetMaintenanceInputError(
                "Choose an active schedule in this budget",
              );
            }
            if (
              normalizedName &&
              schedules.some(
                (candidate) =>
                  candidate.id !== scheduleId &&
                  candidate.name?.trim() === normalizedName,
              )
            ) {
              throw new BudgetMaintenanceInputError(
                "A schedule with this name already exists",
              );
            }

            const fields: Parameters<typeof client.updateSchedule>[1] = {};
            if (normalizedName !== undefined) fields.name = normalizedName;
            if (edit.accountId !== undefined) {
              const account = (await client.getAccounts()).find(
                (candidate) => candidate.id === edit.accountId,
              );
              if (!account || account.closed) {
                throw new BudgetMaintenanceInputError(
                  "Choose an open account in this budget",
                );
              }
              fields.account = edit.accountId;
            }
            if (edit.payeeId !== undefined) {
              const payee = (await client.getPayees()).find(
                (candidate) => candidate.id === edit.payeeId,
              );
              if (!payee) {
                throw new BudgetMaintenanceInputError(
                  "Choose a payee in this budget",
                );
              }
              fields.payee = edit.payeeId;
            }
            if (edit.amountMinor !== undefined) {
              fields.amount = edit.amountMinor;
            } else if (edit.amountRangeMinor !== undefined) {
              fields.amount = edit.amountRangeMinor;
            }
            if (edit.amountOp !== undefined) fields.amountOp = edit.amountOp;
            if (edit.date !== undefined) {
              fields.date = edit.date as NonNullable<typeof fields.date>;
            }

            await client.updateSchedule(
              scheduleId,
              fields,
              edit.accountId !== undefined || edit.date !== undefined,
            );
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    skipSchedule(budgetId, scheduleId, expectedNextDate, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetScheduleChangedError();
            }
            if (
              typeof schedule.date !== "object" ||
              schedule.date === null ||
              !("frequency" in schedule.date)
            ) {
              throw new BudgetMaintenanceInputError(
                "A one-time schedule has no next date to skip",
              );
            }
            if (schedule.next_date !== expectedNextDate) {
              throw new BudgetScheduleChangedError();
            }
            const internal = client.internal;
            if (!internal) {
              throw new Error("Actual schedule actions are unavailable");
            }
            await internal.send("schedule/skip-next-date", { id: scheduleId });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    postSchedule(budgetId, scheduleId, expectedNextDate, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedule = (await client.getSchedules()).find(
              (candidate) => candidate.id === scheduleId,
            );
            if (!schedule || schedule.completed) {
              throw new BudgetScheduleChangedError();
            }
            if (schedule.next_date !== expectedNextDate) {
              throw new BudgetScheduleChangedError();
            }
            if (!schedule.account) {
              throw new BudgetMaintenanceInputError(
                "Choose an account before posting this schedule",
              );
            }
            const internal = client.internal;
            if (!internal) {
              throw new Error("Actual schedule actions are unavailable");
            }
            await internal.send("schedule/post-transaction", {
              id: scheduleId,
            });
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
    deleteSchedule(budgetId, scheduleId, encryptionPassword) {
      return locks.withBudgetLock(budgetId, () =>
        session.runBudget(
          budgetId,
          async () => {
            const schedules = await client.getSchedules();
            if (!schedules.some((candidate) => candidate.id === scheduleId)) {
              throw new BudgetMaintenanceInputError(
                "Schedule does not exist in this budget",
              );
            }
            await client.deleteSchedule(scheduleId);
          },
          writeOptions(encryptionPassword),
        ),
      );
    },
  };
}

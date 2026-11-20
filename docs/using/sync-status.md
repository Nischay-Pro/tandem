# Sync status names

These labels show whether a change has reached Actual and whether you need to
make a choice.

| Status                   | Meaning                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| **Synced**               | Actual confirmed the change and the phone read back the saved result.                                                    |
| **Pending Changes**      | One or more local changes still need to be sent or confirmed by the Tandem server.                                       |
| **Saved on this device** | The change is stored locally. The server has not confirmed it yet.                                                       |
| **Needs review**         | Actual changed or removed the same record while this edit was waiting. Compare the versions and choose what to keep.     |
| **Status unknown**       | The phone did not receive a final server response. The change may have reached Actual. Check its status before retrying. |
| **Rejected**             | The server confirmed that Actual did not apply the change. Open it to see the available actions.                         |

If a rejected budget change offers **Try again**, use it after the underlying
problem is fixed. If the app offers **Refresh budget** or **Check Actual**,
follow that action to get the latest information before deciding what to do.

**Waiting for your Tandem server to be updated** means the edit is saved on
your device and needs a feature the server has yet to report. Ask your operator
to [update Tandem](../deploy/upgrade.md). The edit syncs automatically when the
server reports support. If Sync Center says **Update the Tandem app**, install
a [newer app build](install-app.md).

Conflict choices need a connection and are complete only after the server
confirms them. If the phone goes offline, keep the item and reconnect before
trying again. Do not clear app data, uninstall, or sign out while changes are
pending, need review, or have an unknown status.

See [Use Tandem offline and review conflicts](offline-sync-and-conflicts.md) for
the full flow.

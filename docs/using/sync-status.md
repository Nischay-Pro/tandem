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
| **Rejected**             | The server confirmed that Actual did not apply the change. Correct the problem or remove the local edit.                 |

Conflict choices need a connection and are complete only after the server
confirms them. If the phone goes offline, keep the item and reconnect before
trying again. Do not clear app data, uninstall, or sign out while changes are
pending, need review, or have an unknown status.

See [Use Tandem offline and review conflicts](offline-sync-and-conflicts.md) for
the full flow.

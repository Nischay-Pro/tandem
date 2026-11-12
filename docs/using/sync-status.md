# Sync status names

| Status                   | Meaning                                                                                                                       |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| **Synced**               | Actual confirmed the change and the phone read back the saved result.                                                         |
| **Pending Changes**      | One or more local changes still need to be sent or confirmed by the Tandem server.                                            |
| **Saved on this device** | The change is stored locally. The server has not confirmed it yet.                                                            |
| **Needs review**         | Actual changed or removed the same record while this edit was waiting. Review the server result before choosing what to keep. |
| **Status unknown**       | The phone did not receive a final server response. Check the operation status before retrying or removing it.                 |
| **Rejected**             | The server confirmed that Actual did not apply the change. Correct the problem or remove the local edit.                      |

Do not clear app data while changes are pending, need review, or have an unknown
status. See [Offline sync and conflicts](offline-sync-and-conflicts.md) for the
full flow.

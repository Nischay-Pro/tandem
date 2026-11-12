# Test Actual compatibility

For contributors checking Tandem against the Actual server and API versions
listed in [Actual compatibility](../concepts/actual-compatibility.md). This
test uses a disposable Actual server and synthetic budgets.

## Run the compatibility test

1. **Start the isolated compatibility test** from the repository root:

   ```sh
   ./scripts/test-actual-compatibility.sh
   ```

   Docker Compose starts a pinned Actual server and PostgreSQL on a private
   network, runs the adapter tests, then removes the test services and data.
   It does not connect to a configured Actual server or use a real budget.

   **Check:** the command exits successfully. If Docker cannot start the
   services, confirm Docker is running and that the test's local port is free,
   then retry. If an adapter assertion fails, check the pinned-version notes
   in [Actual compatibility](../concepts/actual-compatibility.md) before
   changing the adapter.

## Verified coverage

| Area                 | What the disposable test checks                                                                                                           | Limit                                                                                                                     |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Transactions         | Create, update, sync, read from an independent workspace, and delete.                                                                     | One ordinary transaction path.                                                                                            |
| Concurrent edits     | Two workspaces edit the same transaction amount and converge after sync.                                                                  | Confirms observed convergence; it does not establish which edit wins or cover every field and entity.                     |
| Transfers and splits | Create paired transfer legs, update/delete transfers, create splits, edit split parents and children, and detect changed split structure. | Exercises the pinned Actual release only.                                                                                 |
| Encrypted budgets    | Reads fail without the budget password and succeed when the password is supplied.                                                         | Test setup uses an internal Actual handler to create an encrypted synthetic fixture; production reads use the public API. |
| Session token        | Reads the catalog and performs a transaction write using a token issued by the disposable server's password login.                        | Does not verify an OpenID-only server or an OpenID-issued token's budget read/write path.                                 |
| Large snapshot       | Reads 15,000 synthetic transactions, then repeats after opening a fresh API session from the same local workspace.                        | Server-side read and JSON serialization only; it does not measure a process restart, network transfer, or phone parsing.  |

One local run returned 15,116 records and 8,644,137 JSON bytes. The first read
took 436 ms and the reopened-session read took 453 ms with the same transaction
revision digest. Treat these numbers as one server-side datapoint, not a mobile
performance guarantee. Measure transfer and parsing on representative phones
and networks before changing sync intervals.

The session-token test verifies a token issued by the disposable server's
password login. It does not test an OpenID-only Actual configuration or an
OpenID-issued token's budget read/write path. For the compatibility boundary,
see [Actual compatibility](../concepts/actual-compatibility.md). For general
developer checks, see [Test Tandem](testing.md).

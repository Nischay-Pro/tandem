# Actual compatibility

The API adapter is version-gated to the exact release pair that passed its
current compatibility baseline: **Actual server 26.11.0 + `@actual-app/api`
26.11.0**. Both are pinned in the compatibility test setup and API dependency
manifest. A server with another release is rejected before the adapter can
open a budget. The integration test establishes login, version check, synthetic
budget/account/transaction creation, ordinary transaction update and deletion,
transfer creation, split creation and readback, server sync, and readback through
independent local workspaces. It also verifies the adapter's encrypted-budget
read path, split parent and child edits/deletes, and paired transfer edits and
deletes.

The version gate calls the official package's `getServerVersion()` method. In
the pinned source, that method reads the server build version, is available
without an open budget, and reports `no-server` or `network-failure` instead of
a version when it cannot verify the server. It does not scrape the web UI or
inspect Actual's private SQLite files.

## Adapter boundary

`@actual-app/api` is a Node.js library, not an HTTP REST API. Its documented
model runs Actual's budget engine against a local SQLite working copy and uses
Actual's sync server to exchange changes. The Tandem adapter therefore calls
the package rather than editing Actual's server files or internal database.

The package exposes a process-global API session. `ActualSession` serializes
operations through one queue, checks the server release once per session, and
calls `downloadBudget()` before each budget operation. Successful writes can
request an explicit `sync()` after the operation. A failed sync is surfaced as
an uncertain outcome; the adapter does not retry it. The durable operation
journal and write recovery policy are separate work in ABC-004 and ABC-005.

## Budget read projection

The pinned API exposes `getAccounts`, `getAccountBalance`, account and category
group reads, `getCategories`, `getPayees`, `getBudgetMonths`, and
`getBudgetMonth`. Transactions use the package's public `q()`/`aqlQuery()`
methods with `splits: "grouped"`, ordered by stable ID and paged with AQL's
`limit`/`offset`. The projection does not open Actual's SQLite database. Its
compatibility test exercises the complete snapshot and change-feed routes with
synthetic accounts, visible and hidden categories, budget months, transactions,
split children, and transfer legs.

Actual does not expose an incremental change feed through this API. Tandem
refreshes and diffs a supported-API snapshot into PostgreSQL. Each successful
refresh stores current records, emits entity changes and deletion tombstones,
and advances the per-budget cursor in one PostgreSQL transaction. The change
feed is therefore a Tandem projection, not Actual's synchronization
protocol; refresh sees the copy downloaded for that request, with external
edits appearing on a subsequent refresh.

For an end-to-end encrypted budget, callers pass its encryption password to the
specific `runBudget()` call that opens the budget. It is separate from the
Actual server login credential. The adapter forwards it to Actual's
`downloadBudget()` call; the Tandem write journal does not store it. The
compatibility test verifies that the catalog identifies the encrypted budget,
a snapshot request without the password fails, and snapshot and transaction
reads with the password succeed. The Actual API opens a local working copy for
these operations, while Tandem stores readable projected records in
PostgreSQL. Actual's encryption therefore protects the budget on Actual's sync
server, but does not make the Tandem API service an encrypted relay; deployers
must trust and protect the service, its readable PostgreSQL projection, local
Actual working copy, and the phones' offline caches. Production backups also
contain these files: the backup script writes mode-600 plaintext by default,
or can encrypt the exported files with GnuPG when configured. File permissions
limit access to the operator account but do not encrypt backup contents.

The adapter must be the only owner of the package singleton in its Node.js
process. This queue coordinates one Tandem API process. Cross-process and
multi-replica serialization will use the PostgreSQL coordination layer; it is
not claimed by this in-process session.

## Disposable compatibility test

Run:

```sh
./scripts/test-actual-compatibility.sh
```

The script starts the pinned official Actual image on an internal Docker
network, initializes a test-only password through the same bootstrap route used
by Actual's UI, and runs against synthetic budget data in isolated temporary
workspaces. It removes those workspaces and stops its isolated Compose project
when the check completes. It never connects to Tandem's configured production
Actual server or uses a real budget.

The test creates, updates, and deletes a synthetic ordinary transaction
through `@actual-app/api`. It syncs the update and verifies it from a second
local data directory, deletes it from that second client, syncs again, then
verifies its absence from a third independent local data directory.

The suite also gives two independent local workspaces the same baseline
transaction, edits its amount in both workspaces without synchronizing the
first edit, then synchronizes the copies in sequence. Actual reconciles the
same-field edits to one amount, and both workspaces converge on that value.
The test intentionally does not prescribe which edit wins: it verifies Actual's
observed merge and convergence behavior, not a stable conflict-winner contract.
This covers one offline transaction-amount conflict; it does not establish
concurrent network-write safety, behavior for every field or entity, or a
publicly supported incremental change cursor.

The test also creates a synthetic transfer payee, sets its `transfer_acct`
through `updatePayee`, and adds a transaction with `runTransfers: true`. Actual
creates the paired transaction and each leg points to the other leg's ID; the
outgoing leg keeps that link after download into the second workspace. The
reconciliation flow edits one transfer leg through Actual's public
`updateTransaction` method and verifies that Actual updates its counterpart,
then deletes the other leg and verifies that Actual removes both. Split
creation is verified through Actual's grouped transaction result and public AQL
read. The adapter edits split parents and children through Actual's public
transaction methods. Parent deletion cascades through Actual's split group;
child deletion follows Actual's native split recalculation behavior. A change to
the split's parent/child structure between read and write is recorded as a
conflict.

The encrypted fixture uses the pinned package's internal `key-make` handler to
enable encryption on synthetic data because the public API has no encryption
toggle. This is test setup only; production adapter reads and writes use the
public API methods and do not access Actual's SQLite files or internal
database.

The same run obtains a disposable Actual server session token from its login
endpoint, initializes a separate `@actual-app/api` workspace with that token
and no server password, and reads the budget catalog. This verifies the
supported session-token path against the pinned Actual release; it does not
exercise a third-party OpenID provider.

## Verified upstream evidence

- Official package docs say Actual has no HTTP REST API and that the Node API
  works against a local data copy: <https://actualbudget.org/docs/api/>.
- The 26.11.0 `getServerVersion()` implementation and no-open-budget test are
  in the official source: <https://github.com/actualbudget/actual/tree/v26.11.0/packages/api>.
- The official Docker image repositories and version tags are documented at
  <https://actualbudget.org/docs/install/docker/>.
- The package manifest declares version 26.11.0, Node `>=22`, and MIT license.
  Tandem pins it exactly and does not copy Actual source code.

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
journal and write recovery policy are documented in [the architecture and trust notes](architecture-and-trust.md).

## Budget snapshot cache

The pinned API exposes `getAccounts`, `getAccountBalance`, account and category
group reads, `getCategories`, `getPayees`, `getBudgetMonths`, and
`getBudgetMonth`. Transactions use the package's public `q()`/`aqlQuery()`
methods with `splits: "grouped"`, ordered by stable ID and paged with AQL's
`limit`/`offset`. Snapshot reads do not open Actual's SQLite database. The
compatibility test exercises complete snapshot reads with synthetic accounts,
visible and hidden categories, budget months, transactions, split children,
and transfer legs.

Actual does not expose an incremental change cursor through this API. Tandem
keeps the latest supported-API snapshot in a bounded in-memory cache so it can
page a consistent generation. When the record set changes, the cache advances
the generation and clients replace their remote baseline with a full snapshot.
An unchanged check returns no row-level changes. API restart or cache eviction
also invalidates the generation and makes clients resync; pending Room overlays
remain local. The cache is per process and currently holds two budgets, so run
one API process rather than multiple replicas behind a load balancer.

For an end-to-end encrypted budget, callers pass its encryption password to the
specific `runBudget()` call that opens the budget. It is separate from the
Actual server login credential. The adapter forwards it to Actual's
`downloadBudget()` call; the Tandem write journal does not store it. The
compatibility test verifies that the catalog identifies the encrypted budget,
a snapshot request without the password fails, and snapshot and transaction
reads with the password succeed. The Actual API opens a local working copy for
these operations. Tandem does not persist a second readable budget projection
in PostgreSQL, but its write journal can still contain transaction values,
notes, and base snapshots until that journal is removed. Actual's encryption
therefore protects the budget on Actual's sync server, but does not make the
Tandem API service an encrypted relay; deployers must trust and protect the
service, its PostgreSQL journal and credentials, local Actual working copy,
and the phones' offline caches. Production backups also contain these files:
the backup script writes mode-600 plaintext by default,
or can encrypt the exported files with GnuPG when configured. File permissions
limit access to the operator account but do not encrypt backup contents.

The adapter must be the only owner of the package singleton in its Node.js
process. This queue coordinates one Tandem API process. Cross-process and
multi-replica serialization will use the PostgreSQL coordination layer; it is
not claimed by this in-process session.

The adapter's disposable integration test and its verified coverage are
documented in [Actual compatibility tests](../develop/actual-compatibility-tests.md).

## Verified upstream evidence

- Official package docs say Actual has no HTTP REST API and that the Node API
  works against a local data copy: <https://actualbudget.org/docs/api/>.
- The 26.11.0 `getServerVersion()` implementation and no-open-budget test are
  in the official source: <https://github.com/actualbudget/actual/tree/v26.11.0/packages/api>.
- The official Docker image repositories and version tags are documented at
  <https://actualbudget.org/docs/install/docker/>.
- The package manifest declares version 26.11.0, Node `>=22`, and MIT license.
  Tandem pins it exactly and does not copy Actual source code.

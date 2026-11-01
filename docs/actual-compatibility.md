# Actual compatibility

The API adapter is version-gated to the exact release pair that passed its
current compatibility baseline: **Actual server 26.10.0 + `@actual-app/api`
26.10.0**. Both are pinned in the compatibility test setup and API dependency
manifest. A server with another release is rejected before the adapter can
open a budget. The integration test establishes login, version check, synthetic
budget/account/transaction creation, a transaction update, server sync, and
read-back through a separate local workspace. Other operations and encrypted
budget behavior require their own tests before being declared supported.

The version gate calls the official package's `getServerVersion()` method. In
the pinned source, that method reads the server build version, is available
without an open budget, and reports `no-server` or `network-failure` instead of
a version when it cannot verify the server. It does not scrape the web UI or
inspect Actual's private SQLite files.

## Adapter boundary

`@actual-app/api` is a Node.js library, not an HTTP REST API. Its documented
model runs Actual's budget engine against a local SQLite working copy and uses
Actual's sync server to exchange changes. The Companion adapter therefore calls
the package rather than editing Actual's server files or internal database.

The package exposes a process-global API session. `ActualSession` serializes
operations through one queue, checks the server release once per session, and
calls `downloadBudget()` before each budget operation. Successful writes can
request an explicit `sync()` after the operation. A failed sync is surfaced as
an uncertain outcome; the adapter does not retry it. The durable operation
journal and write recovery policy are separate work in ABC-004 and ABC-005.

For an end-to-end encrypted budget, callers pass its encryption password only
to the specific `runBudget()` call. It is separate from the Actual server login
credential. The adapter does not persist that password. Encrypted-budget
compatibility remains unverified until its dedicated test is added.

The adapter must be the only owner of the package singleton in its Node.js
process. This queue coordinates one Companion API process. Cross-process and
multi-replica serialization will use the PostgreSQL coordination layer; it is
not claimed by this in-process session.

## Disposable compatibility test

Run:

```sh
./scripts/test-actual-compatibility.sh
```

The script starts the pinned official Actual image on an internal Docker
network, initializes a test-only password through the same bootstrap route used
by Actual's UI, and runs against synthetic budget data. It uses only
`.dev-tmp/actual/compat-server` and `.dev-tmp/actual/compat-client`, then clears
those paths and stops its isolated Compose project. It never connects to the
Companion's configured production Actual server or uses a real budget.

The test creates and updates a synthetic transaction through
`@actual-app/api`, syncs it, shuts down that local client, and downloads the
result into an independent second local data directory. The updated amount and
notes must be present there.

## Verified upstream evidence

- Official package docs say Actual has no HTTP REST API and that the Node API
  works against a local data copy: <https://actualbudget.org/docs/api/>.
- The 26.10.0 `getServerVersion()` implementation and no-open-budget test are
  in the official source: <https://github.com/actualbudget/actual/tree/v26.10.0/packages/api>.
- The official Docker image repositories and version tags are documented at
  <https://actualbudget.org/docs/install/docker/>.
- The package manifest declares version 26.10.0, Node `>=22`, and MIT license.
  The companion pins it exactly and does not copy Actual source code.

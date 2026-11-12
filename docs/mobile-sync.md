# Mobile sync

Tandem is local-first: once a budget has synced, the app opens its Room cache
without waiting for the server. The sync coordinator updates that cache in the
background, and the screens observe Room directly.

## How the pieces fit

```mermaid
flowchart LR
  UI[Compose UI] --> DB[(Room cache)]
  DB <--> Sync[Sync coordinator]
  Worker[Android WorkManager or iOS background task] --> Sync
  Sync <--> Client[Generated API client]
  Client <--> API[Tandem API]
  API <--> Projection[(Server projection and change feed)]
  API <--> ActualAPI["Actual API package in Tandem"]
  ActualAPI <--> Actual[Actual Budget server]
```

The UI reads local records. The coordinator sends changes through the Tandem
API, which uses Actual's supported Node API package (`@actual-app/api`) to read
and write budgets, and maintains the server-side projection used for snapshots
and change pages. The package runs inside the Tandem API service, not as a
separate network service. Tandem does not write directly to Actual's database.

## First sync

```mermaid
sequenceDiagram
  actor Person
  participant App as Mobile app
  participant API as Tandem API
  participant Actual as Actual Budget server
  participant Vault as Server credential store
  participant ActualApi as Actual API package in Tandem
  participant Room
  Person->>App: Enter Actual server and sign-in details
  App->>API: Sign in to Actual
  API->>Actual: Verify credentials or complete sign-in
  Actual-->>API: Actual session token and user identity
  API->>Vault: Store Actual session token encrypted
  Vault-->>API: Token stored
  API->>ActualApi: Read this user's budget catalog
  ActualApi->>Actual: List accessible budgets
  Actual-->>ActualApi: Budget catalog
  ActualApi-->>API: Budget IDs, names and encryption state
  API-->>App: Device token and authorized budget IDs
  App->>API: Load budget names and details
  API->>ActualApi: Read authorized budget catalog
  ActualApi->>Actual: List accessible budgets
  Actual-->>ActualApi: Budget catalog
  ActualApi-->>API: Authorized budget catalog
  API-->>App: Available budgets
  Person->>App: Choose a budget
  opt Encrypted budget
    Person->>App: Enter budget password
  end
  App->>API: Request full snapshot
  API->>ActualApi: Download and read selected budget
  ActualApi->>Actual: Sync and read through supported API
  Actual-->>ActualApi: Budget records and display settings
  ActualApi-->>API: Snapshot records and display settings
  API-->>App: Snapshot and sync checkpoint
  App->>Room: Apply snapshot transactionally
  Room-->>App: Notify screens from local data
```

After a successful first sync, Tandem remembers the selected budget locally.
On later launches it opens that cached budget immediately, even if Tandem or
Actual is unreachable. Sign in again only if the saved device credential has
expired, been revoked, or cleared from the device.

## Later refreshes

Manual refresh, app resume, restored network access, and eligible scheduled
work all use the same coordinator.

```mermaid
flowchart TD
  Trigger[Manual, resume, network, or scheduled trigger] --> Ready{Sync enabled and session available?}
  Ready -->|No| Cache[Keep showing the local budget]
  Ready -->|Yes| Cursor[Read saved cursor and pending writes]
  Cursor --> Valid{Cursor still valid?}
  Valid -->|Yes| Pages[Fetch change-feed pages]
  Valid -->|No: expired or reset| Snapshot[Fetch a fresh snapshot]
  Pages --> Apply[Apply records and cursor in one Room transaction]
  Snapshot --> Apply
  Apply --> UI[Room flows update the open screens]
```

Each paged run holds the server's `throughCursor` fixed. Tandem applies a page
and its next cursor together; deleted records become tombstones. If the cursor
expires or the server generation changes, the coordinator replaces the remote
baseline from a fresh snapshot while retaining local pending edits. Every
device keeps its own cursor over the shared change feed. See the
[API contract](api/README.md#transaction-reads-and-reconciliation) for feed
retention and reset behavior.

## Saving edits and resolving errors

Saving a transaction writes to Room first. The UI shows that local change
immediately while the durable outbox waits for the server.

```mermaid
stateDiagram-v2
  [*] --> queued
  queued --> sending: sync starts
  sending --> committed: Actual read-back confirms write
  sending --> conflict: remote revision changed
  sending --> rejected: definitive validation error
  sending --> uncertain: timeout or cancellation
  uncertain --> committed: operation lookup finds write
  uncertain --> queued: person explicitly retries
  conflict --> superseded: edit rebased on latest revision
  conflict --> discarded: person keeps Actual's copy
  rejected --> discarded: person removes local draft
  uncertain --> discarded: person removes local draft
```

An uncertain write is checked by operation ID before the next sync. Tandem
never automatically posts it again; retry is explicit and reuses the same
operation ID and idempotency key. Conflicts keep the local draft for review.
The person can keep Actual's version or rebase the edit on the newest server
revision. If Actual deleted the transaction, the local conflict must be
discarded.

Transaction creates use a stable `tandem:<uuid>` identifier. The API passes it
to Actual as `imported_id` and reads the created row back before committing the
journal entry. Updates, deletes, snapshots, and change-feed reconciliation use
that same identifier. Transaction edits support amount, payee, category, date,
account, and notes. Amounts stay in integer minor units.

## Offline behavior

Offline access starts after a successful sync on this device. Tandem opens the
saved copy for the same server and Actual account; it does not use a different
account's cache. Local changes stay on this device until Tandem can confirm
them with Actual. The **Pending Changes** screen shows queued work and changes
that need review.

| Area                                            | Works offline                                                                                                                                           | Needs a connection                                                                                                            |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Budget, accounts, activity, search, and reports | Read the saved budget, transactions, account balances, schedules, and reports. Search reads the local transaction set.                                  | First sign-in, loading a budget that has never synced here, and refreshing data from Actual.                                  |
| Transactions                                    | Add, edit, and delete transactions in the local outbox. A transaction using a payee or account being created offline waits for that item to sync first. | Actual must confirm queued writes before they are considered synced. Conflicts stay available for review.                     |
| Payees and categories                           | Create payees and queue category or payee changes, including supported group, visibility, and assignment changes.                                       | Tandem sends the queued operations to Actual and checks the result when online.                                               |
| Budget plan                                     | Queue assigned amounts, carryover, copy-last-month, templates, next-month holds, hold resets, and moves between categories.                             | Actual must accept the queued plan changes before they are shared with other devices.                                         |
| Accounts                                        | Queue account creation, rename, close, reopen, and ordering changes. Local transactions can refer to a newly queued account.                            | Account changes are applied and confirmed by Actual when connected.                                                           |
| Schedules                                       | View synced schedules and queue edits, deletes, posts, and skips for existing schedules.                                                                | Add a new schedule in Actual Budget; Tandem does not currently create schedules. Actual must confirm queued schedule actions. |
| Statement import                                | —                                                                                                                                                       | A connection to Tandem and Actual is required to check duplicate IDs and import statement rows.                               |
| Bank sync and sign-in                           | The cached budget remains readable.                                                                                                                     | Sign-in, budget discovery, and bank sync through Actual require a connection.                                                 |
| Encrypted budgets                               | Read an already cached copy.                                                                                                                            | A budget password is required for foreground sync. Tandem never saves that password; background sync skips encrypted budgets. |
| Currency and reports                            | Use Actual's saved currency settings and calculate reports from Room's indexed transaction columns.                                                     | No connection is needed to display the cached values.                                                                         |

When a budget has no saved selection, Tandem opens the most recently synced
local budget. A prior successful sync is enough to open the local copy, even if
its change-feed cursor must be rebuilt later. Tandem uses saved catalog
metadata for its name and encryption state, and discovers cached budgets from
Room if that metadata is missing.

## Refresh schedule

| Trigger                    | Android                                                                                | iOS                                                                         |
| -------------------------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Foreground                 | Manual refresh; resume and network-restoration refresh when enabled                    | Manual refresh; app-active and network-restoration refresh when enabled     |
| Periodic                   | Unique WorkManager request with a six-hour interval and a connected-network constraint | `BGAppRefreshTask` with a six-hour earliest start; iOS decides when it runs |
| Metered network preference | Can require an unmetered network                                                       | Waits for a path that is not marked expensive or constrained                |
| Encrypted budgets          | Background work skips these budgets because passwords are not stored                   | Background work skips these budgets for the same reason                     |

Android and iOS may defer scheduled work to satisfy their power and network
rules. iOS scheduling has a native host target, but its background runtime
behavior still needs a macOS simulator or device check. Foreground sync can use
the password entered to unlock an encrypted budget.

Resume and network events coalesce while a sync is active. Successful attempts
have a 30-second per-budget cooldown; a failed attempt can queue one follow-up.
Android workers and the open app may use separate database instances, so Room
multi-instance invalidation keeps the visible screens current.

## Credentials and encryption

| Item               | Handling                                                                                        |
| ------------------ | ----------------------------------------------------------------------------------------------- |
| Actual password    | Sent for sign-in; never stored on the device or server                                          |
| OpenID return code | One-use and short-lived; exchanged immediately for a device credential                          |
| Device credential  | Android stores an AES-GCM record with its key in Keystore; iOS uses a device-only Keychain item |
| Budget password    | Sent only on requests that need it; never stored in Room or the outbox                          |
| Cached budget      | Stored locally so the selected budget remains readable offline                                  |

Actual-encrypted budgets are decrypted by Actual's API for Tandem's server-side
projection and the mobile cache. Actual's encryption therefore does not protect
those working copies. See [the architecture notes](architectural-design.md)
for the server trust boundary and the fully encrypted alternative.

“Forget this device” revokes the server token before removing the local
credential. If Tandem is unreachable, the credential stays available so the
revocation can be retried. Device tokens expire after 90 days of inactivity.

## Code map

| Module                                  | Responsibility                                                                             |
| --------------------------------------- | ------------------------------------------------------------------------------------------ |
| `mobile/data`                           | Room records, local overlays, change cursor, durable outbox, and sync coordinator          |
| `mobile/api-client`                     | Generated OpenAPI client and adapter for the data module                                   |
| `mobile/composeApp`                     | Shared Compose screens observing Room flows                                                |
| `mobile/androidApp` and `mobile/iosApp` | Platform hosts, secure credential stores, and scheduled work                               |
| `services/api`                          | Device authorization, budget projection, change feed, write journal, and Actual API access |

## Local checks

Run from `mobile/`:

```sh
./gradlew :data:jvmTest :data:koverVerify :data:ktlintCheck \
  :api-client:jvmTest :api-client:koverVerify :api-client:ktlintCheck
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 :composeApp:ktlintCheck \
  :androidApp:assembleDebug
```

The iOS simulator app and scheduled-refresh runtime checks require macOS.

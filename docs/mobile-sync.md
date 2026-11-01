# Mobile synchronization

The mobile modules currently provide local persistence and synchronization
libraries; the Android/iOS application UI and its screenshot workflow are the
next slice (ABC-009). Android and iOS clients will share the same coordinator,
repository, and generated API adapter.

## Components and entry point

- `mobile/data` owns the Room SQLite database, local/remote record overlays,
  change-feed checkpoint, durable write outbox, and `BudgetSyncCoordinator`.
- `mobile/api-client` contains the generated OpenAPI client and the handwritten
  `GeneratedBudgetSyncRemote` adapter that implements the data module's remote
  protocol.
- The app owns authentication, its database location, connectivity/lifecycle
  triggers, and presentation of sync/conflict state. Create one coordinator for
  a repository and call `synchronize(budgetId)` when the app starts, returns to
  the foreground, or reconnects. Calls through that coordinator are serialized.
  A caller can set `sendQueuedWrites = false` for a pull-only refresh.

Pass an encrypted budget's password to `synchronize` when needed. It is sent
only on the relevant budget requests and is not stored in the database or
outbox. Device bearer credentials belong in platform secure storage.

## Pulling a budget

The first sync, an invalid checkpoint, an expired cursor, or a server generation
reset starts with a complete snapshot. Applying the snapshot replaces the
remote baseline transactionally while retaining local pending writes and their
optimistic drafts. A snapshot captured against an older local checkpoint is
retried instead of overwriting newer changes.

Later syncs read the change feed from the saved decimal cursor. Each paginated
run keeps the server's `throughCursor` fixed, applies each page and its next
cursor in one database transaction, and continues until `hasMore` is false.
Upserts replace remote records; deletion events create tombstones. If the server
requires a reset or changes generation, the coordinator fetches a fresh
snapshot. The Companion's feed is shared server history, so independent device
databases each advance their own cursor over the same changes.

The change feed and snapshot behavior, including retention and generation
resets, is described in [the API contract guide](api/README.md#transaction-reads-and-reconciliation).

## Sending local edits

Local edits are stored optimistically with their base revision, base snapshot,
patch, operation UUID, and idempotency key. Unsent edits to one record can be
coalesced while they share the same base. The current Companion write endpoint
supports transaction updates and deletions only. Local creates are marked
`rejected` with `create-not-supported`; the coordinator does not send them.

After posting, the coordinator reads the authoritative transaction before
marking an update committed. A conflict keeps the local draft and stores the
latest remote transaction for review. Resolving a conflict means enqueueing a
new edit with the latest remote revision and snapshot; this atomically marks
the previous conflict `superseded` and queues the new edit. If the remote
transaction was deleted, discard the conflict instead of rebasing it.

HTTP 401, 403, and 404 responses leave the edit queued, record the error, and
fail that sync attempt so credentials or budget access can be fixed before the
next try. Definitive request errors (400, 409, or 422) mark the entry rejected
and remove its optimistic record overlay; the rejected outbox entry and error
remain available for review.

If a POST or its response has an uncertain outcome, the outbox entry remains
`uncertain` and the coordinator polls its operation ID. Before every sync,
interrupted `sending` entries are recovered as uncertain and polled, including
after process restart. It does not
automatically POST an uncertain operation again; if the operation cannot be
read, the UI can call `repository.retryUncertainWrite(operationId)` to
explicitly queue a retry with the same operation ID and idempotency key, or
`repository.discardWrite(operationId)` to remove the local draft. Discarding a
conflict, uncertain, rejected, or queued edit restores the current remote
projection when no other active write remains for that record. Canceling the
sync coroutine persists the write as uncertain, and
the next sync recovers and polls it. No uncertain operation is automatically
reposted.

Outbox states are `queued`, `sending`, `uncertain`, `conflict`, `committed`,
`rejected`, `superseded`, and `discarded`. Rejected and discarded edits do not
remain as active record overlays. Snapshot resets do not clear outbox entries.
The UI should surface pending, uncertain, conflict, rejected, and discarded
edits so users can understand what still needs attention.

## Local verification

From `mobile/`, run the JVM regression and coverage checks for the persistence,
coordinator, and generated-client adapter:

```sh
./gradlew :data:jvmTest :data:koverVerify :data:ktlintCheck \
  :api-client:jvmTest :api-client:koverVerify :api-client:ktlintCheck
```

Android and iOS target compilation is covered by the mobile CI job; iOS
simulator runtime tests require macOS. The local Android emulator configuration
is documented in the development setup, but screenshots require the ABC-009 UI.

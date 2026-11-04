# Mobile synchronization

The mobile project includes the shared Room-backed local-first data and sync
libraries, a Compose Multiplatform presentation module, and an installable
Android host app. Android and iOS use the same Tandem design system and
screens; the iOS host is `MainViewController()` in `mobile/composeApp`.

The shared app pairs devices through the generated API client, lists only
server-authorized budgets, and syncs snapshots and changes into Room on Android
and iOS. The budget overview, cached month/category plan, category group/detail,
account balances, account activity, and transaction activity read the selected
budget's local records. The Activity screen displays the newest 50 rows while
Room retains the complete synced transaction set; Sync Center reports the full
visible count. Initial setup selects the most recently active cached month when
the prior selection is unavailable.

App launch is local-first after a budget has synced once. Cache discovery,
preference loading, and device-credential restoration run independently. The
startup screen shows “Opening your budget” while local state is read, rather
than presenting pairing or welcome UI first. The app opens the previously
selected cached budget without waiting for the Tandem server, budget catalog,
or a working device credential. If there is no saved selection, it opens the
most recently synced cached budget. It uses the saved server catalog only to
recover names and encryption metadata, and filters that catalog to budgets
that really have a local snapshot. If the preference catalog is missing,
cached budgets are discovered from Room and labeled using their saved currency
when available.
The app can then attempt sync in the background of the open screen; a network
failure leaves the cached budget readable and offers retry through Sync Center.
Pairing is only needed again for remote access when the server says the device
credential is invalid.

Snapshot and first-page change responses also carry an allowlist of the
selected Actual budget's currency and display preferences. The mobile client
persists these settings beside that budget's local records. The API carries
Actual's own currency symbol, decimal places, default number format, and symbol
placement for the pinned `@actual-app/api` release; Android and iOS use these
values instead of their platform currency tables. An explicit budget
number-format preference takes precedence. If currency preferences are
disabled, Actual itself falls back to USD; if no number format is configured,
the phone locale supplies separators. The `hideFraction` preference applies
to displayed totals, while transaction edit fields retain the full editable
precision so a notes-only edit cannot silently change an amount.

The local Actual 26.10.0 load fixture contains a CAD budget with 2,182
transaction records and a USD budget with 13,164 records. Across the two
budgets, 15,000 rows are synthetic purchases spanning seven years. Pixel 10a
first-sync verification confirmed both budgets reached the `READY` state and
the records were stored locally. One first attempt for USD hit a transient
connection error; retry completed successfully. This test data and its seed
script live only under ignored `.dev-tmp/` paths.
The compatibility server also retains a separate older synthetic fixture. The
Pixel's current device credential is scoped only to the CAD and USD household
budget IDs, so the budget chooser shows exactly those two; the compatibility
fixture is outside its authorized scope.

Editing the amount and notes of an existing transaction enqueues an optimistic
local update and sends it through the sync coordinator. Conflict review can
keep Actual's copy or rebase and submit the device edit; uncertain and rejected
writes have explicit review, retry, and discard controls. These UI write flows
are wired to the API and local outbox. On the Pixel, an existing synthetic
Safeway transaction was changed from -$95.58 to -$100.00, confirmed by Actual,
then restored to -$95.58 and confirmed again. Conflict review was also tested:
the official Actual client changed that same row by one cent while the Pixel
held the prior revision; the stale device edit produced a conflict showing
both versions. Keeping Actual discarded the local draft, and the original
-$95.58 value was restored through the app and confirmed. New transactions use
a caller-generated `tandem:<uuid>` ID persisted as Actual's `imported_id`. The
projection and follow-up reads translate Actual's internal random ID back to
this stable ID, so the local optimistic record, outbox, snapshot, change feed,
reads, updates, and deletes all refer to one key. The server checks the ID is
unused and reads the created transaction back from Actual before committing the
journal entry. Sync preferences trigger the real coordinator for manual
refreshes and, when enabled, on returns to a budget. The local-data page reads
the full transaction count, cached account count, sync state, and
queued/conflicted outbox rows from Room. Refresh uses the same sync action;
clearing local data remains disabled to protect queued and uncertain writes.

Android can schedule a unique six-hour periodic WorkManager job. Its network
constraint is `CONNECTED`, or `UNMETERED` when the Wi-Fi-only option is enabled
(this also permits other unmetered networks). The worker reloads the selected
budget and secure device credential from local storage, checks that the budget
is authorized for that device, and uses the regular snapshot/change-feed and
outbox coordinator. Foreground and worker syncs can use separate coordinator
and Room instances, so all coordinators in the app process share one mutex;
this prevents a second sync from treating another coordinator's active `sending`
write as an interrupted process. Android Room builders enable multi-instance
invalidation, and the open Compose session observes recent transactions, total
transaction count, related budget records, outbox rows, and sync state as Room
flows. Worker changes therefore appear in the open UI without a route reload.
The Pixel's Android device regression verified updates across two independently
opened database instances. The worker skips encrypted budgets because budget
passwords are never persisted. WorkManager may defer periodic execution to meet
system power and network rules. On 2026-11-04, Pixel 10a ran
`BackgroundBudgetSyncWorker` against the original local Tandem/Actual API using
the saved device credential. The actual six-hour periodic WorkManager job
ran and returned `SUCCESS`; the selected CAD Room row remained `READY`, had no
sync error, and its `lastSyncedAt` advanced to 2026-11-04 21:31:25 UTC. The saved
credential remained active, confirming the scheduled run did not require
pairing again. The schedule preference was restored to off after verification.

iOS now has a native host target at `mobile/iosApp/TandemForActual.xcodeproj`.
Its app delegate registers a `BGAppRefreshTask`, schedules the next request with
a six-hour earliest start, and connects the background-sync preference to the
shared Kotlin sync coordinator. iOS decides when an eligible refresh actually
runs; the six-hour value is not a fixed execution interval. The Kotlin runner
loads the selected budget and preferences from Room, the device session from
Keychain, honors Wi-Fi-only mode, and skips encrypted budgets whose passwords
are not stored. The Xcode target also owns the iOS app icon and dark launch
screen assets. The shared appearance setting is forwarded to SwiftUI so iOS
system chrome follows the selected light, dark, or system mode.

This Linux workspace cannot run Xcode or the iOS app. Kotlin iOS arm64 and
simulator targets compile here, but the Swift host, Xcode framework link, asset
catalog, and scheduled runtime behavior still need a macOS build and device or
simulator check.

Automatic sync runs when the app resumes or network access returns while a
cached budget is visible. It requires the sync-on-open preference, a restored
device session, and an encryption password for encrypted budgets. Resume and
network events coalesce while a sync is active; a failed sync gets one queued
follow-up, while a successful sync clears the queued event. Successful attempts
have a 30-second cooldown per budget. Android uses Activity resume and
default-network callbacks while the app is started. iOS listens for the
app-active notification and Network framework path changes. Both iOS targets
compile, but iOS runtime behavior has not been device-tested from this Linux
workspace. The iOS host registers scheduled refresh, but its Swift integration
and runtime scheduling remain unverified until an Xcode build runs on macOS.

Appearance and home-layout preferences now affect the shared UI: the selected
background is applied to every screen, the budget overview supports roomy,
compact, and essentials-first layouts, and Reduce Motion switches route changes
to immediate transitions. Conflict, rejected-edit, and sync-reminder settings
control in-app banners on the live budget overview. They do not post operating
system notifications. Budget Preferences shows which planning values come from
Actual and marks upcoming due-date data as unavailable because that data is not
part of the current mobile sync. Biometric app lock and transaction deletion are
not exposed as switches or actions until those features are implemented.

The eight-character pairing code is shown as readable, centered text in the
entry field. It accepts the server's `XXXX-XXXX` format, with or without the
hyphen. The pairing code is never persisted. After pairing, the app stores the
server URL, device bearer credential, device identity, and authorized budget
IDs through the platform secure store. Android encrypts that record with
AES-GCM and keeps the key in Android Keystore; iOS stores it as a Keychain
generic-password item marked for this device only. The app restores the device
session on launch and clears the saved credential only when the server explicitly
reports that the device credential is invalid.
“Forget this device on this phone” removes the local credential; revoke the
device on the server as well to invalidate its bearer token. Encrypted-budget
passwords are not saved.
Temporary wireframes, test data, and device review artifacts belong under
ignored `.dev-tmp/` paths and must not be committed.

## Components and entry point

- `mobile/data` owns the Room SQLite database, local/remote record overlays,
  change-feed checkpoint, durable write outbox, and `BudgetSyncCoordinator`.
- `mobile/api-client` contains the generated OpenAPI client and the handwritten
  `GeneratedBudgetSyncRemote` adapter that implements the data module's remote
  protocol.
- The Compose root creates the API client, local repository, and sync
  coordinator. Pairing, budget catalog loading, and the first sync are wired to
  onboarding. On launch, completed Room checkpoints are enough to open a local
  budget; restoring the device credential and loading the server catalog are
  not prerequisites for showing cached data. The most recently selected cached
  budget is preferred, with the newest successful local snapshot as fallback.
  A per-session lock protects a full multi-request sync from a
  concurrent re-pair or credential removal. The generated API package also
  keeps its base URL and auth provider process-wide, so requests select the
  owning session under a shared request lock. The UI starts an initial sync
  when a budget is opened and exposes manual sync in Sync Center. Android
  background sync uses WorkManager as described above. Conflict, uncertain,
  and rejected write review screens expose their supported keep/rebase, retry,
  and discard actions. Resume and network-restoration signals trigger a
  throttled selected-budget sync on Android and iOS; iOS scheduled background
  work still requires a host target that is not present in this workspace.
- Device bearer credentials are restored from platform secure storage when the
  app launches. Android stores AES-GCM ciphertext in app-private preferences
  with its encryption key in Android Keystore; iOS stores the serialized
  session as a device-only Keychain generic-password item. A credential is
  cleared only when the API response explicitly identifies an invalid device
  credential. Other 401 responses and temporary secure-store read failures
  preserve the saved credential and can be retried.
  Forgetting the device clears its credential from this phone but does not
  revoke the server-side device token. Pairing codes and encrypted-budget
  passwords are never persisted; the budget password is cleared after a
  successful initial sync.
- The budget, account, and Activity screens read local Room records. Cached
  budget months supply the available-to-budget, category-group, and category
  values; account snapshots supply current balances; transaction records supply
  recent activity and account filtering. Transaction details show the cached
  record and expose the supported amount/notes editor. New transaction forms
  choose accounts, payees, and categories from the selected Actual budget,
  accept signed amounts and ISO dates, save optimistically to Room, and submit
  through the durable outbox. Conflict resolution and uncertain/rejected write
  review use the same coordinator.

The Android host handles system Back through the shared route stack and uses
safe drawing insets for the status and navigation bars. Pixel 10a review
confirmed that headers clear the status bar and that system-bar icon contrast
tracks the selected light or dark theme. Screenshots are disposable review
artifacts only when explicitly requested; they are not source assets and must
not be committed.

Pass an encrypted budget's password to `synchronize` when needed. It is sent
only on the relevant budget requests and is not stored in the database or
outbox. Device credentials are persisted only through the Android Keystore
backed store or iOS Keychain implementation.

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
snapshot. The Tandem's feed is shared server history, so independent device
databases each advance their own cursor over the same changes.

The change feed and snapshot behavior, including retention and generation
resets, is described in [the API contract guide](api/README.md#transaction-reads-and-reconciliation).

## Sending local edits

Local edits are stored optimistically with their base revision, base snapshot,
patch, operation UUID, and idempotency key. Unsent edits to one record can be
coalesced while they share the same base. The Tandem write endpoint supports
stable-ID creates and revision-based updates and deletions. A create carries its
complete input in `patch`, has no remote base, and uses a `tandem:<uuid>` record
ID. The server passes this identifier as Actual's `imported_id` through the
official `addTransactions` API. The projection continues exposing that same ID
even though Actual assigns a separate internal UUID. Room, the outbox, retries,
and change-feed reconciliation therefore use the caller-generated key without
writing to Actual's database directly.

Create and update amounts are limited to ±1,000,000,000 Actual amount units,
and dates before 1900-01-01 are rejected. A create also requires an existing
open account; any non-null payee or category must exist in the selected budget.
Those reference checks run under the budget lock before the Actual write. A
failed check returns HTTP 400 and leaves a terminal rejected journal entry.

After posting, the coordinator reads the authoritative transaction before
marking a create or update committed. A conflict keeps the local draft and stores the
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
coordinator, and generated-client adapter, then compile the connected app:

```sh
./gradlew :data:jvmTest :data:koverVerify :data:ktlintCheck \
  :api-client:jvmTest :api-client:koverVerify :api-client:ktlintCheck
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 :composeApp:ktlintCheck \
  :androidApp:assembleDebug
```

Android and iOS target compilation is covered by the mobile CI job; iOS
simulator runtime tests require macOS. The Android build can be installed on an
authorized device and started in sample preview mode as documented in the
development setup. After pairing, account/category/transaction values come
from the selected budget's local copy. A month without a cached budget record
shows an explicit empty state. Use the ignored two-currency Actual fixture to
exercise larger local snapshots; transaction editing and conflict handling
still need physical-device write-path verification.

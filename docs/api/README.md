# Tandem API

The Tandem REST API is versioned under `/api/v1`. It is served by the self-hosted Tandem service; Actual's own server does not expose a general-purpose REST API.

The generated OpenAPI 3.1 document is served from the running API at:

- `GET /api/v1/openapi.json` — generated OpenAPI document.

The canonical versioned contract is checked in at `docs/api/openapi-v1.json`.
After changing route schemas, regenerate it with `pnpm api:openapi:generate`;
`pnpm api:openapi:check` compares the checked-in file with the running route
definitions and is part of `pnpm check:all`.

## Implemented endpoints

| Method   | Path                                                                 | Purpose                                                            |
| -------- | -------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `GET`    | `/api/v1/health/live`                                                | Liveness check for the API process.                                |
| `GET`    | `/api/v1/health/ready`                                               | Readiness check for PostgreSQL and any configured startup session. |
| `GET`    | `/api/v1/openapi.json`                                               | Generated API contract.                                            |
| `GET`    | `/api/v1/auth/methods`                                               | Read the Actual server's available sign-in methods.                |
| `POST`   | `/api/v1/auth/login`                                                 | Sign in with an Actual password and create a device session.       |
| `POST`   | `/api/v1/auth/openid/start`                                          | Start Actual OpenID sign-in in the system browser.                 |
| `GET`    | `/api/v1/auth/actual-return/{state}/openid-cb`                       | Receive Actual's browser callback and return to the mobile app.    |
| `POST`   | `/api/v1/auth/openid/complete`                                       | Exchange a one-use return code for the device session.             |
| `GET`    | `/api/v1/auth/me`                                                    | Return the authenticated device and its budget scope.              |
| `GET`    | `/api/v1/devices`                                                    | List devices visible to the authenticated device.                  |
| `DELETE` | `/api/v1/devices/{deviceId}`                                         | Revoke an authorized device token.                                 |
| `GET`    | `/api/v1/budgets`                                                    | List Actual budgets visible to the signed-in account.              |
| `GET`    | `/api/v1/budgets/{budgetId}/snapshot`                                | Refresh and read a complete budget snapshot.                       |
| `GET`    | `/api/v1/budgets/{budgetId}/changes`                                 | Read generation-aware pages of budget changes.                     |
| `GET`    | `/api/v1/budgets/{budgetId}/transactions/{transactionId}`            | Read a transaction snapshot and revision.                          |
| `POST`   | `/api/v1/budgets/{budgetId}/transactions/{transactionId}/operations` | Create, update, or delete a transaction idempotently.              |
| `GET`    | `/api/v1/operations/{operationId}`                                   | Read operation status and conflict details.                        |

## Sign-in and device authorization

Set `ACTUAL_SERVER_URL` on the Tandem API to the Actual server address reachable
from the API container. Tandem reads the server's active methods from
`/account/needs-bootstrap` and supports password and OpenID sign-in. The Actual
password is sent to `/account/login` for that request only; Tandem does not save
it. Actual returns a session token, which Tandem validates with
`/account/validate` to identify the account and discover its budgets.

`POST /api/v1/auth/login` accepts `{"password":"…","deviceName":"…"}`.
It returns a random device bearer token and the budgets visible to that Actual
account. The token is shown only in this response; PostgreSQL stores only its
SHA-256 hash. Send it as `Authorization: Bearer <token>`. Device tokens expire
after 90 days without an authenticated request. Each successful request
refreshes that idle window. A device can list or revoke itself; it cannot
manage another device's credential.

Tandem stores the Actual session token server-side, encrypted in PostgreSQL with
`AUTH_TOKEN_ENCRYPTION_KEY`. Keep that key stable and private. Changing it makes
stored Actual sessions unreadable, so affected devices must sign in again. A
budget password for an Actual-encrypted budget is different: mobile sends it
only on the requests that need it, and Tandem does not persist it.

### OpenID sign-in

Set `ACTUAL_PUBLIC_URL` to the public HTTPS URL that reaches Tandem through the
reverse proxy. The browser return URL is built from that base. If it is unset,
Tandem does not advertise OpenID as available.

1. The app calls `POST /api/v1/auth/openid/start` with its device name. Tandem
   starts Actual's OpenID login and returns the provider redirect URL.
2. Actual redirects the browser to
   `/api/v1/auth/actual-return/{state}/openid-cb?token=…`. Tandem validates the
   Actual session, associates it with the account, and redirects the app using a
   short-lived, one-use Tandem code. The Actual token is never included in the
   app link.
3. The app exchanges that code through `POST /api/v1/auth/openid/complete` and
   stores the resulting Tandem device token in the platform's secure storage.

The state expires after five minutes and the app return code expires after 90
seconds. Both are one-use. The callback response disables caching and referrers.
The Nginx example disables access logging on the callback because Actual's
callback URL includes its session token as a query parameter.

Sign-in endpoints are rate-limited to 10 requests per client address per minute
in each API process. Forwarded client addresses are ignored by default. Set
`TRUST_PROXY` to the exact reverse-proxy IPs or narrow CIDRs, such as
`172.30.0.2` or `172.30.0.0/24`; do not use `0.0.0.0/0`. Keep the API reachable
only through that trusted proxy. The Nginx example applies a second request
limit to sign-in and a broader limit to other API traffic.

`GET /api/v1/budgets` requires a device token and returns budgets visible to the
signed-in Actual account. Each item includes its Actual sync ID, display name,
and whether the budget is encrypted. The response does not expose an encryption
key or password and uses `Cache-Control: no-store`.

## Transaction reads and reconciliation

Set `ACTUAL_SERVER_URL` on the Tandem API container to enable Actual sign-in and
budget access. A deployment may also configure `ACTUAL_SESSION_TOKEN` as an
operator-managed startup session; it is not required for each person to sign in.
Session tokens can expire or be revoked. When a configured startup token is
rejected, `GET /api/v1/health/ready` returns `503` with a message to replace the
token and restart Tandem. Tandem does not refresh it automatically.

The Actual server must be the exact version supported by the API build; the API
fails startup if it cannot verify that version. `ACTUAL_API_DATA_DIR` defaults
to `/data/actual-api` and must be mounted on persistent storage. This workspace
belongs to the official Node.js API and contains decrypted Actual budget files;
protect it and its backups as financial data. PostgreSQL stores a readable
projection of budget records, including records from encrypted budgets. Device
Room caches also contain readable data. Neither the Actual password nor session
token is sent to mobile devices.

Startup logs include application-controlled configuration and migration-history
messages. PostgreSQL driver errors and other unexpected failures log only their
error type, so connection strings and credentials are not copied into logs.

The budget snapshot and change endpoints use the same device scope and may also
receive `X-Budget-Encryption-Password` for an encrypted budget. Actual's public
API has no incremental change feed, so each complete snapshot and each new
changes poll asks the pinned Actual API to download the budget, reads supported
Actual API entities, and diffs them into PostgreSQL. A request that continues a
page with `throughCursor` reads the already-materialized change rows and does
not start another Actual download.

`GET /api/v1/budgets/{budgetId}/snapshot` returns a complete set of records with
their type, stable Actual ID (or month string), JSON body, and SHA-256 revision,
along with a generation and decimal-string cursor. It includes accounts and
current balances, account groups, hidden and visible categories and category
groups, payees, budget-month totals and category allocations, and transactions
including split children and transfer links. Transaction bodies use the same
normalized fields as the transaction read endpoint, and their revisions match
that endpoint so an offline client can safely use a downloaded record as its
write base. Internal raw bank-import payloads are omitted from transaction
records. The response is `Cache-Control: no-store`.

Start a change poll with
`GET /api/v1/budgets/{budgetId}/changes?generation=<generation>&cursor=<cursor>`.
The default page size is 100; `limit` accepts 1 through 500. The response gives
`throughCursor`, a high-water mark fixed for that pagination run, `nextCursor`,
and `hasMore`. While `hasMore` is true, request the next page with the same
generation and `throughCursor`, and set `cursor` to `nextCursor`. Once the page
is complete, omit `throughCursor` on the next poll so the API refreshes Actual
again. Upserts carry the full current record body; deletions carry a tombstone
with a null body. The latest upsert body is stored with the current projection
and joined into its feed event; when a later update replaces it, the previous
body moves to that historical event until it leaves retention.

If `resetRequired` is true, or the returned generation differs from the local
generation, discard the local remote-data baseline and fetch a complete
snapshot. This also applies when the requested cursor is ahead of the server or
its change history has expired. The server retains the newest 100,000 change
events per budget; older mobile cursors get a reset instead of a partial feed.
Local pending mobile operations must remain in the outbox while a remote
snapshot is replaced.

Set `CHANGE_FEED_EPOCH` to a stable installation value. When restoring an older
PostgreSQL backup, rotate this value before starting the restored service; the
next refresh creates new generations and forces clients to fetch snapshots.
Keep the epoch outside the PostgreSQL backup. A restored database cannot infer
that it has been rewound if both its records and change history are restored to
an earlier point and the configured epoch is left unchanged.

These endpoints return the state in the Actual copy downloaded for that
request. Actual offers no public revision-conditional reads or incremental
feed, so a change made through another Actual client after the download is
visible on the next refresh. Refresh failures return a structured `502` and do
not advance the Tandem cursor. PostgreSQL stores a plaintext projection of
budget data; protect its volume and backups as financial data.

Use `GET /api/v1/budgets/{budgetId}/transactions/{transactionId}` to obtain the
canonical transaction snapshot and its SHA-256 `revision`. Include
`X-Budget-Encryption-Password` only for an encrypted budget. This budget-level
password is distinct from the Actual account sign-in password; the service
forwards it to the official API for that request and does not persist it. Use
HTTPS. Responses
containing financial data set `Cache-Control: no-store`.

Submit a write to
`POST /api/v1/budgets/{budgetId}/transactions/{transactionId}/operations` with
an `Idempotency-Key` header and a JSON body containing:

- A client-generated UUID `operationId`.
- For `action: "create"`, use `transactionId: "tandem:<uuid>"` in the path,
  omit `baseRevision` and `baseSnapshot`, and supply the complete account,
  amount, date, payee, category, notes, and cleared values in `patch`. The
  server uses this caller-owned ID as Actual's `imported_id`, then exposes that
  same ID in snapshots, change pages, reads, updates, and deletes. Actual still
  assigns its internal database ID.
- For `action: "update"` or `"delete"`, include the `baseRevision` and complete
  `baseSnapshot` returned by the transaction read endpoint. Supply a field
  patch for updates or an empty patch for deletion.

Create and update amounts must be integer Actual amount units between
`-1,000,000,000` and `1,000,000,000`. Transaction dates must be valid ISO dates
on or after `1900-01-01`. Create requests must reference an existing open
account; non-null payee and category IDs must exist in that budget. Invalid
amounts, dates, or references return HTTP 400. Reference failures are stored
as terminal `rejected` operations, so retrying the same operation returns the
same 400 response. These checks run before Actual is asked to write a row.

Keep the same operation ID, idempotency key, base snapshot, and patch when
retrying an uncertain HTTP request. The durable journal prevents repeated
requests from applying twice. Reusing a key or operation ID with a different
payload returns HTTP 409. Device budget scope is checked before the Actual API
is called.

After 90 days, the service compacts the JSON payloads for committed and
rejected operations. It keeps the operation ID, idempotency key, request hash,
and final status, so an old retry remains idempotent. The original snapshots,
patch, and result are no longer available after compaction. Queued, applying,
uncertain, and conflict operations are retained with their payloads.

The operation response distinguishes `queued`, `applying`, `committed`,
`conflict`, `uncertain`, and `rejected`. A conflict is a durable operation that
includes the base snapshot, local patch, latest remote snapshot, and overlapping
fields. It does not overwrite the Actual change. To resolve it, read the
transaction again and submit a new operation against that fresh snapshot with
the user's chosen patch. Poll `GET /api/v1/operations/{operationId}` after
timeouts or reconnects; it returns only records owned by the authenticated
principal and within the device's budget scope. Missing, foreign, and out-of-scope
operations return HTTP 404.

The public Actual transaction update/delete API has no revision precondition.
The Tandem detects changes visible before a write and records them as
conflicts, but cannot prevent an Actual edit racing between its last read and
write. Stable create IDs are reserved to the `tandem:<uuid>` namespace;
collisions are reported as conflicts. Split parents and children can be edited
or deleted through Actual's public transaction methods. Changes to the split
group structure are checked before writing; parent deletion follows Actual's
group cascade, and child deletion follows Actual's split recalculation rules.

Actual API calls share one serialized process-wide client. Each Actual session
call has a five-minute deadline. If one expires, in-flight writes remain
uncertain and the client fails closed; restart the API process before using
Actual again. This prevents one stalled call from holding the serialized queue
indefinitely or allowing overlapping calls into Actual's singleton client.

These endpoints are described by the generated OpenAPI document. Validate each
response against the contract served by the running version.

The shared mobile client applies snapshots and paged changes to a local SQLite
database and polls durable operation status after uncertain writes. Its lifecycle
and recovery behavior are documented in
[`docs/mobile-sync.md`](../mobile-sync.md). The current transaction operation
endpoint supports stable-ID transaction creation, updates, and deletions.

# Tandem API

The Tandem REST API is versioned under `/api/v1`. It is served by the self-hosted Tandem service; Actual's own server does not expose a general-purpose REST API.

The generated OpenAPI 3.1 document is served from the running API at:

- `GET /api/v1/openapi.json` — generated OpenAPI document.

The canonical versioned contract is checked in at `docs/api/openapi-v1.json`.
After changing route schemas, regenerate it with `pnpm api:openapi:generate`;
`pnpm api:openapi:check` compares the checked-in file with the running route
definitions and is part of `pnpm check:all`.

## Implemented endpoints

| Method   | Path                                                                 | Purpose                                                        |
| -------- | -------------------------------------------------------------------- | -------------------------------------------------------------- |
| `GET`    | `/api/v1/health/live`                                                | Liveness check for the API process.                            |
| `GET`    | `/api/v1/health/ready`                                               | Readiness check; returns `503` when PostgreSQL is unavailable. |
| `GET`    | `/api/v1/openapi.json`                                               | Generated API contract.                                        |
| `POST`   | `/api/v1/auth/pair`                                                  | Exchange a one-time pairing code for a device token.           |
| `GET`    | `/api/v1/auth/me`                                                    | Return the authenticated device and its budget scope.          |
| `GET`    | `/api/v1/devices`                                                    | List devices paired to the same principal.                     |
| `DELETE` | `/api/v1/devices/{deviceId}`                                         | Revoke a paired device token.                                  |
| `GET`    | `/api/v1/budgets`                                                    | List Actual budgets visible to the authenticated device.       |
| `GET`    | `/api/v1/budgets/{budgetId}/snapshot`                                | Refresh and read a complete budget snapshot.                   |
| `GET`    | `/api/v1/budgets/{budgetId}/changes`                                 | Read generation-aware pages of budget changes.                 |
| `GET`    | `/api/v1/budgets/{budgetId}/transactions/{transactionId}`            | Read a transaction snapshot and revision.                      |
| `POST`   | `/api/v1/budgets/{budgetId}/transactions/{transactionId}/operations` | Create, update, or delete a transaction idempotently.          |
| `GET`    | `/api/v1/operations/{operationId}`                                   | Read operation status and conflict details.                    |

## Device pairing and authentication

Create a one-time pairing code from an API container with database access:

```sh
docker compose -f docker-compose.dev.yml exec api \
  pnpm --filter @tandem-for-actual/api pair \
  --budget-id '<Actual budget sync ID>'
```

Use `--budget-id` more than once to scope the new device to selected budgets, or
use `--all-budgets` to grant all budgets to that principal; a device with this
scope can also manage other devices for that principal. The principal ID
defaults to `owner`; `--principal-id <id>` selects another principal. The CLI
prints a JSON object containing a five-minute, single-use code in the
`XXXX-XXXX` format, such as `4DF7-J1M9`. The code uses eight random characters
from a 33-symbol uppercase-letter-and-digit alphabet (about 40.36 bits of
entropy); generators omit the look-alike
letters `I`, `L`, and `O`, while pairing accepts lowercase input, surrounding
whitespace, an omitted hyphen, and `I`/`L`/`O` as aliases for `1`/`1`/`0`.
Treat the CLI output as a secret and deliver it to the mobile device over a
trusted channel.

`POST /api/v1/auth/pair` accepts `{"code":"…","deviceName":"…"}` and returns
the opaque bearer token once. The short code is only a bootstrap credential;
the resulting device token has 256 bits of random
entropy; only its SHA-256 hash is stored in PostgreSQL. Pairing code hashes use
HMAC with SHA-256 and the independent `PAIRING_CODE_HASH_KEY`. Set it to at
least 32 random bytes and provide the same value to the API and pairing CLI.
Rotating the key invalidates pending pairing codes but does not affect paired
devices. Device tokens expire after 90 days without an
authenticated request; each successful request refreshes that idle window.
Invalid pairing codes are limited to 10 per client address per minute.
Successful pairings and other request-validation errors do not consume this
failure limit. The in-memory limit applies separately to each API process.
Forwarded client addresses are ignored by default. For a
reverse-proxy deployment, set `TRUST_PROXY` to the exact proxy IP addresses or
narrow CIDRs, for example `172.30.0.2` or `172.30.0.0/24`. Do not use
`0.0.0.0/0`, and make the API reachable only through the trusted proxy network.
Fastify then keys the pairing limit on the forwarded client address. The Nginx
example also applies a per-address limit to the pairing route. If `TRUST_PROXY`
is unset, the API warns when it receives `X-Forwarded-For`; requests arriving
from the same proxy socket share the API's in-memory limit.
The Nginx example also limits API traffic to 600 requests per minute per client
address, with a short burst allowance. Keep the API reachable only through this
proxy so invalid-token traffic cannot bypass that limit.

Invalid pairing input returns HTTP 400 with a structured `invalid_request`
error. An unavailable, expired, or already-used pairing code returns HTTP 401.

Send the token as `Authorization: Bearer <token>`. `GET /api/v1/auth/me` returns
the current device and its budget IDs. A budget-scoped device can list and
revoke itself only. A device paired with `--all-budgets` has full device
management within its principal and can list or revoke the principal's other
devices. Attempts to manage an invisible device return 404. Revoked tokens stop
authenticating immediately. The pairing response uses `Cache-Control: no-store`
because it contains the new credential.

`GET /api/v1/budgets` requires a device token and returns cloud-backed budgets
within that device's authorized scope. Each item includes its Actual sync ID,
display name, and whether the budget is encrypted. The response does not expose
an encryption key or password and uses `Cache-Control: no-store`.

## Transaction reads and reconciliation

Set `ACTUAL_SERVER_URL` and `ACTUAL_SERVER_PASSWORD` on the Tandem API
container to enable Actual integration. The server must be the exact version
supported by the API build; the API fails startup if it cannot verify that
version. `ACTUAL_API_DATA_DIR` defaults to `/data/actual-api` and must be mounted
on persistent storage. This workspace belongs to the official Node.js API and
can contain decrypted budget data; protect it and its backups as financial data.
The server password authenticates the Tandem service to Actual. It is never
sent to mobile devices.

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
with a null body.

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
password is distinct from `ACTUAL_SERVER_PASSWORD`; the service forwards it to
the official API for that request and does not persist it. Use HTTPS. Responses
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
collisions are reported as conflicts. Transfers and split transactions are
readable but remain non-editable through reconciliation until their mutation
semantics are verified.

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

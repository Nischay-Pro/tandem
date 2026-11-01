# Companion API

The Companion REST API is versioned under `/api/v1`. It is served by the self-hosted Companion service; Actual's own server does not expose a general-purpose REST API.

The generated OpenAPI 3.1 document is served from the running API at:

- `GET /api/v1/openapi.json` — generated OpenAPI document.

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
| `GET`    | `/api/v1/budgets/{budgetId}/transactions/{transactionId}`            | Read a transaction snapshot and revision.                      |
| `POST`   | `/api/v1/budgets/{budgetId}/transactions/{transactionId}/operations` | Submit an idempotent update or deletion.                       |
| `GET`    | `/api/v1/operations/{operationId}`                                   | Read operation status and conflict details.                    |

## Device pairing and authentication

Create a one-time code from an API container with database access:

```sh
docker compose -f docker-compose.dev.yml exec api \
  pnpm --filter @actual-budget-companion/api pair \
  --budget-id '<Actual budget sync ID>'
```

Use `--budget-id` more than once to scope the new device to selected budgets, or
use `--all-budgets` to grant all budgets to that principal; a device with this
scope can also manage other devices for that principal. The principal ID
defaults to `owner`; `--principal-id <id>` selects another principal. The CLI
prints a JSON object containing a 5-minute, single-use code. Treat that output
as a secret and deliver it to the mobile device over a trusted channel.

`POST /api/v1/auth/pair` accepts `{"code":"…","deviceName":"…"}` and returns
the opaque bearer token once. The token has 256 bits of random entropy; only its
SHA-256 hash is stored in PostgreSQL. Pairing attempts are limited to 10 per
client address per minute per API process. Forwarded client addresses are
ignored by default. For a reverse-proxy deployment, set `TRUST_PROXY` to the
exact proxy IP addresses or narrow CIDRs, for example `172.30.0.2` or
`172.30.0.0/24`. Do not use `0.0.0.0/0`, and make the API reachable only
through the trusted proxy network. Fastify then keys the pairing limit on the
forwarded client address. If `TRUST_PROXY` is unset, requests arriving from the
same proxy socket share a rate-limit bucket.

Invalid pairing input returns HTTP 400 with a structured `invalid_request`
error. An unavailable, expired, or already-used pairing code returns HTTP 401.

Send the token as `Authorization: Bearer <token>`. `GET /api/v1/auth/me` returns
the current device and its budget IDs. A budget-scoped device can list and
revoke itself only. A device paired with `--all-budgets` has full device
management within its principal and can list or revoke the principal's other
devices. Attempts to manage an invisible device return 404. Revoked tokens stop
authenticating immediately. The pairing response uses `Cache-Control: no-store`
because it contains the new credential.

## Transaction reads and reconciliation

Set `ACTUAL_SERVER_URL` and `ACTUAL_SERVER_PASSWORD` on the Companion API
container to enable Actual integration. The server must be the exact version
supported by the API build; the API fails startup if it cannot verify that
version. `ACTUAL_API_DATA_DIR` defaults to `/data/actual-api` and must be mounted
on persistent storage. This workspace belongs to the official Node.js API and
can contain decrypted budget data; protect it and its backups as financial data.
The server password authenticates the Companion service to Actual. It is never
sent to mobile devices.

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
- `action`: `update` or `delete`.
- The `baseRevision` and complete `baseSnapshot` returned by the transaction
  read endpoint.
- A field patch for updates, or an empty patch for deletion.

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
The Companion detects changes visible before a write and records them as
conflicts, but cannot prevent an Actual edit racing between its last read and
write. Transfers and split transactions are readable but remain non-editable
through reconciliation until their mutation semantics are verified.

These endpoints are described by the generated OpenAPI document. Validate each
response against the contract served by the running version.

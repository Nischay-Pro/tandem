# Companion API

The Companion REST API is versioned under `/api/v1`. It is served by the self-hosted Companion service; Actual's own server does not expose a general-purpose REST API.

The generated OpenAPI 3.1 document is served from the running API at:

- `GET /api/v1/openapi.json` — generated OpenAPI document.

## Implemented endpoints

| Method   | Path                         | Purpose                                                        |
| -------- | ---------------------------- | -------------------------------------------------------------- |
| `GET`    | `/api/v1/health/live`        | Liveness check for the API process.                            |
| `GET`    | `/api/v1/health/ready`       | Readiness check; returns `503` when PostgreSQL is unavailable. |
| `GET`    | `/api/v1/openapi.json`       | Generated API contract.                                        |
| `POST`   | `/api/v1/auth/pair`          | Exchange a one-time pairing code for a device token.           |
| `GET`    | `/api/v1/auth/me`            | Return the authenticated device and its budget scope.          |
| `GET`    | `/api/v1/devices`            | List devices paired to the same principal.                     |
| `DELETE` | `/api/v1/devices/{deviceId}` | Revoke a paired device token.                                  |

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

These endpoints are described by the generated OpenAPI document. Validate each
response against the contract served by the running version; planned endpoints
are not available until they appear there.

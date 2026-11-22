# Extension sync API

Tandem's app talks to Actual directly. This optional service syncs sealed
planning records between devices. The machine-readable contract is
[`docs/api/openapi-v1.json`](../api/openapi-v1.json); regenerate it with
`pnpm api:openapi:generate`.

Every budget route needs an `x-actual-token` header holding the caller's Actual
session token. The service asks Actual whether that session can open the budget.

| Method and path                                              | Purpose                                                                                 |
| ------------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| `GET /extension/v1/health/live`                              | The process is running.                                                                 |
| `GET /extension/v1/health/ready`                             | Storage is reachable.                                                                   |
| `GET /extension/v1/capabilities`                             | API version and features.                                                               |
| `GET /extension/v1/budgets/{budgetId}/key`                   | The budget's wrapped planning key, or 404.                                              |
| `PUT /extension/v1/budgets/{budgetId}/key`                   | Store a wrapped key; `expectedKeyId` must match the current one (`null` for the first). |
| `GET /extension/v1/budgets/{budgetId}/changes?after=&limit=` | Records changed after a sequence number, oldest first.                                  |
| `PUT /extension/v1/budgets/{budgetId}/records/{recordId}`    | Store a sealed record as the next revision.                                             |

## Records

A record body carries `recordType`, `schemaVersion`, `revision`, `baseRevision`,
`deleted`, and the base64 `iv` and `ciphertext`. The server accepts it only when
`baseRevision` is the revision it holds (or `null` for a new record) and
`revision` is one more. Otherwise it answers 409 `revision_conflict` with the
current record so the device can merge.

## Errors

Errors use `{ "error": { "code", "message" } }`. Codes: `sign_in_required` (401),
`budget_forbidden` (403), `key_not_found` (404), `key_conflict` and
`revision_conflict` (409), `actual_unavailable` and `storage_unavailable` (503).

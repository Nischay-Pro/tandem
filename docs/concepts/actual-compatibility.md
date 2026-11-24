# Actual compatibility

Tandem syncs with Actual using Actual's own sync protocol, the one its web and
desktop apps use. It is checked against **Actual server 26.11.0**, and `@actual-app/api` 26.11.0 records
the test fixtures.

## What the app implements

- **Sync.** Hybrid logical clock timestamps, the Merkle trie Actual uses to find
  differences, and the protobuf messages sent to `/sync/sync`. Edits made in
  Tandem, in Actual and on other devices converge on the same values.
- **Budgets.** Envelope and tracking budgets, carryover, holds, transfers and
  splits are calculated on the device with a port of Actual's spreadsheet rules.
- **Rules and schedules.** Actual's rule engine runs on new transactions, and
  schedules advance and post the way Actual's apps do.
- **Bank sync.** Linked accounts download through the Actual server's
  SimpleFIN, GoCardless, Enable Banking, Pluggy.ai and Akahu integrations and are
  reconciled the way Actual's bank sync does.
- **Statement import.** CSV, QIF, OFX and QFX files are previewed on the device
  and matched against existing transactions, as Actual's import does.
- **Sign-in.** Password and OpenID sign-in, using the Actual server's own
  login flow.
- **Encryption.** End-to-end encrypted budgets use the same key derivation
  (PBKDF2-HMAC-SHA512) and AES-256-GCM as Actual. Tandem checks the budget
  password against Actual's test message and never sends it anywhere.

## How it's checked

- Unit tests compare the on-device results with outputs recorded from Actual's
  own client for the same budgets: snapshots, budget months, rule results and
  schedule dates.
- The [Actual compatibility tests](../develop/actual-compatibility-tests.md)
  run every supported edit through a real Actual server, then read the budget
  back with Actual's client, including an encrypted budget.

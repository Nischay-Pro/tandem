# Architecture and trust

Tandem is a phone and tablet app for Actual Budget. It talks to your Actual
server directly, the same way Actual's own apps do. Nothing else has to run for
budgeting to work.

## Budget data

- The app downloads each budget file from Actual and keeps it on the device in a
  private, backup-excluded database.
- Every change is recorded as an Actual sync message and sent to `/sync/sync`.
  Changes made in Actual or on another device arrive the same way, so all copies
  converge on the same result.
- Budget months, rules, schedules and transfers are calculated on the device with
  a port of Actual's own logic, checked against Actual's results in tests.
- End-to-end encrypted budgets stay encrypted in transit and on Actual's server.
  The app derives the key from the budget password exactly as Actual does and
  never sends the password or the key anywhere. By default the password isn't
  stored on the device; the app asks for it when it needs it. If you allow
  background sync for an encrypted budget, its password is kept in the device's
  protected storage so scheduled syncs can open it.
- Edits made offline are saved on the device first and sent when Actual is
  reachable again. If Actual resets a budget's sync history, the device keeps a
  backup of its copy before downloading the budget again.

## Planning records

Some features hold details Actual has no place for, such as a debt's APR or a
savings goal. These planning records:

- live on the device, each sealed with AES-256-GCM under a random key for that
  budget, kept in the Android Keystore or iOS Keychain;
- bind the budget, record, schema version and revision into each seal, so a
  record can't be moved or replayed under another identity;
- never change Actual's ledger.

## Optional extension sync

To share planning records across devices, you can run Tandem's extension sync
service beside Actual. It only stores records the device has already sealed. It
can't read them, and it lets a request through only after Actual confirms the
caller's Actual session can open that budget.

If the service is down or not installed, the app keeps working: planning records
wait on the device and sync when it returns. Budget data never passes through it.

To add a device, enter the planning recovery key from a device that already
syncs. If you lose a device, replace the key from Settings: Tandem makes a new
planning key and recovery key, re-encrypts every record with them and uploads
them again. The lost device can't read anything synced afterwards, and each
device you still use needs the new recovery key. Records the lost device already
had stay readable on that device. Encrypted budgets wrap the planning key with
the budget's own key instead, so for them, change the budget's password in
Actual.

## What each part can see

| Part                   | Sees                                                              |
| ---------------------- | ----------------------------------------------------------------- |
| Your Actual server     | Budget data (encrypted, if the budget uses end-to-end encryption) |
| The phone or tablet    | Everything for budgets it has opened                              |
| Extension sync service | Budget ids, record ids and types, sizes, timing; never contents   |

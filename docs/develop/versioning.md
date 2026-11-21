# App and server versioning

The app syncs budgets with Actual directly and is tested against one Actual
release at a time (see [Actual compatibility](../concepts/actual-compatibility.md)).
When Actual changes its sync protocol or schema, update the pinned release, the
recorded fixtures and the compatibility script together.

## Extension sync

The optional extension sync service and the app also ship separately.
`GET /extension/v1/capabilities` needs no sign-in and returns:

| Field        | Meaning                                                               |
| ------------ | --------------------------------------------------------------------- |
| `apiVersion` | The contract version. It changes only when a request changes meaning. |
| `features`   | Named behaviours the service supports, such as `planning-records`.    |

Rules for changes:

- **Add, don't change.** Add a field, value or route; never change what an
  existing one means.
- **Name new behaviour** in the `features` list and have the app check for it.
- **Wait, don't fail.** Planning records that need a missing feature wait on the
  device and sync once the service reports it. Budget sync never waits on it.

Release versions (0.1.0, 0.2.0, …) and `apiVersion` are independent. Keep
`apiVersion` at `1` until the first public release.

# App and server versioning

The Tandem app and the Tandem server ship separately. A newer app can talk to an older server, and an older app can talk to a newer server, as long as both are at or after the API baseline.

## What the server reports

`GET /api/v1/capabilities` needs no sign-in and returns:

| Field                     | Meaning                                                                  |
| ------------------------- | ------------------------------------------------------------------------ |
| `apiVersion`              | The contract version. It changes only when a request changes meaning.    |
| `minimumClientApiVersion` | The oldest app API version this server still serves.                     |
| `features`                | Named behaviours this server supports, such as `hold-total`.             |
| `actualVersion`           | The Actual Budget release this server's adapter is built and tested for. |

A server without this endpoint predates the baseline. The app treats it as having no named features and shows **Update your Tandem server**.

## Rules for changes

- **Add, don't change.** Add a new field, value, or route. Never change what an existing field or request means.
- **Name new behaviour.** When the app needs server behaviour that older servers lack, add a name to `SERVER_FEATURES` in `services/api/src/capabilities.ts`. Gate the app on it with `requiredServerFeature` in `TandemMobileSession.kt`.
- **Wait, don't fail.** A queued change that needs a missing feature waits on the device as "Waiting for your Tandem server to be updated". It syncs automatically once the server reports the feature.
- **Error codes stay specific.** `invalid_request` means the data is wrong, not that the server is too old.

## Version numbers

Release versions (0.1.0, 0.2.0, …) and the API version are independent.

| Number                                                       | Change it when                                                                                   |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| Release version                                              | Every release.                                                                                   |
| `TANDEM_API_VERSION` (server) and `CLIENT_API_VERSION` (app) | A request changes meaning or is removed. Rare.                                                   |
| `MINIMUM_CLIENT_API_VERSION` (server)                        | The server intentionally stops serving older apps. The app then shows **Update the Tandem app**. |
| `SERVER_FEATURES` (server)                                   | A new feature ships. Most releases only add names here.                                          |

Keep both API versions at `1` and the minimum at `1` until the first public release.

# Tandem documentation

Choose a page by what you need to do.

## Run Tandem

| Page                                               | For                                                          |
| -------------------------------------------------- | ------------------------------------------------------------ |
| [Install extension sync](deploy/install.md)        | Operators adding optional planning sync beside Actual.       |
| [Configuration reference](deploy/configuration.md) | Operators changing settings in `.env`.                       |
| [Reverse proxy](deploy/reverse-proxy.md)           | Operators routing the extension sync path.                   |
| [Upgrade](deploy/upgrade.md)                       | Operators updating or rolling back extension sync.           |
| [Back up and restore](deploy/backup-restore.md)    | Operators protecting and recovering planning records.        |
| [Troubleshooting](deploy/troubleshooting.md)       | Operators diagnosing connection and readiness failures.      |
| [Trust and security](deploy/trust.md)              | Operators deciding what data and access the server can hold. |

## Use the app

| Page                                                              | For                                                           |
| ----------------------------------------------------------------- | ------------------------------------------------------------- |
| [Install the app](using/install-app.md)                           | Android users installing the nightly prerelease.              |
| [Offline sync and conflicts](using/offline-sync-and-conflicts.md) | People using Tandem offline or reviewing pending edits.       |
| [Sync status names](using/sync-status.md)                         | People interpreting Pending Changes and transaction statuses. |

## Understand Tandem

| Page                                                         | For                                                           |
| ------------------------------------------------------------ | ------------------------------------------------------------- |
| [Architecture and trust](concepts/architecture-and-trust.md) | Operators and developers reviewing data flow and privacy.     |
| [Actual compatibility](concepts/actual-compatibility.md)     | Operators checking the supported Actual release and behavior. |
| [Glossary](concepts/glossary.md)                             | Anyone checking the meaning of Tandem terms.                  |

## Build and contribute

| Page                                                                | For                                                                  |
| ------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [Build from source](develop/build-from-source.md)                   | Developers building the API, Android app, or iOS app.                |
| [Test Tandem](develop/testing.md)                                   | Contributors running API and mobile checks before review.            |
| [Actual compatibility tests](develop/actual-compatibility-tests.md) | Contributors checking direct sync against the pinned Actual release. |
| [Toolchain](develop/toolchain.md)                                   | Developers setting up Gradle, Kotlin, and the generated client.      |
| [UI wording](develop/ui-writing.md)                                 | Developers changing mobile interface text.                           |
| [Extension sync API](develop/api-reference.md)                      | Developers integrating with the optional extension sync service.     |
| [App and server versioning](develop/versioning.md)                  | Contributors changing the API or features the app depends on.        |
| [OpenAPI document](api/openapi-v1.json)                             | Tools generating or checking API clients. Keep this path stable.     |

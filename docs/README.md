# Tandem documentation

Choose a page by what you need to do.

## Run Tandem

| Page                                               | For                                                          |
| -------------------------------------------------- | ------------------------------------------------------------ |
| [Install the server](deploy/install.md)            | Operators setting up Tandem beside Actual.                   |
| [Configuration reference](deploy/configuration.md) | Operators changing API and Compose settings.                 |
| [Reverse proxy](deploy/reverse-proxy.md)           | Operators routing HTTPS traffic to Actual and Tandem.        |
| [Upgrade](deploy/upgrade.md)                       | Operators rebuilding or rolling back the API.                |
| [Back up and restore](deploy/backup-restore.md)    | Operators protecting and recovering budget data.             |
| [Troubleshooting](deploy/troubleshooting.md)       | Operators diagnosing connection and readiness failures.      |
| [Trust and security](deploy/trust.md)              | Operators deciding what data and access the server can hold. |

## Use the app

| Page                                                              | For                                                           |
| ----------------------------------------------------------------- | ------------------------------------------------------------- |
| [Offline sync and conflicts](using/offline-sync-and-conflicts.md) | People using Tandem offline or reviewing pending edits.       |
| [Sync status names](using/sync-status.md)                         | People interpreting Pending Changes and transaction statuses. |

## Understand Tandem

| Page                                                         | For                                                           |
| ------------------------------------------------------------ | ------------------------------------------------------------- |
| [Architecture and trust](concepts/architecture-and-trust.md) | Operators and developers reviewing data flow and privacy.     |
| [Actual compatibility](concepts/actual-compatibility.md)     | Operators checking the supported Actual release and behavior. |

## Build and contribute

| Page                                        | For                                                              |
| ------------------------------------------- | ---------------------------------------------------------------- |
| [Build and test](develop/build-and-test.md) | Developers building Android, iOS, and the API.                   |
| [Toolchain](develop/toolchain.md)           | Developers setting up Gradle, Kotlin, and the generated client.  |
| [UI wording](develop/ui-writing.md)         | Developers changing mobile interface text.                       |
| [API reference](develop/api-reference.md)   | Developers integrating with the Tandem API.                      |
| [OpenAPI document](api/openapi-v1.json)     | Tools generating or checking API clients. Keep this path stable. |

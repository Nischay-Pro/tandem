# Tandem for Actual Budget

Your budget, everywhere. Access your Actual Budget finances on your phone, even
when you are offline.

Tandem is a mobile app and a small server that connects it to your own Actual
Budget service. Actual remains the source of truth. Tandem keeps a local copy on
the phone and syncs saved changes with Actual when the connection returns.

## Get started

1. Run an Actual Budget server.
2. Set up Tandem's API gateway by following the [server installation guide](docs/deploy/install.md).
3. [Install the Android app](docs/using/install-app.md), enter the Actual server address, and sign in with your Actual account.

The [server installation guide](docs/deploy/install.md) uses one setup command
to install the latest published nightly Compose bundle with its API image pinned
by digest. It stops safely if that release bundle is not available yet. See the
[deployment configuration](docs/deploy/configuration.md) and
[troubleshooting guide](docs/deploy/troubleshooting.md) for server settings and
connection problems. The [operator guides](docs/README.md#run-tandem) also cover
proxy setup, upgrades, backups, and restores.

## How it works

The phone stores its budget copy locally. The Tandem API uses Actual's supported
Node client to read and write budgets. This gives Tandem a server-side trust
boundary; review [architecture and data privacy](docs/concepts/architecture-and-trust.md)
before deploying it. For Actual's encrypted budgets, see the
[compatibility notes](docs/concepts/actual-compatibility.md).

## Build from source

See [build instructions](docs/develop/build-from-source.md),
[testing instructions](docs/develop/testing.md), the
[toolchain reference](docs/develop/toolchain.md), and the
[API reference](docs/develop/api-reference.md). The machine-readable API
contract remains at [`docs/api/openapi-v1.json`](docs/api/openapi-v1.json).

## Documentation

The [documentation index](docs/README.md) lists every guide by audience.

## License

Tandem is licensed under AGPL-3.0-or-later. See [`LICENSE`](LICENSE).

# Development

## Local scratch data

`.dev-tmp/` is ignored by Git and is reserved for disposable development artifacts, database files, test fixtures, and exported images. Use synthetic data only. Docker images remain in the Docker daemon unless explicitly exported into `.dev-tmp/artifacts/`.

## API checks

The API checks run inside Docker because the development environment may not have Node.js installed. The toolchain is pinned to Node.js 26.13.0 LTS, pnpm 12.16.1, and PostgreSQL 18.6 on Alpine 3.24. These are the latest stable releases verified for this stack; PostgreSQL 19 is still a release candidate, so it is not used for this financial-data service.

```sh
docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
```

The integration-test profile runs formatting, lint, typecheck, and the API test suite against PostgreSQL. Coverage enforces 100% lines, statements, functions, and branches for API source. The development database is stored under `.dev-tmp/` and must contain synthetic data only.

GitHub Actions runs the same Compose quality gate for pushes and pull requests with read-only repository permissions.

`services/api/Dockerfile.dev` is only for development and tests; it includes the full toolchain. The production image will be separate and contain runtime dependencies only.

Start the API with:

```sh
docker compose -f docker-compose.dev.yml up --build api
```

Then fetch `http://localhost:19753/api/v1/openapi.json`.

## Code quality

Run the Compose integration-test command above for the full gate. Running `pnpm check:all` directly also requires `DATABASE_URL` to point to a disposable PostgreSQL database.

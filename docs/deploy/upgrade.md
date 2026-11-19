# Upgrade Tandem

The API image uses the tag in `.env`; the default is `latest-nightly`. Update
the installation from its directory:

```sh
docker compose pull
docker compose down
docker compose up -d
```

For a specific API version, change `TANDEM_API_TAG` in `.env` before pulling.
PostgreSQL remains pinned in `docker-compose.yml`. Keep `.env` and the Docker
volumes when updating; do not run `docker compose down --volumes`.

Check the services and readiness after startup:

```sh
docker compose ps
curl --fail https://budget.example.com/api/v1/health/ready
```

Replace the hostname with your public Tandem URL. If the API does not become
healthy, inspect `docker compose logs tandem-api tandem-postgres` and keep the
volumes intact while investigating.

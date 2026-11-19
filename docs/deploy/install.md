# Install Tandem

Run the installer from the directory where you want Tandem installed. It creates
only `tandem/docker-compose.yml` and `tandem/.env` by default. The Compose file
is refreshed on every run; an existing `.env` is left unchanged.

## Install and start

```sh
curl --fail --silent --show-error --location \
  https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy/tandem-setup.sh \
  --output tandem-setup.sh
bash tandem-setup.sh
```

The installer asks for the install directory (Enter uses `./tandem`), the API
image tag (Enter uses `latest-nightly`), and whether it should add Tandem routes
to a detected Nginx HTTPS site. It supports Ubuntu's packaged Nginx and regular
Docker Nginx. Certbot and your existing certificate renewal remain unchanged.

The installer creates the `.env` on first run with a generated PostgreSQL
password and session encryption key. Keep `.env` private. On later runs it
preserves every setting and secret while replacing `docker-compose.yml` with the
current version. Edit `.env` if you want to change the API image tag or server
settings.

Start Tandem with:

```sh
cd tandem
docker compose pull
docker compose down
docker compose up -d
```

Check the services and API readiness:

```sh
docker compose ps
curl --fail https://budget.example.com/api/v1/health/ready
```

The HTTPS Nginx site must route `/api/v1/` to Tandem. If you declined automatic
Nginx changes or no site was detected, follow [Reverse proxy setup](reverse-proxy.md).
Replace `budget.example.com` with your public Tandem hostname.

Set `ACTUAL_SERVER_URL` in `.env` when the Actual server is not discoverable
through your deployment. For settings, see the [configuration reference](configuration.md).
For updates, see [Upgrade](upgrade.md).

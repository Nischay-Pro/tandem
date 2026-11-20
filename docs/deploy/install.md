# Install Tandem

Run the installer from the directory where you want Tandem installed. By
default it writes `tandem/docker-compose.yml` and `tandem/.env`. Each run
refreshes the Compose file from `main` and uses your existing `.env` as the
starting configuration.

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

The installer creates `.env` on the first run with a generated PostgreSQL
password and session encryption key. Keep `.env` private. Automatic Nginx setup
can update `TANDEM_API_HOST_PORT` and `TRUST_PROXY` when configuring the proxy.

Before starting Tandem, set `ACTUAL_SERVER_URL` in `.env` to the Actual server
URL reachable from the Tandem API container. For example, use
`http://actual:5006` if the Actual service is reachable by that name on the
shared Docker network. For OpenID sign-in, also set `ACTUAL_PUBLIC_URL` to the
public HTTPS URL that reaches Tandem. See the [configuration reference](configuration.md).

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

For updates, see [Upgrade](upgrade.md).

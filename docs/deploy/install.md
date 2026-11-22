# Install extension sync

Tandem needs only your Actual server. Install this service only if you want
planning records, such as savings goals and debt details, to sync between
devices. See [architecture and trust](../concepts/architecture-and-trust.md).

## Requirements

- Docker with Compose
- An Actual server the service can reach
- An HTTPS reverse proxy for the address the app uses

## Set up

1. Run the setup script. It writes `docker-compose.yml` and `.env` to the
   current directory:

   ```sh
   curl -fsSL https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy/tandem-setup.sh | bash
   ```

2. Set `ACTUAL_SERVER_URL` in `.env` to the Actual address reachable from the
   container, for example `http://actual:5006`.
3. Start the services:

   ```sh
   docker compose up -d
   ```

4. Route `/extension/v1/` on your Actual hostname to the service. See
   [reverse proxy](reverse-proxy.md).

The service is ready when `/extension/v1/health/ready` returns
`{"status":"ok"}`.

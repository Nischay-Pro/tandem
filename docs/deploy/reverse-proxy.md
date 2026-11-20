# Reverse proxy setup

Tandem needs the same public HTTPS hostname pattern as the existing Actual
site. Certbot and the current certificate renewal process continue to manage
HTTPS.

During setup, Tandem checks for an existing HTTPS vhost in Ubuntu's packaged
Nginx or a regular Docker Nginx container. If it finds one, it asks before
adding the `/api/v1/` route. If port `19753` is occupied on the host, setup
chooses another free loopback port and updates `.env` and the Nginx route.
Managed Nginx dashboards and ambiguous configurations are left unchanged.

If you declined the change or Nginx was not detected, configure the route
manually.

1. Define the `tandem_auth` and `tandem_api` rate-limit zones and the
   `tandem_api` upstream inside Nginx's `http` context. Copy those declarations
   from [`tandem-upstreams.conf.example`](../../deploy/nginx/tandem-upstreams.conf.example).
   - For host-installed Nginx, set the `tandem_api` upstream to
     `127.0.0.1:<TANDEM_API_HOST_PORT>`. The default port is `19753`.
   - For Docker Nginx, connect it to the network named by `TANDEM_EDGE_NETWORK`
     (default `tandem-edge`) and set the upstream to `tandem-api:19753`.

2. Add the `/api/v1` and `/api/v1/` locations, including the more specific
   `/api/v1/auth/` locations, to the existing HTTPS server block. Use
   [`tandem-locations.conf.example`](../../deploy/nginx/tandem-locations.conf.example)
   for the routing, request headers, and limits. Keep the existing Actual `/`
   location and its COOP/COEP headers.

Set `TRUST_PROXY` in `.env` to the exact proxy address or a narrow CIDR seen by
the API. Do not trust all forwarded headers or expose the API directly.

Check the Nginx configuration, reload it, then verify:

```sh
curl --fail https://budget.example.com/api/v1/health/ready
```

The response should be `{"status":"ok"}`. Actual OpenID returns its session
token in the query string under `/api/v1/auth/actual-return/`. Keep that query
string out of both access and error logs. The example location turns off access
logging and directs error logging for that route to `/dev/null`.

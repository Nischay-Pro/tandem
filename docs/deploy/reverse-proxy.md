# Reverse proxy setup

Tandem needs the same public HTTPS hostname pattern as the existing Actual
site. Certbot and the current certificate renewal process continue to manage
HTTPS.

During setup, Tandem checks for an existing HTTPS vhost in Ubuntu's packaged
Nginx or a regular Docker Nginx container. If it finds one, it asks before
adding the `/api/v1/` route. If port `19753` is occupied on the host, setup
chooses another free loopback port and updates `.env` and the Nginx route.
Managed Nginx dashboards and ambiguous configurations are left unchanged.

If you declined the change or Nginx was not detected, route `/api/v1/` to the
Tandem API on port `19753`:

- For host-installed Nginx, use `http://127.0.0.1:<TANDEM_API_HOST_PORT>`.
- For Docker Nginx, connect it to the `tandem-edge` network and use
  `http://tandem-api:19753`.

Add the route to the existing HTTPS server block before Actual's catch-all.
The locations in [`tandem-locations.conf.example`](../../deploy/nginx/tandem-locations.conf.example)
include the authentication rate limits and request headers. Keep the Actual
`/` route and its existing COOP/COEP headers.

Set `TRUST_PROXY` in `.env` to the exact proxy address or a narrow CIDR seen by
the API. Do not trust all forwarded headers or expose the API directly.

Check the Nginx configuration, reload it, then verify:

```sh
curl --fail https://budget.example.com/api/v1/health/ready
```

The response should be `{"status":"ok"}`. The Actual callback path contains a
short-lived sign-in code, so disable access logging for
`/api/v1/auth/actual-return/`; see the example locations above.

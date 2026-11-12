# Reverse proxy setup

For operators routing a public HTTPS address to Tandem and Actual.

## Steps

1. **Join the services to one private network.** Attach Nginx, the Tandem API,
   and Actual to the same Docker network. If you use a different network name,
   set `TANDEM_EDGE_NETWORK` and use that name in both services.

   **Check:** `docker network inspect tandem-edge` lists the API and proxy.
   If it does not, attach both services to the same external network.

2. **Add the upstreams.** Copy the declarations from
   [`deploy/nginx/tandem-upstreams.conf.example`](../../deploy/nginx/tandem-upstreams.conf.example)
   into Nginx's `http {}` section. The supplied Compose service is
   `tandem-api:19753`; use that name on the shared network.

   **Check:** Nginx can resolve the API and Actual service names on the shared
   network. If not, check their Compose network memberships.

3. **Route `/api/v1/` to Tandem.** Add the locations from
   [`deploy/nginx/tandem-locations.conf.example`](../../deploy/nginx/tandem-locations.conf.example)
   to the existing TLS server block, before Actual's catch-all route. Keep the
   Actual `/` route and its existing COOP/COEP headers.

   **Check:** `nginx -t` succeeds. If the API path displays Actual's web page,
   move the Tandem locations above the Actual catch-all.

4. **Set proxy trust and reload Nginx.** Set `TANDEM_TRUST_PROXY` to the exact proxy
   address or narrow CIDR the API sees. If a CDN sits in front, configure Nginx
   real-IP handling for only that upstream. Keep the API private; do not trust
   all forwarded headers.

   **Check:** Nginx reloads and `curl --fail https://budget.example.com/api/v1/health/ready`
   returns `{"status":"ok"}`. If it fails, use the
   [troubleshooting guide](troubleshooting.md).

The sample disables callback access and non-emergency error logging because
Actual's short-lived sign-in token returns in that callback URL. Custom proxies
must omit its query string from access and error logs too.

For the full install sequence, see [Install the server](install.md). For
settings, see [Configuration](configuration.md).

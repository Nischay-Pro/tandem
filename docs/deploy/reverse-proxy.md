# Reverse proxy

The app connects to your Actual server's HTTPS address. Only extension sync
needs a route of its own.

Add this location to the server block that serves Actual, before Actual's
catch-all:

```nginx
location ^~ /extension/v1/ {
    limit_req zone=tandem_api burst=60 nodelay;
    client_max_body_size 256k;
    proxy_pass http://tandem_api;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    add_header Cache-Control "no-store" always;
}
```

The full example lives in `deploy/nginx/tandem-locations.conf.example`, and
`deploy/configure-docker-nginx.sh` adds it to a Docker Nginx for you after you
approve the change.

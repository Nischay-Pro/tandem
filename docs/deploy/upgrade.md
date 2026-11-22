# Upgrade

Pull the image for the tag in `.env` and recreate the container:

```sh
docker compose pull tandem-api
docker compose up -d tandem-api
```

The service creates or updates its tables on start. To roll back, set
`TANDEM_API_TAG` to the earlier tag and run the same commands.

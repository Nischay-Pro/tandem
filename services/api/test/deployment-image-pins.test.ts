import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";

const nodeImage =
  "node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc";
const postgresImage =
  "postgres:18.6-alpine3.24@sha256:9ba53c7d41ea093baf5c1ba635ba5434c4f979880527d49bb7d9237d3193085b";
const actualImage =
  "ghcr.io/actualbudget/actual:26.11.0@sha256:06cf186569cf58d93e12659ff1a517b65b5b0ebee7050a3991d4bd8a1d398da6";
const nginxImage =
  "nginx:1.30.6-alpine3.24@sha256:e9b2d7624fd7957f4ed44ae0a272d93fb73aeb8512ba8916f8118ae6143fd115";

describe("deployment image digests", () => {
  it("pins every externally sourced image used by builds, deployment, and checks", async () => {
    const imageReferences = [
      { file: "../Dockerfile", images: [nodeImage] },
      { file: "../Dockerfile.dev", images: [nodeImage] },
      {
        file: "../../../deploy/docker-compose.yml",
        images: [postgresImage],
      },
      {
        file: "../../../docker-compose.dev.yml",
        images: [postgresImage, actualImage],
      },
      { file: "../../../deploy/tandem-data.py", images: [nodeImage] },
      {
        file: "../../../scripts/test-dockerignore-secrets.sh",
        images: [nodeImage],
      },
      { file: "../../../.github/workflows/ci.yml", images: [nginxImage] },
    ];

    for (const { file, images } of imageReferences) {
      const contents = await readFile(new URL(file, import.meta.url), "utf8");
      for (const image of images) {
        expect(contents, file).toContain(image);
      }
    }
  });

  it("keeps production pull-only and source builds in the disposable check overlay", async () => {
    const production = await readFile(
      new URL("../../../deploy/docker-compose.yml", import.meta.url),
      "utf8",
    );
    const checkOverlay = await readFile(
      new URL("../../../scripts/compose/production-check.yml", import.meta.url),
      "utf8",
    );

    expect(production).toContain(
      "image: ghcr.io/nischay-pro/tandem-api:${TANDEM_API_TAG:-latest-nightly}",
    );
    expect(production).not.toMatch(/^\s+build:/m);
    expect(checkOverlay).toContain("dockerfile: services/api/Dockerfile");
  });
});

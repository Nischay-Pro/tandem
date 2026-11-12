import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";

const nodeImage =
  "node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072";
const postgresImage =
  "postgres:18.6-alpine3.24@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395";
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
        file: "../../../deploy/compose/production.yml",
        images: [postgresImage],
      },
      {
        file: "../../../docker-compose.dev.yml",
        images: [postgresImage, actualImage],
      },
      { file: "../../../deploy/backup.sh", images: [nodeImage] },
      { file: "../../../deploy/restore.sh", images: [nodeImage] },
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
      new URL("../../../deploy/compose/production.yml", import.meta.url),
      "utf8",
    );
    const checkOverlay = await readFile(
      new URL("../../../deploy/compose/production-check.yml", import.meta.url),
      "utf8",
    );

    expect(production).toContain("image: ${TANDEM_API_IMAGE:");
    expect(production).not.toMatch(/^\s+build:/m);
    expect(checkOverlay).toContain("dockerfile: services/api/Dockerfile");
  });
});

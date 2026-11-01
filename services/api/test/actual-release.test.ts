import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";

describe("Actual compatibility release pin", () => {
  it("pins the published API client and compatibility container to one release", async () => {
    const packageManifest = JSON.parse(
      await readFile(new URL("../package.json", import.meta.url), "utf8"),
    ) as { dependencies: Record<string, string> };
    const actualSessionSource = await readFile(
      new URL("../src/actual/actual-session.ts", import.meta.url),
      "utf8",
    );
    const compose = await readFile(
      new URL("../../../docker-compose.dev.yml", import.meta.url),
      "utf8",
    );
    const supportedVersion = actualSessionSource.match(
      /SUPPORTED_ACTUAL_SERVER_VERSION = "([^"]+)"/,
    )?.[1];

    expect(supportedVersion).toBe(
      packageManifest.dependencies["@actual-app/api"],
    );
    expect(compose).toContain(
      `image: actualbudget/actual-server:${supportedVersion}`,
    );
  });
});

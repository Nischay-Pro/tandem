import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";

describe("Actual compatibility release pin", () => {
  it("keeps the API client, server image, and mobile requirement on one release", async () => {
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
    const mobileUiSource = await readFile(
      new URL(
        "../../../mobile/composeApp/src/commonMain/kotlin/app/tandemforactual/ui/TandemApp.kt",
        import.meta.url,
      ),
      "utf8",
    );
    const supportedVersion = actualSessionSource.match(
      /SUPPORTED_ACTUAL_SERVER_VERSION = "([^"]+)"/,
    )?.[1];
    const mobileSupportedVersion = mobileUiSource.match(
      /private const val SUPPORTED_ACTUAL_SERVER_VERSION = "([^"]+)"/,
    )?.[1];

    expect(supportedVersion).toBe(
      packageManifest.dependencies["@actual-app/api"],
    );
    expect(compose).toContain(
      `image: ghcr.io/actualbudget/actual:${supportedVersion}`,
    );
    expect(mobileSupportedVersion).toBe(supportedVersion);
    expect(mobileUiSource).toMatch(
      /ListRow\(\s*"↗",\s*"Supported server version",\s*"Required by this Tandem release",\s*SUPPORTED_ACTUAL_SERVER_VERSION/s,
    );
  });
});

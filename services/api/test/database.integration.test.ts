import { describe, expect, it } from "vitest";
import { createPostgresDatabase } from "../src/database.js";

describe("PostgreSQL connection integration", () => {
  it("connects, executes the readiness query, and closes its pool", async () => {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error(
        "DATABASE_URL is required; run this test with docker compose",
      );
    }

    const database = createPostgresDatabase(connectionString);
    await expect(database.ping()).resolves.toBeUndefined();
    await expect(database.close()).resolves.toBeUndefined();
  });
});

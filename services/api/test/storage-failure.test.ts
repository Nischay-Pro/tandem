import { describe, expect, it } from "vitest";
import { isStorageFullError } from "../src/storage-failure.js";

describe("storage-full error detection", () => {
  it("recognizes PostgreSQL disk-full errors", () => {
    expect(
      isStorageFullError(
        Object.assign(new Error("write failed"), { code: "53100" }),
      ),
    ).toBe(true);
  });

  it("recognizes wrapped SQLite and filesystem errors", () => {
    expect(
      isStorageFullError(
        new Error("Actual write failed", {
          cause: new Error("SQLITE_FULL: database or disk is full"),
        }),
      ),
    ).toBe(true);
    expect(
      isStorageFullError(
        Object.assign(new Error("write failed"), { code: "ENOSPC" }),
      ),
    ).toBe(true);
    expect(
      isStorageFullError(
        Object.assign(new Error("write failed"), { errno: "ENOSPC" }),
      ),
    ).toBe(true);
  });

  it("does not relabel unrelated storage failures", () => {
    expect(isStorageFullError(new Error("disk I/O error"))).toBe(false);
    expect(isStorageFullError({ message: 123 })).toBe(false);
  });
});

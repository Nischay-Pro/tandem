import { describe, expect, it, vi } from "vitest";
import {
  actualWorkspaceHasFreeSpace,
  readActualStorageReadiness,
} from "../src/actual-storage-readiness.js";

describe("actualWorkspaceHasFreeSpace", () => {
  it("checks the existing workspace path", () => {
    const readStats = vi.fn(() => ({ bavail: 2 }) as never);

    expect(actualWorkspaceHasFreeSpace("/actual", readStats)).toBe(true);
    expect(readStats).toHaveBeenCalledWith("/actual");
  });

  it("reports a full filesystem", () => {
    expect(
      actualWorkspaceHasFreeSpace("/actual", () => ({ bavail: 0 }) as never),
    ).toBe(false);
  });

  it("checks the nearest existing parent when the workspace is not created yet", () => {
    const readStats = vi.fn((path: string) => {
      if (path === "/actual/new") {
        throw Object.assign(new Error("missing"), { code: "ENOENT" });
      }
      return { bavail: 1 } as never;
    });

    expect(actualWorkspaceHasFreeSpace("/actual/new", readStats)).toBe(true);
    expect(readStats.mock.calls.map(([path]) => path)).toEqual([
      "/actual/new",
      "/actual",
    ]);
  });

  it("propagates a missing root filesystem", () => {
    const missingRoot = Object.assign(new Error("missing root"), {
      code: "ENOENT",
    });

    expect(() =>
      actualWorkspaceHasFreeSpace("/", () => {
        throw missingRoot;
      }),
    ).toThrow(missingRoot);
  });

  it.each([
    Object.assign(new Error("permission denied"), { code: "EACCES" }),
    new Error("unknown filesystem error"),
  ])("propagates filesystem errors that are not a missing path", (error) => {
    expect(() =>
      actualWorkspaceHasFreeSpace("/actual", () => {
        throw error;
      }),
    ).toThrow(error);
  });

  it("propagates a null filesystem failure", () => {
    let thrown: unknown;
    try {
      actualWorkspaceHasFreeSpace("/actual", () => {
        throw null;
      });
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeNull();
  });
});

describe("readActualStorageReadiness", () => {
  it.each([
    [{ ready: false }, { ready: false }],
    [
      { ready: false, message: "Actual is unavailable." },
      { ready: false, message: "Actual is unavailable." },
    ],
  ])("preserves startup readiness", (startup, expected) => {
    const hasFreeSpace = vi.fn();

    expect(
      readActualStorageReadiness("/actual", startup, hasFreeSpace),
    ).toEqual(expected);
    expect(hasFreeSpace).not.toHaveBeenCalled();
  });

  it("reports a full workspace", () => {
    expect(
      readActualStorageReadiness("/actual", { ready: true }, () => false),
    ).toEqual({
      ready: false,
      code: "server_storage_full",
      message:
        "Tandem server storage is full. Free disk space on the server and try again.",
    });
  });

  it("reports failures checking free space", () => {
    expect(
      readActualStorageReadiness("/actual", { ready: true }, () => {
        throw new Error("filesystem details stay private");
      }),
    ).toEqual({
      ready: false,
      message:
        "Tandem could not check available server storage. Check the server and try again.",
    });
  });

  it("reports ready when the workspace has space", () => {
    expect(
      readActualStorageReadiness("/actual", { ready: true }, () => true),
    ).toEqual({ ready: true });
  });
});

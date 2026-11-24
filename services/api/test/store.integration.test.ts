import pg from "pg";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { PostgresExtensionStore } from "../src/store.js";

const url = process.env.DATABASE_URL;
const record = {
  recordType: "planning.debt_terms",
  schemaVersion: 1,
  revision: 1,
  baseRevision: null,
  deleted: false,
  iv: "aXY=",
  ciphertext: "Yw==",
};

describe.skipIf(!url)("Postgres extension store", () => {
  let store: PostgresExtensionStore;
  const scope = `test|${Date.now()}`;

  beforeAll(async () => {
    store = new PostgresExtensionStore(new pg.Pool({ connectionString: url }));
    await store.ensureSchema();
    await store.ensureSchema();
  });

  afterAll(async () => {
    await store.close();
  });

  it("keeps revisions linear and pages by sequence", async () => {
    await store.ping();
    const first = await store.write(scope, "r1", record);
    expect(first.status).toBe("stored");
    expect(await store.write(scope, "r1", record)).toMatchObject({
      status: "conflict",
      current: { revision: 1 },
    });
    expect(
      await store.write(scope, "r2", {
        ...record,
        revision: 3,
        baseRevision: 2,
      }),
    ).toEqual({ status: "conflict", current: undefined });
    await store.write(scope, "r1", {
      ...record,
      revision: 2,
      baseRevision: 1,
      deleted: true,
    });
    await store.write(scope, "r2", record);
    const page = await store.changes(scope, 0, 1);
    expect(page.hasMore).toBe(true);
    expect(page.records[0]).toMatchObject({
      recordId: "r1",
      revision: 2,
      deleted: true,
      iv: "aXY=",
      ciphertext: "Yw==",
    });
    const rest = await store.changes(scope, page.cursor, 10);
    expect(rest).toMatchObject({
      hasMore: false,
      records: [{ recordId: "r2" }],
    });
    expect(await store.changes(scope, rest.cursor, 10)).toEqual({
      records: [],
      cursor: rest.cursor,
      hasMore: false,
      restored: false,
    });
    expect((await store.changes(scope, rest.cursor, 1, "r2")).restored).toBe(
      false,
    );
    expect((await store.changes(scope, rest.cursor, 1, "r1")).restored).toBe(
      true,
    );
    expect((await store.changes(scope, rest.cursor + 99, 1)).restored).toBe(
      true,
    );
  });

  it("guards the wrapped key with the expected key id", async () => {
    const key = {
      keyId: "k1",
      wrappedKey: "dw==",
      wrapSalt: "cw==",
      keyCheck: "Yw==",
    };
    expect(await store.key(scope)).toBeUndefined();
    expect(await store.putKey(scope, key, null)).toBe(true);
    expect(await store.putKey(scope, { ...key, keyId: "k2" }, null)).toBe(
      false,
    );
    expect(await store.putKey(scope, { ...key, keyId: "k2" }, "k1")).toBe(true);
    expect(await store.key(scope)).toEqual({ ...key, keyId: "k2" });
  });

  it("rolls back a failed write", async () => {
    await expect(
      store.write(scope, "r9", { ...record, schemaVersion: Number.NaN }),
    ).rejects.toThrow();
    expect(
      (await store.changes(scope, 0, 50)).records.some(
        (r) => r.recordId === "r9",
      ),
    ).toBe(false);
  });
});

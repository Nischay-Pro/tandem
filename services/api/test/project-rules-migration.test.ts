import { describe, expect, it, vi } from "vitest";
import type { PoolClient } from "pg";
import {
  down,
  up,
} from "../src/schema/migrations/20261107100000-project-rules-and-schedules.js";

describe("project rule and schedule migration", () => {
  it("adds rule and schedule records to both journal constraints", async () => {
    const calls: string[] = [];
    const query = vi.fn(async (sql: string) => {
      calls.push(sql);
      return { rows: [], rowCount: 0 };
    });

    await up({ query } as unknown as PoolClient);

    expect(query).toHaveBeenCalledTimes(2);
    expect(calls[0]).toContain("companion_budget_records");
    expect(calls[1]).toContain("companion_budget_changes");
    for (const sql of calls) {
      expect(sql).toContain("ALTER TABLE companion_budget_");
      expect(sql).toContain("'rule', 'schedule'");
    }
  });

  it("removes rule and schedule journal rows before restoring the old constraints", async () => {
    const calls: string[] = [];
    const query = vi.fn(async (sql: string) => {
      calls.push(sql);
      return { rows: [], rowCount: 0 };
    });

    await down({ query } as unknown as PoolClient);

    expect(query).toHaveBeenCalledTimes(4);
    expect(calls[0]).toContain("DELETE FROM companion_budget_changes");
    expect(calls[1]).toContain("DELETE FROM companion_budget_records");
    expect(calls[2]).toContain("ALTER TABLE companion_budget_records");
    expect(calls[2]).not.toContain("'rule', 'schedule'");
  });
});

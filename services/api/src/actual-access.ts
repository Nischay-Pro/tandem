import { createHash } from "node:crypto";

const CACHE_MS = 60_000;
const DENIED_MS = 10_000;
const MAX_CACHED = 10_000;

export type Access = "allowed" | "denied" | "unavailable";

export interface BudgetAccess {
  check(token: string, budgetId: string): Promise<Access>;
  /** True once Actual has accepted this token recently. */
  isKnown?(token: string): boolean;
}

type Fetch = (
  input: string,
  init: { headers: Record<string, string>; signal: AbortSignal },
) => Promise<{
  ok: boolean;
  status: number;
  json(): Promise<unknown>;
}>;

/**
 * Asks the Actual server whether a session can see a budget. The service never trusts a budget id on its own: the
 * caller's Actual token has to list it. Answers are cached briefly per token so sync doesn't hit Actual on every call.
 */
export class ActualBudgetAccess implements BudgetAccess {
  private readonly cache = new Map<
    string,
    { budgets: Set<string> | null; until: number }
  >();

  constructor(
    private readonly actualServerUrl: string,
    private readonly fetcher: Fetch = fetch as unknown as Fetch,
    private readonly now: () => number = Date.now,
  ) {}

  async check(token: string, budgetId: string): Promise<Access> {
    const cacheKey = hash(token);
    const cached = this.cache.get(cacheKey);
    if (cached && cached.until > this.now())
      return cached.budgets?.has(budgetId) ? "allowed" : "denied";
    let response: Awaited<ReturnType<Fetch>>;
    try {
      response = await this.fetcher(
        `${this.actualServerUrl}/sync/list-user-files`,
        {
          headers: { "x-actual-token": token },
          signal: AbortSignal.timeout(5_000),
        },
      );
    } catch {
      return "unavailable";
    }
    if (response.status === 401 || response.status === 403) {
      this.remember(cacheKey, null, DENIED_MS);
      return "denied";
    }
    if (!response.ok) return "unavailable";
    const body = (await response.json().catch(() => undefined)) as
      { data?: Array<{ groupId?: unknown; deleted?: unknown }> } | undefined;
    if (!Array.isArray(body?.data)) return "unavailable";
    const budgets = new Set(
      body.data
        .filter((file) => !file.deleted && typeof file.groupId === "string")
        .map((file) => file.groupId as string),
    );
    this.remember(cacheKey, budgets, CACHE_MS);
    return budgets.has(budgetId) ? "allowed" : "denied";
  }

  isKnown(token: string): boolean {
    const cached = this.cache.get(hash(token));
    return !!cached?.budgets && cached.until > this.now();
  }

  private remember(key: string, budgets: Set<string> | null, ttl: number) {
    const now = this.now();
    if (this.cache.size >= MAX_CACHED) {
      for (const [stale, entry] of this.cache) {
        if (entry.until <= now) this.cache.delete(stale);
      }
    }
    while (this.cache.size >= MAX_CACHED) {
      this.cache.delete(this.cache.keys().next().value!);
    }
    this.cache.delete(key);
    this.cache.set(key, { budgets, until: now + ttl });
  }
}

const hash = (token: string) =>
  createHash("sha256").update(token).digest("hex");

import { createHash } from "node:crypto";

const CACHE_MS = 60_000;

export type Access = "allowed" | "denied" | "unavailable";

export interface BudgetAccess {
  check(token: string, budgetId: string): Promise<Access>;
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
    { budgets: Set<string>; until: number }
  >();

  constructor(
    private readonly actualServerUrl: string,
    private readonly fetcher: Fetch = fetch as unknown as Fetch,
    private readonly now: () => number = Date.now,
  ) {}

  async check(token: string, budgetId: string): Promise<Access> {
    const cacheKey = createHash("sha256").update(token).digest("hex");
    const cached = this.cache.get(cacheKey);
    if (cached && cached.until > this.now())
      return cached.budgets.has(budgetId) ? "allowed" : "denied";
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
    if (response.status === 401 || response.status === 403) return "denied";
    if (!response.ok) return "unavailable";
    const body = (await response.json().catch(() => undefined)) as
      { data?: Array<{ groupId?: unknown; deleted?: unknown }> } | undefined;
    if (!Array.isArray(body?.data)) return "unavailable";
    const budgets = new Set(
      body.data
        .filter((file) => !file.deleted && typeof file.groupId === "string")
        .map((file) => file.groupId as string),
    );
    this.cache.set(cacheKey, { budgets, until: this.now() + CACHE_MS });
    return budgets.has(budgetId) ? "allowed" : "denied";
  }
}

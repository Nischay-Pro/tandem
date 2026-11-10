import { createHash } from "node:crypto";

export interface ActualPasswordGrant {
  principalId: string;
  sessionToken: string;
  budgetIds: string[];
}

export class ActualPasswordRejectedError extends Error {
  constructor() {
    super("Actual sign-in was rejected");
    this.name = "ActualPasswordRejectedError";
  }
}

export class ActualPasswordUnavailableError extends Error {
  constructor() {
    super("Actual sign-in is unavailable");
    this.name = "ActualPasswordUnavailableError";
  }
}

export class ActualPasswordLogin {
  constructor(
    private readonly serverURL: string,
    private readonly fetchRequest: typeof fetch = fetch,
    private readonly timeoutMs = 10_000,
  ) {}

  async signIn(password: string): Promise<ActualPasswordGrant> {
    if (password.length < 1 || password.length > 1_024) {
      throw new ActualPasswordRejectedError();
    }

    const serverURL = new URL(this.serverURL);
    let response: Response;
    try {
      const endpoint = new URL(serverURL);
      endpoint.pathname = `${endpoint.pathname.replace(/\/+$/, "")}/account/login`;
      endpoint.search = "";
      endpoint.hash = "";
      response = await this.fetchRequest(endpoint, {
        method: "POST",
        headers: {
          accept: "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({ loginMethod: "password", password }),
        signal: AbortSignal.timeout(this.timeoutMs),
        cache: "no-store",
      });
    } catch {
      throw new ActualPasswordUnavailableError();
    }

    if (response.status === 401 || response.status === 403) {
      throw new ActualPasswordRejectedError();
    }
    if (!response.ok) throw new ActualPasswordUnavailableError();

    let value: unknown;
    try {
      value = await response.json();
    } catch {
      throw new ActualPasswordUnavailableError();
    }
    const sessionToken = readSessionToken(value);
    if (!sessionToken) throw new ActualPasswordRejectedError();

    const principalId = await this.identifySessionToken(sessionToken);
    return {
      principalId,
      sessionToken,
      budgetIds: [],
    };
  }

  async identifySessionToken(sessionToken: string): Promise<string> {
    if (sessionToken.length === 0 || sessionToken.length > 4_096) {
      throw new ActualPasswordRejectedError();
    }
    const serverURL = new URL(this.serverURL);
    const userId = await this.readValidatedUserId(serverURL, sessionToken);
    const userHash = createHash("sha256")
      .update(serverURL.origin, "utf8")
      .update("\0", "utf8")
      .update(userId, "utf8")
      .digest("hex");
    return `actual-user:${userHash}`;
  }

  async startOpenIdSignIn(returnUrl: string): Promise<string> {
    let response: Response;
    try {
      const endpoint = new URL(this.serverURL);
      endpoint.pathname = `${endpoint.pathname.replace(/\/+$/, "")}/account/login`;
      endpoint.search = "";
      endpoint.hash = "";
      response = await this.fetchRequest(endpoint, {
        method: "POST",
        headers: {
          accept: "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({ loginMethod: "openid", returnUrl }),
        signal: AbortSignal.timeout(this.timeoutMs),
        cache: "no-store",
      });
    } catch {
      throw new ActualPasswordUnavailableError();
    }
    if (!response.ok) throw new ActualPasswordUnavailableError();
    let value: unknown;
    try {
      value = await response.json();
    } catch {
      throw new ActualPasswordUnavailableError();
    }
    const redirectUrl = readRedirectUrl(value);
    if (!redirectUrl) throw new ActualPasswordRejectedError();
    return redirectUrl;
  }

  private async readValidatedUserId(
    serverURL: URL,
    sessionToken: string,
  ): Promise<string> {
    let response: Response;
    try {
      const endpoint = new URL(serverURL);
      endpoint.pathname = `${endpoint.pathname.replace(/\/+$/, "")}/account/validate`;
      endpoint.search = "";
      endpoint.hash = "";
      response = await this.fetchRequest(endpoint, {
        headers: {
          accept: "application/json",
          "X-ACTUAL-TOKEN": sessionToken,
        },
        signal: AbortSignal.timeout(this.timeoutMs),
        cache: "no-store",
      });
    } catch {
      throw new ActualPasswordUnavailableError();
    }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new ActualPasswordRejectedError();
      }
      throw new ActualPasswordUnavailableError();
    }
    let value: unknown;
    try {
      value = await response.json();
    } catch {
      throw new ActualPasswordUnavailableError();
    }
    const userId = readValidatedUserId(value);
    if (userId === undefined) throw new ActualPasswordRejectedError();
    return userId;
  }
}

function readRedirectUrl(value: unknown): string | undefined {
  if (!isRecord(value)) return undefined;
  const data = isRecord(value.data) ? value.data : undefined;
  const redirectUrl = data?.returnUrl ?? value.returnUrl;
  if (typeof redirectUrl !== "string" || redirectUrl.length > 4_096) {
    return undefined;
  }
  try {
    const parsed = new URL(redirectUrl);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
      return undefined;
    }
    if (parsed.username || parsed.password) return undefined;
    return parsed.toString();
  } catch {
    return undefined;
  }
}

function readSessionToken(value: unknown): string | undefined {
  if (!isRecord(value)) return undefined;
  const data = isRecord(value.data) ? value.data : undefined;
  const token = data?.token ?? value.token;
  return typeof token === "string" && token.length > 0 && token.length <= 4_096
    ? token
    : undefined;
}

function readValidatedUserId(value: unknown): string | undefined {
  if (!isRecord(value) || value.status !== "ok" || !isRecord(value.data)) {
    return undefined;
  }
  const userId = value.data.userId;
  return typeof userId === "string" &&
    userId.trim().length > 0 &&
    userId.length <= 200
    ? userId
    : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

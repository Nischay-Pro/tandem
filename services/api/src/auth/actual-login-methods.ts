export type ActualLoginMethod = "password" | "openid" | "unsupported";

export interface ActualLoginMethodOption {
  method: "password" | "openid";
  displayName: string;
  active: boolean;
}

export interface ActualLoginMethods {
  bootstrapped: boolean;
  loginMethod: ActualLoginMethod | null;
  availableLoginMethods: ActualLoginMethodOption[];
}

export class ActualLoginMethodsUnavailableError extends Error {
  constructor() {
    super("Could not read Actual login methods");
    this.name = "ActualLoginMethodsUnavailableError";
  }
}

const knownMethods = new Set<string>(["password", "openid"]);

export class ActualLoginMethodsReader {
  constructor(
    private readonly serverURL: string,
    private readonly fetchRequest: typeof fetch = fetch,
    private readonly timeoutMs = 5_000,
  ) {}

  async read(): Promise<ActualLoginMethods> {
    try {
      const endpoint = new URL(this.serverURL);
      endpoint.pathname = `${endpoint.pathname.replace(/\/+$/, "")}/account/needs-bootstrap`;
      endpoint.search = "";
      endpoint.hash = "";

      const response = await this.fetchRequest(endpoint, {
        headers: { accept: "application/json" },
        signal: AbortSignal.timeout(this.timeoutMs),
        cache: "no-store",
      });
      if (!response.ok) throw new ActualLoginMethodsUnavailableError();

      return parseActualLoginMethods(await response.json());
    } catch {
      throw new ActualLoginMethodsUnavailableError();
    }
  }
}

function parseActualLoginMethods(value: unknown): ActualLoginMethods {
  if (!isRecord(value) || value.status !== "ok" || !isRecord(value.data)) {
    throw new ActualLoginMethodsUnavailableError();
  }

  const data = value.data;
  if (typeof data.bootstrapped !== "boolean") {
    throw new ActualLoginMethodsUnavailableError();
  }

  const availableLoginMethods = Array.isArray(data.availableLoginMethods)
    ? data.availableLoginMethods.flatMap(parseLoginMethodOption)
    : [];

  return {
    bootstrapped: data.bootstrapped,
    loginMethod: parseSelectedLoginMethod(data.loginMethod),
    availableLoginMethods,
  };
}

function parseLoginMethodOption(value: unknown): ActualLoginMethodOption[] {
  if (!isRecord(value) || !isKnownMethod(value.method)) return [];

  return [
    {
      method: value.method,
      displayName: safeDisplayName(value.displayName, value.method),
      active: value.active === true || value.active === 1,
    },
  ];
}

function parseSelectedLoginMethod(value: unknown): ActualLoginMethod | null {
  if (typeof value !== "string") return null;
  return isKnownMethod(value) ? value : "unsupported";
}

function isKnownMethod(
  value: unknown,
): value is ActualLoginMethodOption["method"] {
  return typeof value === "string" && knownMethods.has(value);
}

function safeDisplayName(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  const displayName = [...value]
    .filter((character) => {
      const codePoint = character.codePointAt(0) as number;
      return codePoint >= 0x20 && codePoint !== 0x7f;
    })
    .join("")
    .trim();
  return displayName.length > 0 ? displayName.slice(0, 80) : fallback;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

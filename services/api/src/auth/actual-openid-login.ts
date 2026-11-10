import { randomBytes } from "node:crypto";
import {
  ActualPasswordLogin,
  type ActualPasswordGrant,
} from "./actual-password-login.js";

const STATE_TTL_MS = 5 * 60_000;
const COMPLETION_TTL_MS = 90_000;
const MAX_PENDING_LOGINS = 1_000;

interface PendingLogin {
  deviceName: string;
  expiresAt: number;
}

export interface OpenIdCompletion {
  deviceName: string;
  grant: ActualPasswordGrant;
}

export class ActualOpenIdLogin {
  readonly #pending = new Map<string, PendingLogin>();
  readonly #completions = new Map<
    string,
    { value: OpenIdCompletion; expiresAt: number }
  >();

  constructor(
    private readonly actualLogin: ActualPasswordLogin,
    private readonly publicUrl: string,
    private readonly prepareGrant: (
      grant: ActualPasswordGrant,
    ) => Promise<ActualPasswordGrant>,
    private readonly now: () => number = Date.now,
    private readonly makeCode: () => string = () =>
      randomBytes(32).toString("base64url"),
  ) {}

  async start(deviceName: string): Promise<{ redirectUrl: string }> {
    this.#removeExpired();
    if (this.#pending.size >= MAX_PENDING_LOGINS) {
      throw new Error("OpenID sign-in is temporarily busy");
    }
    const normalizedDeviceName = deviceName.trim();
    if (normalizedDeviceName.length < 1 || normalizedDeviceName.length > 100) {
      throw new Error("Device name is invalid");
    }
    const state = this.makeCode();
    const callbackUrl = new URL(this.publicUrl);
    callbackUrl.pathname = `${callbackUrl.pathname.replace(/\/+$/, "")}/api/v1/auth/actual-return/${state}/openid-cb`;
    callbackUrl.search = "";
    callbackUrl.hash = "";
    const redirectUrl = await this.actualLogin.startOpenIdSignIn(
      callbackUrl.toString(),
    );
    this.#pending.set(state, {
      deviceName: normalizedDeviceName,
      expiresAt: this.now() + STATE_TTL_MS,
    });
    return { redirectUrl };
  }

  async completeCallback(
    state: string,
    sessionToken: string,
  ): Promise<string | undefined> {
    this.#removeExpired();
    const pending = this.#pending.get(state);
    this.#pending.delete(state);
    if (!pending || pending.expiresAt <= this.now()) return undefined;

    const grant: ActualPasswordGrant = {
      principalId: await this.actualLogin.identifySessionToken(sessionToken),
      sessionToken,
      budgetIds: [],
    };
    const preparedGrant = await this.prepareGrant(grant);
    const code = this.makeCode();
    this.#completions.set(code, {
      value: { deviceName: pending.deviceName, grant: preparedGrant },
      expiresAt: this.now() + COMPLETION_TTL_MS,
    });
    return `tandem://signin/complete?code=${encodeURIComponent(code)}`;
  }

  consume(code: string): OpenIdCompletion | undefined {
    this.#removeExpired();
    const completion = this.#completions.get(code);
    this.#completions.delete(code);
    return completion && completion.expiresAt > this.now()
      ? completion.value
      : undefined;
  }

  #removeExpired(): void {
    const now = this.now();
    for (const [state, pending] of this.#pending) {
      if (pending.expiresAt <= now) this.#pending.delete(state);
    }
    for (const [code, completion] of this.#completions) {
      if (completion.expiresAt <= now) this.#completions.delete(code);
    }
  }
}

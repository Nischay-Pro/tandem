import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import {
  ActualPasswordLogin,
  type ActualPasswordGrant,
} from "./actual-password-login.js";

const STATE_TTL_MS = 5 * 60_000;
const COMPLETION_TTL_MS = 90_000;
const MAX_PENDING_LOGINS = 1_000;
const MOBILE_SIGN_IN_RETURN_URI = "app.tandemforactual://signin/complete";
const OPENID_BROWSER_COOKIE_PREFIX = "tandem_openid_";

export function openIdBrowserCookieName(state: string): string {
  return `${OPENID_BROWSER_COOKIE_PREFIX}${state}`;
}

interface PendingLogin {
  deviceName: string;
  codeChallenge: string;
  callbackState: string;
  actualRedirectUrl: string;
  browserCookieHash?: string;
  expiresAt: number;
}

export interface OpenIdBrowserHandoff {
  redirectUrl: string;
  setCookie: string;
}

export interface OpenIdCompletion {
  deviceName: string;
  grant: ActualPasswordGrant;
}

export class ActualOpenIdLogin {
  readonly #pending = new Map<string, PendingLogin>();
  readonly #completions = new Map<
    string,
    { value: OpenIdCompletion; codeChallenge: string; expiresAt: number }
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

  async start(
    deviceName: string,
    codeChallenge: string,
    callbackState: string,
  ): Promise<{ redirectUrl: string }> {
    this.#removeExpired();
    if (this.#pending.size >= MAX_PENDING_LOGINS) {
      throw new Error("OpenID sign-in is temporarily busy");
    }
    if (!/^[A-Za-z0-9_-]{43}$/u.test(codeChallenge)) {
      throw new Error("OpenID code challenge is invalid");
    }
    if (!/^[A-Za-z0-9_-]{43}$/u.test(callbackState)) {
      throw new Error("OpenID callback state is invalid");
    }
    const normalizedDeviceName = deviceName.trim();
    if (normalizedDeviceName.length < 1 || normalizedDeviceName.length > 100) {
      throw new Error("Device name is invalid");
    }
    const state = this.makeCode();
    const redirectUrl = await this.actualLogin.startOpenIdSignIn(
      this.#actualCallbackUrl(state),
    );
    this.#pending.set(state, {
      deviceName: normalizedDeviceName,
      codeChallenge,
      callbackState,
      actualRedirectUrl: redirectUrl,
      expiresAt: this.now() + STATE_TTL_MS,
    });
    return { redirectUrl: this.#browserStartUrl(state) };
  }

  startBrowserHandoff(
    state: string,
    existingCookieValue?: string,
  ): OpenIdBrowserHandoff | undefined {
    this.#removeExpired();
    const pending = this.#pending.get(state);
    if (!pending || pending.expiresAt <= this.now()) return undefined;

    let cookieValue = existingCookieValue;
    if (pending.browserCookieHash) {
      if (!cookieValue || !this.#browserCookieMatches(pending, cookieValue)) {
        return undefined;
      }
    } else {
      cookieValue = randomBytes(32).toString("base64url");
      pending.browserCookieHash = this.#hash(cookieValue);
    }

    const cookieName = openIdBrowserCookieName(state);
    const callbackPath = new URL(this.#actualCallbackUrl(state)).pathname;
    const maxAgeSeconds = Math.max(
      0,
      Math.floor((pending.expiresAt - this.now()) / 1_000),
    );
    const secure = new URL(this.publicUrl).protocol === "https:";
    const cookieAttributes = `Path=${callbackPath}; Max-Age=${maxAgeSeconds}; HttpOnly; SameSite=Lax${secure ? "; Secure" : ""}`;

    return {
      redirectUrl: pending.actualRedirectUrl,
      setCookie: `${cookieName}=${cookieValue}; ${cookieAttributes}`,
    };
  }

  async completeCallback(
    state: string,
    sessionToken: string,
    browserCookieValue?: string,
  ): Promise<string | undefined> {
    this.#removeExpired();
    const pending = this.#pending.get(state);
    if (
      !pending ||
      pending.expiresAt <= this.now() ||
      !browserCookieValue ||
      !this.#browserCookieMatches(pending, browserCookieValue)
    ) {
      return undefined;
    }
    this.#pending.delete(state);

    const grant: ActualPasswordGrant = {
      principalId: await this.actualLogin.identifySessionToken(sessionToken),
      sessionToken,
      budgetIds: [],
    };
    const preparedGrant = await this.prepareGrant(grant);
    const code = this.makeCode();
    this.#completions.set(code, {
      value: { deviceName: pending.deviceName, grant: preparedGrant },
      codeChallenge: pending.codeChallenge,
      expiresAt: this.now() + COMPLETION_TTL_MS,
    });
    return `${MOBILE_SIGN_IN_RETURN_URI}?code=${encodeURIComponent(code)}&state=${encodeURIComponent(pending.callbackState)}`;
  }

  consume(code: string, codeVerifier: string): OpenIdCompletion | undefined {
    this.#removeExpired();
    const completion = this.#completions.get(code);
    if (
      !completion ||
      completion.expiresAt <= this.now() ||
      !/^[A-Za-z0-9._~-]{43,128}$/u.test(codeVerifier)
    ) {
      return undefined;
    }

    const challenge = createHash("sha256")
      .update(codeVerifier, "ascii")
      .digest("base64url");
    if (
      !timingSafeEqual(
        Buffer.from(challenge, "ascii"),
        Buffer.from(completion.codeChallenge, "ascii"),
      )
    ) {
      return undefined;
    }

    this.#completions.delete(code);
    return completion.value;
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

  #browserCookieMatches(pending: PendingLogin, cookieValue: string): boolean {
    if (
      !pending.browserCookieHash ||
      !/^[A-Za-z0-9_-]{43}$/u.test(cookieValue)
    ) {
      return false;
    }
    const suppliedHash = this.#hash(cookieValue);
    return timingSafeEqual(
      Buffer.from(suppliedHash, "ascii"),
      Buffer.from(pending.browserCookieHash, "ascii"),
    );
  }

  #hash(value: string): string {
    return createHash("sha256").update(value, "ascii").digest("base64url");
  }

  #browserStartUrl(state: string): string {
    const browserStartUrl = new URL(this.publicUrl);
    browserStartUrl.pathname = `${browserStartUrl.pathname.replace(/\/+$/, "")}/api/v1/auth/openid/browser-start/${state}`;
    browserStartUrl.search = "";
    browserStartUrl.hash = "";
    return browserStartUrl.toString();
  }

  #actualCallbackUrl(state: string): string {
    const callbackUrl = new URL(this.publicUrl);
    callbackUrl.pathname = `${callbackUrl.pathname.replace(/\/+$/, "")}/api/v1/auth/actual-return/${state}/openid-cb`;
    callbackUrl.search = "";
    callbackUrl.hash = "";
    return callbackUrl.toString();
  }
}

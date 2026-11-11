import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import {
  ActualPasswordLogin,
  type ActualPasswordGrant,
} from "./actual-password-login.js";

const STATE_TTL_MS = 5 * 60_000;
const COMPLETION_TTL_MS = 90_000;
const MAX_PENDING_LOGINS = 1_000;
const MOBILE_SIGN_IN_RETURN_URI = "app.tandemforactual:/signin/complete";

interface PendingLogin {
  deviceName: string;
  codeChallenge: string;
  callbackState: string;
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
    const callbackUrl = new URL(this.publicUrl);
    callbackUrl.pathname = `${callbackUrl.pathname.replace(/\/+$/, "")}/api/v1/auth/actual-return/${state}/openid-cb`;
    callbackUrl.search = "";
    callbackUrl.hash = "";
    const redirectUrl = await this.actualLogin.startOpenIdSignIn(
      callbackUrl.toString(),
    );
    this.#pending.set(state, {
      deviceName: normalizedDeviceName,
      codeChallenge,
      callbackState,
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
}

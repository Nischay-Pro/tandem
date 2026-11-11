import { isIP } from "node:net";

export interface RequestRateLimitDecision {
  allowed: boolean;
  retryAfterSeconds: number;
}

interface ClientWindow {
  requests: number;
  expiresAt: number;
}

export class RequestRateLimiter {
  #clients = new Map<string, ClientWindow>();

  constructor(
    private readonly maxRequests: number,
    private readonly windowMs: number,
    private readonly maxClients = 10_000,
    private readonly now: () => number = Date.now,
  ) {}

  reserveRequest(clientKey: string): RequestRateLimitDecision {
    const currentTime = this.now();
    const normalizedClientKey = normalizeClientKey(clientKey);
    let window = this.#clients.get(normalizedClientKey);
    if (window && window.expiresAt <= currentTime) {
      this.#clients.delete(normalizedClientKey);
      window = undefined;
    }

    if (!window) {
      this.#removeExpiredClients(currentTime);
      if (this.#clients.size >= this.maxClients) {
        const oldestClient = this.#clients.keys().next().value as string;
        this.#clients.delete(oldestClient);
      }
      window = { requests: 0, expiresAt: currentTime + this.windowMs };
      this.#clients.set(normalizedClientKey, window);
    }

    if (window.requests >= this.maxRequests) {
      return {
        allowed: false,
        retryAfterSeconds: Math.max(
          1,
          Math.ceil((window.expiresAt - currentTime) / 1000),
        ),
      };
    }

    window.requests += 1;
    return { allowed: true, retryAfterSeconds: 0 };
  }

  #removeExpiredClients(currentTime: number): void {
    for (const [clientKey, window] of this.#clients) {
      if (window.expiresAt <= currentTime) this.#clients.delete(clientKey);
    }
  }
}

function normalizeClientKey(clientKey: string): string {
  const address = clientKey.split("%", 1)[0]!;
  const version = isIP(address);
  if (version === 4) return `ipv4:${address}`;
  if (version !== 6) return clientKey;

  const words = expandIPv6Address(address);
  if (!words) return clientKey;
  if (isIPv4MappedAddress(words)) {
    const [first, second] = words.slice(6) as [number, number];
    return `ipv4:${first >> 8}.${first & 0xff}.${second >> 8}.${second & 0xff}`;
  }

  return `ipv6:${words
    .slice(0, 4)
    .map((word) => word.toString(16))
    .join(":")}/64`;
}

function expandIPv6Address(address: string): number[] | undefined {
  let expandedInput = address;
  if (address.includes(".")) {
    const finalColon = address.lastIndexOf(":");
    const octets = address
      .slice(finalColon + 1)
      .split(".")
      .map(Number);
    if (
      octets.length !== 4 ||
      octets.some((octet) => octet < 0 || octet > 255)
    ) {
      return undefined;
    }
    const firstWord = (octets[0]! << 8) | octets[1]!;
    const secondWord = (octets[2]! << 8) | octets[3]!;
    expandedInput = `${address.slice(0, finalColon)}:${firstWord.toString(16)}:${secondWord.toString(16)}`;
  }

  const compressionIndex = expandedInput.indexOf("::");
  let segments: string[];
  if (compressionIndex === -1) {
    segments = expandedInput.split(":");
    if (segments.length !== 8) return undefined;
  } else {
    const left = expandedInput
      .slice(0, compressionIndex)
      .split(":")
      .filter(Boolean);
    const right = expandedInput
      .slice(compressionIndex + 2)
      .split(":")
      .filter(Boolean);
    const missing = 8 - left.length - right.length;
    if (missing < 1) return undefined;
    segments = [
      ...left,
      ...Array.from({ length: missing }, () => "0"),
      ...right,
    ];
  }

  if (
    segments.length !== 8 ||
    segments.some((segment) => !/^[0-9a-f]{1,4}$/iu.test(segment))
  ) {
    return undefined;
  }
  return segments.map((segment) => Number.parseInt(segment, 16));
}

function isIPv4MappedAddress(words: readonly number[]): boolean {
  return (
    words.length === 8 &&
    words.slice(0, 5).every((word) => word === 0) &&
    words[5] === 0xffff
  );
}

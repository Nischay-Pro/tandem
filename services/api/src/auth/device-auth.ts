import { createHash, randomBytes, randomUUID } from "node:crypto";

export const DEVICE_CREDENTIAL_IDLE_TTL_DAYS = 90;
const SECRET_BYTES = 32;

export interface DeviceRecord {
  deviceId: string;
  principalId: string;
  name: string;
  budgetIds: string[];
  createdAt: Date;
  lastSeenAt: Date | null;
  revokedAt: Date | null;
}

export interface DeviceAuthStore {
  createDevice(input: {
    principalId: string;
    budgetIds: string[];
    deviceId: string;
    deviceName: string;
    credentialHash: string;
  }): Promise<DeviceRecord>;
  authenticate(credentialHash: string): Promise<DeviceRecord | undefined>;
  listDevices(principalId: string): Promise<DeviceRecord[]>;
  revokeDevice(principalId: string, deviceId: string): Promise<boolean>;
}

export class DeviceAuthInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DeviceAuthInputError";
  }
}

export interface SignedInDevice extends DeviceRecord {
  accessToken: string;
}

export class DeviceAuthorization {
  constructor(
    private readonly store: DeviceAuthStore,
    private readonly makeSecret: () => Buffer = () => randomBytes(SECRET_BYTES),
    private readonly makeDeviceId: () => string = randomUUID,
  ) {}

  async createDevice(input: {
    principalId: string;
    budgetIds: string[];
    deviceName: string;
  }): Promise<SignedInDevice> {
    const principalId = validatePrincipalId(input.principalId);
    const budgetIds = validateBudgetIds(input.budgetIds);
    const deviceName = validateDeviceName(input.deviceName);
    const accessToken = this.#newSecret();
    const device = await this.store.createDevice({
      principalId,
      budgetIds,
      deviceId: this.makeDeviceId(),
      deviceName,
      credentialHash: hashSecret(accessToken),
    });
    return { ...device, accessToken };
  }

  async authenticate(
    accessToken: string | undefined,
  ): Promise<DeviceRecord | undefined> {
    if (accessToken === undefined || !isWellFormedCredential(accessToken)) {
      return undefined;
    }
    return this.store.authenticate(hashSecret(accessToken));
  }

  async listDevicesForDevice(device: DeviceRecord): Promise<DeviceRecord[]> {
    const devices = await this.store.listDevices(
      validatePrincipalId(device.principalId),
    );
    if (device.budgetIds.includes("*")) {
      return devices;
    }
    return devices.filter(
      (candidate) => candidate.deviceId === device.deviceId,
    );
  }

  async revokeDeviceForDevice(
    device: DeviceRecord,
    deviceId: string,
  ): Promise<boolean> {
    const principalId = validatePrincipalId(device.principalId);
    const targetDeviceId = validateDeviceId(deviceId);
    if (!device.budgetIds.includes("*") && device.deviceId !== targetDeviceId) {
      return false;
    }
    return this.store.revokeDevice(principalId, targetDeviceId);
  }

  static canAccessBudget(device: DeviceRecord, budgetId: string): boolean {
    return (
      budgetId.length > 0 &&
      (device.budgetIds.includes("*") || device.budgetIds.includes(budgetId))
    );
  }

  #newSecret(): string {
    const secret = this.makeSecret();
    if (secret.length !== SECRET_BYTES) {
      throw new Error("Secure token generator returned an invalid secret");
    }
    return secret.toString("base64url");
  }
}

export function hashSecret(secret: string): string {
  return createHash("sha256").update(secret, "utf8").digest("hex");
}

function isWellFormedCredential(value: string): boolean {
  return /^[A-Za-z0-9_-]{43}$/.test(value);
}

function validatePrincipalId(value: string): string {
  const length = typeof value === "string" ? codePointLength(value) : 0;
  if (
    typeof value !== "string" ||
    length < 1 ||
    length > 200 ||
    value.trim() !== value ||
    hasControlCharacters(value)
  ) {
    throw new DeviceAuthInputError(
      "principalId must be a valid non-empty identifier",
    );
  }
  return value;
}

function validateDeviceName(value: string): string {
  const normalized = typeof value === "string" ? value.trim() : "";
  if (
    typeof value !== "string" ||
    codePointLength(normalized) < 1 ||
    codePointLength(normalized) > 100 ||
    hasControlCharacters(value)
  ) {
    throw new DeviceAuthInputError(
      "deviceName must contain between 1 and 100 characters",
    );
  }
  return normalized;
}

function validateDeviceId(value: string): string {
  if (
    typeof value !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    )
  ) {
    throw new DeviceAuthInputError("deviceId must be a UUID");
  }
  return value;
}

function validateBudgetIds(value: string[]): string[] {
  if (!Array.isArray(value) || value.length > 100) {
    throw new DeviceAuthInputError(
      "budgetIds must contain no more than 100 values",
    );
  }
  if (value.includes("*") && value.length !== 1) {
    throw new DeviceAuthInputError(
      "Wildcard budget access cannot be combined with specific IDs",
    );
  }
  for (const budgetId of value) {
    if (
      typeof budgetId !== "string" ||
      codePointLength(budgetId) < 1 ||
      codePointLength(budgetId) > 500 ||
      budgetId.trim() !== budgetId ||
      hasControlCharacters(budgetId)
    ) {
      throw new DeviceAuthInputError(
        "budgetIds must contain valid non-empty identifiers",
      );
    }
  }
  if (new Set(value).size !== value.length) {
    throw new DeviceAuthInputError("budgetIds must not contain duplicates");
  }
  return [...value];
}

function hasControlCharacters(value: string): boolean {
  for (const character of value) {
    const codePoint = character.charCodeAt(0);
    if (codePoint < 0x20 || codePoint === 0x7f) {
      return true;
    }
  }
  return false;
}

function codePointLength(value: string): number {
  return [...value].length;
}

import { join } from "node:path";
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { durableJson, readJson, Serial } from "./files.ts";
import { endpoint, fail } from "./validation.ts";

interface Device {
  id: string;
  name: string;
  tokenHash: string;
  revoked: boolean;
  createdAt: number;
}
interface Session {
  secretHash: string;
  expiresAt: number;
}
interface State {
  version: 1;
  receiverId: string;
  devices: Device[];
  pending: Session | null;
}
export interface Pairing {
  version: 1;
  receiverId: string;
  endpoint: string;
  fingerprint: string;
  secret: string;
  expiresAt: number;
}
const hash = (token: string) =>
  createHash("sha256").update(token).digest("hex");
const equal = (a: string, b: string) =>
  a.length === b.length &&
  timingSafeEqual(new TextEncoder().encode(a), new TextEncoder().encode(b));
export class Auth {
  private serial = new Serial();
  private attempts: number[] = [];
  private state!: State;
  constructor(readonly directory: string) {}
  get receiverId() {
    return this.state.receiverId;
  }
  async init() {
    this.state = await readJson<State>(join(this.directory, "identity.json")) ??
      {
        version: 1,
        receiverId: crypto.randomUUID(),
        devices: [],
        pending: null,
      };
    if (this.state.version !== 1) {
      throw new Error("Unsupported receiver state version");
    }
    await this.save();
  }
  private save() {
    return durableJson(join(this.directory, "identity.json"), this.state);
  }
  startPairing(address: string, fingerprint: string): Promise<Pairing> {
    return this.serial.run(async () => {
      const secret = randomBytes(32).toString("base64url");
      const expiresAt = Date.now() + 300_000;
      this.state.pending = { secretHash: hash(secret), expiresAt };
      await this.save();
      return {
        version: 1,
        receiverId: this.receiverId,
        endpoint: endpoint(address),
        fingerprint,
        secret,
        expiresAt,
      };
    });
  }
  pair(value: unknown) {
    const now = Date.now();
    this.attempts = this.attempts.filter((time) => time > now - 60_000);
    if (this.attempts.length >= 16) fail("rate_limited", 429);
    this.attempts.push(now);
    return this.serial.run(async () => {
      const v = value as Record<string, unknown>;
      if (
        !v || v.version !== 1 || v.receiverId !== this.receiverId ||
        typeof v.secret !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(v.secret) ||
        typeof v.deviceName !== "string" || v.deviceName.length > 80 ||
        [...v.deviceName].some((c) =>
          c.codePointAt(0)! < 32 || c.codePointAt(0) === 127
        )
      ) fail("pairing_invalid", 403);
      const session = this.state.pending;
      if (
        !session || session.expiresAt <= now ||
        !equal(session.secretHash, hash(v.secret))
      ) fail("pairing_invalid", 403);
      const token = randomBytes(32).toString("base64url");
      const id = crypto.randomUUID();
      this.state.pending = null;
      this.state.devices.push({
        id,
        name: v.deviceName,
        tokenHash: hash(token),
        revoked: false,
        createdAt: now,
      });
      await this.save();
      return { version: 1, receiverId: this.receiverId, deviceId: id, token };
    });
  }
  authenticate(header: string | null): string {
    if (!header || !/^Bearer [A-Za-z0-9_-]{43}$/.test(header)) {
      fail("unauthenticated", 401);
    }
    const digest = hash(header.slice(7));
    const device = this.state.devices.find((d) =>
      !d.revoked && equal(d.tokenHash, digest)
    );
    if (!device) fail("unauthenticated", 401);
    return device.id;
  }
  list() {
    return this.state.devices.map(({ id, name, revoked, createdAt }) => ({
      id,
      name,
      revoked,
      createdAt,
    }));
  }
  revoke(id: string): Promise<void> {
    return this.serial.run(async () => {
      const device = this.state.devices.find((d) => d.id === id);
      if (!device) throw new Error("Unknown device ID");
      device.revoked = true;
      await this.save();
    });
  }
  cancelPairing() {
    return this.serial.run(async () => {
      this.state.pending = null;
      await this.save();
    });
  }
}

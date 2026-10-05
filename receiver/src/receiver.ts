import { join, resolve } from "node:path";
import { Auth, type Pairing } from "./auth.ts";
import { PhotoStore } from "./store.ts";
import { handler } from "./server.ts";
import { certificate } from "./tls.ts";
import { durableJson, noLinks, readJson, Serial } from "./files.ts";
import { privateMode } from "./platform.ts";
import { detectLanAddress } from "./lan.ts";
import { listenWithFallback } from "./listen.ts";
import {
  integerOption,
  type ReceiverSettings,
  receiverSettings,
} from "./receiver-settings.ts";
import { localIp, type Receipt } from "./validation.ts";
// @ts-types="qrcode-types"
import QRCode from "qrcode";
export interface Arrival extends Receipt {
  root: string;
}
export class Receiver {
  readonly auth: Auth;
  settings!: ReceiverSettings;
  private lock?: Deno.FsFile;
  private server?: Deno.HttpServer;
  private store?: PhotoStore;
  private mutations = new Serial();
  private saveActivity = new Serial();
  private recent: Arrival[] = [];
  private active = 0;
  private status = "stopped";
  private error: string | null = null;
  private pairing: Pairing | null = null;
  private qr: string | null = null;
  address: string | null = null;
  fingerprint: string | null = null;
  interfaceName: string | null = null;
  constructor(
    readonly state: string,
    private values = new Map<string, string>(),
  ) {
    this.auth = new Auth(state);
  }
  async init(management = false) {
    await Deno.mkdir(this.state, { recursive: true, mode: 0o700 });
    await noLinks(this.state);
    await privateMode(this.state, 0o700);
    const path = join(this.state, "process.lock");
    try {
      const info = await Deno.lstat(path);
      if (info.isSymlink || !info.isFile) throw new Error("Unsafe state lock");
    } catch (e) {
      if (!(e instanceof Deno.errors.NotFound)) throw e;
    }
    this.lock = await Deno.open(path, {
      create: true,
      write: true,
      mode: 0o600,
    });
    if (!await this.lock.tryLock(true)) {
      this.lock.close();
      this.lock = undefined;
      throw new Error(
        "Receiver state is in use; stop the running receiver before offline management.",
      );
    }
    await this.lock.truncate(0);
    await this.lock.write(new TextEncoder().encode(String(Deno.pid)));
    await this.auth.init();
    if (management) return;
    const saved = await readJson<ReceiverSettings>(
      join(this.state, "receiver-config.json"),
    );
    this.settings = receiverSettings(this.values, saved);
    new PhotoStore(this.settings.root, this.state, this.auth.receiverId);
    if (!saved && !this.values.has("--root")) {
      for (const name of ["receipts", "journals"]) {
        try {
          for await (const entry of Deno.readDir(join(this.state, name))) {
            if (entry.isFile && entry.name.endsWith(".json")) {
              throw new Error(
                "Existing uploads from an older receiver were found. Start once with --root YOUR_EXISTING_PHOTO_FOLDER; it will be remembered automatically.",
              );
            }
          }
        } catch (e) {
          if (!(e instanceof Deno.errors.NotFound)) throw e;
        }
      }
    }
    this.recent =
      await readJson<Arrival[]>(join(this.state, "activity.json")) ?? [];
  }
  snapshot() {
    if (
      this.pairing &&
      (this.pairing.expiresAt <= Date.now() ||
        this.auth.list().some((d) =>
          !d.revoked && d.createdAt >= this.pairing!.expiresAt - 300_000
        ))
    ) {
      this.pairing = null;
      this.qr = null;
    }
    return {
      version: 1,
      status: this.status,
      error: this.error,
      root: this.settings?.root ?? null,
      endpoint: this.address,
      interfaceName: this.interfaceName,
      receiverId: this.auth.receiverId,
      fingerprint: this.fingerprint,
      active: this.active,
      pairing: this.pairing,
      qr: this.qr,
      devices: this.auth.list().filter((d) => !d.revoked),
      recent: this.recent,
    };
  }
  start() {
    return this.mutations.run(() => this.startInner());
  }
  private async startInner() {
    if (this.server) return this.snapshot();
    this.status = "starting";
    this.error = null;
    try {
      const lan = this.settings.bind === "auto"
        ? await detectLanAddress()
        : null;
      const bind = lan?.address ?? this.settings.bind;
      if (!localIp(bind)) {
        throw new Error(
          "Connect to a private Wi-Fi or Ethernet network to receive photos.",
        );
      }
      const tls = await certificate(this.state, bind);
      this.store = new PhotoStore(
        this.settings.root,
        this.state,
        this.auth.receiverId,
        integerOption("--max-mib", this.values.get("--max-mib"), 64, 1, 256) *
          1024 * 1024,
        integerOption(
          "--timeout-seconds",
          this.values.get("--timeout-seconds"),
          240,
          10,
          600,
        ) * 1000,
      );
      await this.store.init();
      this.store.onCommitted = (receipt) => {
        this.recent = [
          { ...receipt, root: this.settings.root },
          ...this.recent.filter((r) => r.photoId !== receipt.photoId),
        ].slice(0, 30);
        this.saveActivity.run(() =>
          durableJson(join(this.state, "activity.json"), this.recent)
        ).catch(() => {});
      };
      const serve = handler(
        this.auth,
        this.store,
        integerOption(
          "--concurrency",
          this.values.get("--concurrency"),
          2,
          1,
          8,
        ),
        (delta) => {
          this.active += delta;
        },
      );
      let ready = false;
      const { server, port } = listenWithFallback(
        this.settings.port,
        this.values.has("--port"),
        (port) =>
          Deno.serve({
            hostname: bind,
            port,
            cert: tls.cert,
            key: tls.key,
            onListen() {},
          }, (request) =>
            ready
              ? serve(request)
              : new Response("Receiver starting", { status: 503 })),
      );
      this.server = server;
      this.settings.port = port;
      await durableJson(
        join(this.state, "receiver-config.json"),
        this.settings,
      );
      this.address = `https://${
        bind.includes(":") ? `[${bind}]` : bind
      }:${port}`;
      this.fingerprint = tls.fingerprint;
      this.interfaceName = lan?.interfaceName ?? null;
      ready = true;
      this.status = "ready";
      if (!this.auth.list().some((d) => !d.revoked)) await this.pairInner();
      return this.snapshot();
    } catch (e) {
      if (this.server) await this.server.shutdown().catch(() => {});
      this.server = undefined;
      this.status = "error";
      this.error = e instanceof Error ? e.message : "Receiver could not start";
      throw e;
    }
  }
  stop() {
    return this.mutations.run(() => this.stopInner());
  }
  private async stopInner() {
    this.status = "pausing";
    const server = this.server;
    this.server = undefined;
    if (server) await server.shutdown();
    await this.auth.cancelPairing();
    this.pairing = null;
    this.qr = null;
    this.active = 0;
    this.status = "stopped";
    return this.snapshot();
  }
  pair() {
    return this.mutations.run(() => this.pairInner());
  }
  private async pairInner() {
    if (!this.server || !this.address || !this.fingerprint) {
      throw new Error("Start the receiver before pairing a phone.");
    }
    this.pairing = await this.auth.startPairing(this.address, this.fingerprint);
    this.qr = await QRCode.toString(JSON.stringify(this.pairing), {
      type: "svg",
      margin: 1,
      color: { dark: "#162e29", light: "#ffffff" },
    });
    return this.snapshot();
  }
  revoke(id: string) {
    return this.mutations.run(async () => {
      await this.auth.revoke(id);
      return this.snapshot();
    });
  }
  setRoot(root: string) {
    return this.mutations.run(async () => {
      const chosen = resolve(root);
      new PhotoStore(chosen, this.state, this.auth.receiverId);
      const wasRunning = !!this.server;
      await this.stopInner();
      await this.store?.bindLegacyDestinations();
      const old = this.settings;
      try {
        const changed = { ...old, root: chosen };
        await durableJson(join(this.state, "receiver-config.json"), changed);
        this.settings = changed;
        if (wasRunning) await this.startInner();
      } catch (e) {
        this.settings = old;
        await durableJson(join(this.state, "receiver-config.json"), old);
        if (wasRunning) await this.startInner().catch(() => {});
        throw e;
      }
      return this.snapshot();
    });
  }
  async close() {
    try {
      await this.stop();
      await this.saveActivity.run(() => Promise.resolve());
    } finally {
      if (this.lock) {
        await this.lock.unlock();
        this.lock.close();
        this.lock = undefined;
      }
    }
  }
}

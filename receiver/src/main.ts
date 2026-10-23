import { join } from "node:path";
// @ts-types="qrcode-types"
import QRCode from "qrcode";
import { Auth } from "./auth.ts";
import { PhotoStore } from "./store.ts";
import { handler } from "./server.ts";
import { certificate } from "./tls.ts";
import { durableJson, noLinks, readJson } from "./files.ts";
import { stateDirectory } from "./state-directory.ts";
import { listenWithFallback } from "./listen.ts";
import { detectLanAddress, networkAction } from "./lan.ts";
import {
  integerOption,
  pairingNeeded,
  type ReceiverSettings,
  receiverSettings,
} from "./receiver-settings.ts";
import { localIp } from "./validation.ts";

function options(args: string[]) {
  const values = new Map<string, string>();
  const flags = new Set<string>();
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (
      ["--pair", "--list-devices", "--help"].includes(arg)
    ) flags.add(arg);
    else if (
      [
        "--root",
        "--state",
        "--bind",
        "--port",
        "--max-mib",
        "--concurrency",
        "--timeout-seconds",
        "--revoke",
      ].includes(arg) && args[i + 1] && !args[i + 1].startsWith("--")
    ) values.set(arg, args[++i]);
    else throw new Error("Unknown option or missing option value");
  }
  return { values, flags };
}
export async function main(args: string[]) {
  const { values: v, flags: f } = options(args);
  if (f.has("--help")) {
    console.log(
      "Folder Camera receiver\nStart: deno task start — automatically selects your LAN and displays a QR on first use.\n[--root DIR] [--bind auto|PRIVATE-IP] [--state DIR] [--port 8443] [--pair]\nRoot defaults to ~/Captures; destination, port and bind preference are remembered.\nState defaults to $XDG_STATE_HOME/folder-camera or ~/.local/state/folder-camera.\n[--max-mib 64] [--concurrency 2] [--timeout-seconds 240]\nOffline management (stop receiver first): [--state DIR] --list-devices | --revoke DEVICE-ID",
    );
    return;
  }
  const state = stateDirectory(v.get("--state"), {
    xdgStateHome: Deno.env.get("XDG_STATE_HOME"),
    home: Deno.env.get("HOME"),
    platform: Deno.build.os === "windows" ? "win32" : Deno.build.os,
    localAppData: Deno.env.get("LOCALAPPDATA"),
  });
  if (v.has("--root")) new PhotoStore(v.get("--root")!, state, "");
  await Deno.mkdir(state, { recursive: true, mode: 0o700 });
  await noLinks(state);
  await Deno.chmod(state, 0o700);
  const lockPath = join(state, "process.lock");
  try {
    const info = await Deno.lstat(lockPath);
    if (info.isSymlink || !info.isFile) throw new Error("Unsafe state lock");
  } catch (e) {
    if (!(e instanceof Deno.errors.NotFound)) throw e;
  }
  const lock = await Deno.open(lockPath, {
    create: true,
    write: true,
    mode: 0o600,
  });
  if (!await lock.tryLock(true)) {
    lock.close();
    throw new Error(
      "Receiver state is in use; stop the running receiver before offline management.",
    );
  }
  await lock.truncate(0);
  await lock.write(new TextEncoder().encode(String(Deno.pid)));
  try {
    const settingsPath = join(state, "receiver-config.json");
    const management = f.has("--list-devices") || v.has("--revoke");
    const savedSettings = management
      ? null
      : await readJson<ReceiverSettings>(settingsPath);
    const settings = !management ? receiverSettings(v, savedSettings) : null;
    if (settings) {
      new PhotoStore(settings.root, state, "");
      if (!savedSettings && !v.has("--root")) {
        // Older versions never recorded the chosen root. Do not silently change a destination with uploaded photos.
        for (const directory of ["receipts", "journals"]) {
          try {
            for await (const item of Deno.readDir(join(state, directory))) {
              if (item.isFile && item.name.endsWith(".json")) {
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
    }
    const auth = new Auth(state);
    await auth.init();
    if (f.has("--list-devices")) {
      console.log(JSON.stringify(auth.list(), null, 2));
      return;
    }
    if (v.has("--revoke")) {
      await auth.revoke(v.get("--revoke")!);
      console.log("Device revoked");
      return;
    }
    const root = settings!.root;
    // Validate independent roots before creating certificates or configuration.
    new PhotoStore(root, state, auth.receiverId);
    const lan = settings!.bind === "auto" ? await detectLanAddress() : null;
    const bind = lan?.address ?? settings!.bind;
    if (!localIp(bind)) {
      throw new Error(
        "Bind must be a private LAN or loopback IP; wildcard/public bindings are forbidden",
      );
    }
    const max = integerOption("--max-mib", v.get("--max-mib"), 64, 1, 256);
    const concurrency = integerOption(
      "--concurrency",
      v.get("--concurrency"),
      2,
      1,
      8,
    );
    const timeout = integerOption(
      "--timeout-seconds",
      v.get("--timeout-seconds"),
      240,
      10,
      600,
    );
    const tls = await certificate(state, bind);
    const store = new PhotoStore(
      root,
      state,
      auth.receiverId,
      max * 1024 * 1024,
      timeout * 1000,
    );
    await store.init();
    const serve = handler(auth, store, concurrency);
    const formatAddress = (ip: string, port: number) =>
      `https://${ip.includes(":") ? `[${ip}]` : ip}:${port}`;
    // The certificate and receiver identity stay the same on every address.
    const open = async (ip: string) => {
      let ready = false;
      const { server, port } = listenWithFallback(
        settings!.port,
        v.has("--port"),
        (port) =>
          Deno.serve({
            hostname: ip,
            port,
            cert: tls.cert,
            key: tls.key,
            onListen() {},
          }, (request) =>
            ready
              ? serve(request)
              : new Response(JSON.stringify({ version: 1, code: "starting" }), {
                status: 503,
                headers: { "Content-Type": "application/json" },
              })),
      );
      settings!.port = port;
      try {
        await durableJson(settingsPath, settings);
        ready = true;
      } catch (e) {
        await server.shutdown();
        throw e;
      }
      return { server, ip, address: formatAddress(ip, port) };
    };
    const showPairing = async (address: string) => {
      const pairing = await auth.startPairing(address, tls.fingerprint);
      console.log("Temporary pairing payload (expires in five minutes):");
      console.log(JSON.stringify(pairing));
      console.log(
        await QRCode.toString(JSON.stringify(pairing), {
          type: "terminal",
          small: true,
        }),
      );
    };
    let current: Awaited<ReturnType<typeof open>> | undefined = await open(
      bind,
    );
    console.log(
      `Folder Camera v1\nAddress: ${current.address}${
        lan ? ` (${lan.interfaceName}, automatic)` : ""
      }\nDestination: ${root}\nState: ${state}\nReceiver ID: ${auth.receiverId}\nCertificate SHA-256: ${tls.fingerprint}\nPairing: first use displays a QR automatically; --pair creates a new single-use QR.`,
    );
    if (pairingNeeded(auth.list(), f.has("--pair"))) {
      await showPairing(current.address);
    }
    const stopped = Promise.withResolvers<void>();
    let following = false, stopping = false;
    // Automatic binds follow the PC to a new network, e.g. a phone hotspot that hands out a new subnet.
    const follow = async () => {
      if (following || stopping) return;
      following = true;
      try {
        const detected = await detectLanAddress().catch(() => null);
        const action = networkAction(
          current?.ip ?? null,
          detected?.address ?? null,
        );
        if (action === "keep") return;
        const previous = current;
        current = undefined;
        if (previous) {
          let timer: ReturnType<typeof setTimeout> | undefined;
          await Promise.race([
            previous.server.shutdown(),
            new Promise((resolve) => timer = setTimeout(resolve, 5000)),
          ]);
          clearTimeout(timer);
        }
        if (action === "lost") {
          if (previous) {
            console.log("LAN address lost; waiting for Wi-Fi or Ethernet.");
          }
          return;
        }
        current = await open(detected!.address);
        if (stopping) {
          await current.server.shutdown();
          return;
        }
        console.log(
          `Address changed: ${current.address} (${
            detected!.interfaceName
          }, automatic). Paired phones keep the same certificate; update the address on the phone or scan a new pairing QR.`,
        );
        if (pairingNeeded(auth.list(), false)) {
          await showPairing(current.address);
        }
      } catch (e) {
        console.error(
          "Could not move to the new address: " +
            (e instanceof Error ? e.message : String(e)),
        );
      } finally {
        following = false;
      }
    };
    const refresh = settings!.bind === "auto"
      ? setInterval(() => void follow(), 10_000)
      : undefined;
    const shutdown = () => {
      stopping = true;
      clearInterval(refresh);
      current?.server.shutdown().catch(() => {});
      stopped.resolve();
    };
    Deno.addSignalListener("SIGINT", shutdown);
    Deno.addSignalListener("SIGTERM", shutdown);
    try {
      await stopped.promise;
      await current?.server.finished;
    } finally {
      Deno.removeSignalListener("SIGINT", shutdown);
      Deno.removeSignalListener("SIGTERM", shutdown);
      await auth.cancelPairing();
    }
  } finally {
    await lock.unlock();
    lock.close();
  }
}
if (import.meta.main) {
  main(Deno.args).catch((e) => {
    console.error(e instanceof Error ? e.message : "Receiver failed");
    Deno.exit(1);
  });
}

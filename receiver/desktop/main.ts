import { join } from "node:path";
import { homedir } from "node:os";
import { Receiver } from "../src/receiver.ts";
import { stateDirectory } from "../src/state-directory.ts";
import { durableJson, readJson } from "../src/files.ts";
import * as system from "./system.ts";
interface NativeWindow extends EventTarget {
  bind(name: string, handler: (...args: unknown[]) => unknown): void;
  show(): void;
  hide(): void;
  close(): void;
  focus(): void;
  executeJs(code: string): Promise<unknown>;
}
interface NativeTray extends EventTarget {
  setIcon(bytes: Uint8Array): void;
  setTooltip(text: string): void;
  setMenu(items: unknown[]): void;
  destroy(): void;
}
const native = Deno as unknown as {
  BrowserWindow: new (options: Record<string, unknown>) => NativeWindow;
  Tray: new () => NativeTray;
};
const assets = new URL("./ui/", import.meta.url);
const files: Record<string, string> = {
  "/": "text/html",
  "/index.html": "text/html",
  "/app.js": "text/javascript",
  "/app.css": "text/css",
  "/icon.png": "image/png",
  "/licenses.txt": "text/plain; charset=utf-8",
};
const csp =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
// The first server belongs to the GUI. Later Deno.serve listeners retain the LAN address/port.
const uiServer = Deno.serve({ onListen() {} }, async (request) => {
  const path = new URL(request.url).pathname;
  if (request.method !== "GET" || !files[path]) {
    return new Response("Not found", { status: 404 });
  }
  return new Response(
    await Deno.readFile(
      new URL(path === "/" ? "index.html" : path.slice(1), assets),
    ),
    {
      headers: {
        "Content-Type": files[path],
        "Content-Security-Policy": csp,
        "X-Content-Type-Options": "nosniff",
        "Cache-Control": "no-store",
      },
    },
  );
});
const window = new native.BrowserWindow({
  title: "Folder Camera Receiver",
  width: 1100,
  height: 840,
});
const values = new Map<string, string>();
for (let i = 0; i < Deno.args.length; i++) {
  if (
    ["--state", "--root", "--bind", "--port"].includes(Deno.args[i]) &&
    Deno.args[i + 1]
  ) values.set(Deno.args[i], Deno.args[++i]);
  else if (!["--background", "--smoke"].includes(Deno.args[i])) {
    throw new Error("Invalid desktop argument");
  }
}
if (
  Deno.args.includes("--smoke") &&
  (!values.has("--state") || !values.has("--root") ||
    values.get("--bind") !== "127.0.0.1")
) {
  throw new Error(
    "Smoke checks require an explicit isolated state/root and loopback bind",
  );
}
let state = stateDirectory(values.get("--state"), {
  platform: Deno.build.os === "windows" ? "win32" : Deno.build.os,
  home: Deno.env.get("HOME"),
  xdgStateHome: Deno.env.get("XDG_STATE_HOME"),
  localAppData: Deno.env.get("LOCALAPPDATA"),
});
if (Deno.build.os === "darwin" && !values.has("--state")) {
  const legacy = join(homedir(), ".local", "state", "folder-camera");
  if (
    await Deno.stat(join(legacy, "identity.json")).then(() => true).catch(() =>
      false
    )
  ) state = legacy;
}
let receiver = new Receiver(state, values),
  initialized = false,
  startupError: string | null = null;
let preferences = { version: 1, closeToTray: false };
let login = false, tray: NativeTray | undefined;
let activation: Deno.HttpServer | undefined;
let activationToken: string | undefined;
let networkRefresh: ReturnType<typeof setInterval> | undefined = undefined;
const initial = {
  version: 1,
  status: "starting",
  root: null,
  endpoint: null,
  receiverId: null,
  fingerprint: null,
  active: 0,
  devices: [],
  recent: [],
  pairing: null,
  qr: null,
};
function snapshot() {
  return {
    ...(initialized ? receiver.snapshot() : {
      ...initial,
      status: startupError ? "error" : "starting",
      error: startupError,
    }),
    initialized,
    autostart: login,
    closeToTray: preferences.closeToTray,
    tray: !!tray,
  };
}
async function activateExisting() {
  for (let attempt = 0; attempt < 8; attempt++) {
    try {
      const previous = await readJson<{ port: number; token: string }>(
        join(state, "desktop-instance.json"),
      );
      if (
        previous && Number.isInteger(previous.port) && previous.port > 0 &&
        previous.port <= 65535 && /^[0-9a-f]{64}$/.test(previous.token)
      ) {
        const response = await fetch(
          `http://127.0.0.1:${previous.port}/activate`,
          {
            method: "POST",
            headers: { Authorization: "Bearer " + previous.token },
            signal: AbortSignal.timeout(800),
          },
        );
        if (response.ok) return true;
      }
    } catch {
      /* A CLI-only profile or a starting GUI has no activation listener yet. */
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  return false;
}
async function boot() {
  try {
    await receiver.init();
    initialized = true;
    preferences = await readJson<typeof preferences>(
      join(state, "desktop-settings.json"),
    ) ?? preferences;
    login = await system.autostartEnabled();
    await receiver.start().catch(() => {});
    if (!activation) {
      activationToken = [...crypto.getRandomValues(new Uint8Array(32))].map(
        (n) => n.toString(16).padStart(2, "0"),
      ).join("");
      activation = Deno.serve(
        { hostname: "127.0.0.1", port: 0, onListen() {} },
        (request) => {
          if (
            request.method !== "POST" ||
            new URL(request.url).pathname !== "/activate" ||
            request.headers.has("Origin") ||
            request.headers.get("Authorization") !== "Bearer " + activationToken
          ) return new Response("Forbidden", { status: 403 });
          window.show();
          window.focus();
          return new Response(null, { status: 204 });
        },
      );
      await durableJson(join(state, "desktop-instance.json"), {
        port: (activation.addr as Deno.NetAddr).port,
        token: activationToken,
      });
    }
  } catch (e) {
    startupError = e instanceof Error ? e.message : "Receiver could not start";
    await receiver.close().catch(() => {});
    if (startupError.includes("in use") && await activateExisting()) {
      await quit();
      return;
    }
  }
}
window.bind("snapshot", snapshot);
window.bind("start", async () => {
  if (!initialized) throw new Error(startupError ?? "Receiver is starting");
  await receiver.start();
  return snapshot();
});
window.bind("stop", async () => {
  await receiver.stop();
  return snapshot();
});
window.bind("pair", async () => {
  await receiver.pair();
  return snapshot();
});
window.bind("revoke", async (id) => {
  if (typeof id !== "string" || !/^[0-9a-f-]{36}$/.test(id)) {
    throw new Error("Invalid phone");
  }
  await receiver.revoke(id);
  return snapshot();
});
window.bind("browseFolders", (path) => {
  if (path !== undefined && typeof path !== "string") {
    throw new Error("Invalid folder");
  }
  return system.browseFolders(path as string | undefined);
});
async function setRoot(root: unknown) {
  if (typeof root !== "string" || root.length > 4096) {
    throw new Error("Invalid capture folder");
  }
  if (!initialized) {
    values.set("--root", root);
    receiver = new Receiver(state, values);
    startupError = null;
    await boot();
    if (!initialized) {
      throw new Error(startupError ?? "Receiver could not start");
    }
  } else await receiver.setRoot(root);
  return snapshot();
}
window.bind("setRoot", setRoot);
window.bind("chooseFolder", async () => {
  const choice = await system.chooseFolder(
    initialized ? receiver.settings.root : homedir(),
  );
  if (choice.path) return await setRoot(choice.path);
  return choice.fallback ? { fallback: true } : null;
});
window.bind("openFolder", async () => {
  if (!initialized) throw new Error("Choose a folder first");
  await system.openFolder(receiver.settings.root);
});
window.bind("setAutostart", async (enabled) => {
  if (typeof enabled !== "boolean") throw new Error("Invalid startup setting");
  await system.setAutostart(enabled);
  login = await system.autostartEnabled();
  return snapshot();
});
window.bind("setCloseToTray", async (enabled) => {
  if (typeof enabled !== "boolean" || !tray || !initialized) {
    throw new Error("Tray is unavailable");
  }
  preferences.closeToTray = enabled;
  await durableJson(join(state, "desktop-settings.json"), preferences);
  return snapshot();
});
let quitting = false;
async function quit(code = 0) {
  if (quitting) return;
  quitting = true;
  const timeout = setTimeout(() => Deno.exit(code), 5000);
  try {
    await receiver.close();
  } finally {
    clearTimeout(timeout);
    if (activation) {
      await activation.shutdown().catch(() => {});
      await Deno.remove(join(state, "desktop-instance.json")).catch(() => {});
    }
    clearInterval(networkRefresh);
    await uiServer.shutdown().catch(() => {});
    tray?.destroy();
    window.close();
    if (Deno.build.os === "darwin") Deno.exitCode = code;
    else Deno.exit(code);
  }
}
window.bind("quit", () => quit());
try {
  tray = new native.Tray();
  tray.setIcon(await Deno.readFile(new URL("icon.png", assets)));
  tray.setTooltip("Folder Camera Receiver");
  tray.setMenu([
    { item: { label: "Open receiver", id: "open", enabled: true } },
    { item: { label: "Quit receiver", id: "quit", enabled: true } },
  ]);
  tray.addEventListener("menuclick", (event) => {
    const id = (event as CustomEvent<{ id: string }>).detail.id;
    if (id === "open") {
      window.show();
      window.focus();
    }
    if (id === "quit") void quit();
  });
  tray.addEventListener("click", () => {
    window.show();
    window.focus();
  });
} catch {
  tray?.destroy();
  tray = undefined;
}
window.addEventListener("close", (event) => {
  if (quitting) return;
  event.preventDefault();
  if (preferences.closeToTray && tray && !quitting) window.hide();
  else void quit();
});
if (Deno.args.includes("--background") && tray) window.hide();
await boot();
if (!quitting) {
  networkRefresh = setInterval(() => {
    if (initialized) void receiver.refreshNetwork();
  }, 5000);
}
if (!quitting && Deno.args.includes("--smoke")) {
  if (!initialized || receiver.snapshot().status !== "ready") {
    throw new Error(
      startupError ?? receiver.snapshot().error ?? "Desktop receiver not ready",
    );
  }
  let rendered = false;
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const value = await window.executeJs(
        "document.getElementById('status-title')?.textContent",
      );
      const actual = value && typeof value === "object" && "ok" in value &&
          "value" in value && value.ok === true
        ? value.value
        : value;
      rendered = actual === "Ready to receive";
    } catch { /* Wait for the bundled document. */ }
    if (rendered) break;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  if (!rendered) {
    console.error(
      "DESKTOP SMOKE FAIL: native receiver UI did not render the ready state",
    );
    await quit(1);
  }
  console.log(
    "DESKTOP SMOKE PASS: native window, bindings, independent TLS listener and persistent receiver initialized",
  );
  setTimeout(() => void quit(), 1500);
}

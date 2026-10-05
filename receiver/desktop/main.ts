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
  focus(): void;
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
};
const csp =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
// The first server belongs to the GUI. Later Deno.serve listeners retain the LAN address/port.
Deno.serve({ onListen() {} }, async (request) => {
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
async function boot() {
  try {
    await receiver.init();
    initialized = true;
    preferences = await readJson<typeof preferences>(
      join(state, "desktop-settings.json"),
    ) ?? preferences;
    login = await system.autostartEnabled();
    await receiver.start().catch(() => {});
  } catch (e) {
    startupError = e instanceof Error ? e.message : "Receiver could not start";
    await receiver.close().catch(() => {});
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
async function quit() {
  if (quitting) return;
  quitting = true;
  const timeout = setTimeout(() => Deno.exit(0), 5000);
  try {
    await receiver.close();
  } finally {
    clearTimeout(timeout);
    tray?.destroy();
    Deno.exit(0);
  }
}
window.bind("quit", quit);
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
  event.preventDefault();
  if (preferences.closeToTray && tray && !quitting) window.hide();
  else void quit();
});
if (Deno.args.includes("--background") && tray) window.hide();
await boot();
if (Deno.args.includes("--smoke")) {
  if (!initialized || receiver.snapshot().status !== "ready") {
    throw new Error(
      startupError ?? receiver.snapshot().error ?? "Desktop receiver not ready",
    );
  }
  console.log(
    "DESKTOP SMOKE PASS: native window, bindings, independent TLS listener and persistent receiver initialized",
  );
  setTimeout(() => void quit(), 1500);
}

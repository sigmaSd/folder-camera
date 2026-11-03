import { join, parse, relative, resolve, sep, win32 } from "node:path";
import { chmod, link, rename, unlink } from "node:fs/promises";
import { codedError } from "./runtime.ts";
export const windows = process.platform === "win32";
export function independentRoots(root: string, state: string): boolean {
  const contains = (parent: string, child: string) => {
    const path = relative(parent, child);
    return path === "" ||
      path !== ".." && !path.startsWith(".." + sep) && !parse(path).root;
  };
  return root !== parse(root).root && state !== parse(state).root &&
    !contains(root, state) && !contains(state, root);
}
export function pathIdentity(path: string): string {
  const prefix = String.fromCharCode(92, 92, 63, 92);
  if (path.startsWith(prefix + "UNC\\")) path = "\\\\" + path.slice(8);
  else if (path.startsWith(prefix)) path = path.slice(4);
  const canonical = resolve(path).normalize("NFC");
  return windows ? canonical.toLowerCase() : canonical;
}
export async function privateMode(path: string, mode: number) {
  if (!windows) await chmod(path, mode);
}
interface Kernel32 {
  symbols: {
    MoveFileExW(from: Uint8Array, to: Uint8Array, flags: number): number;
    GetLastError(): number;
  };
}
type DlOpen = (path: string, symbols: Record<string, unknown>) => Kernel32;
/** Deno's FFI, where the receiver runs on Deno (Node has none: it publishes with fs). */
const dlopen = (globalThis as unknown as { Deno?: { dlopen?: DlOpen } }).Deno
  ?.dlopen;
function windowsLibrary(open: DlOpen) {
  const system = process.env.SystemRoot;
  if (!system || !parse(system).root) {
    throw new Error("Windows system directory is unavailable");
  }
  return open(join(system, "System32", "kernel32.dll"), {
    MoveFileExW: { parameters: ["buffer", "buffer", "u32"], result: "i32" },
    GetLastError: { parameters: [], result: "u32" },
  });
}
let kernel: Kernel32 | undefined;
function wide(value: string) {
  value = win32.toNamespacedPath(value);
  const data = new Uint16Array(value.length + 1);
  for (let i = 0; i < value.length; i++) data[i] = value.charCodeAt(i);
  return new Uint8Array(data.buffer);
}
/**
 * Windows uses no-replace/write-through moves (on Node: a hard link, then the source removed); POSIX publication
 * uses hard links. An occupied target fails with EEXIST.
 */
export async function publishFile(
  source: string,
  target: string,
  replace = false,
) {
  if (!windows || !dlopen) {
    if (replace) await rename(source, target);
    else {
      await link(source, target);
      if (windows) await unlink(source);
    }
    return;
  }
  kernel ??= windowsLibrary(dlopen);
  if (
    !kernel.symbols.MoveFileExW(
      wide(source),
      wide(target),
      0x8 | (replace ? 0x1 : 0),
    )
  ) {
    const error = kernel.symbols.GetLastError();
    if (error === 80 || error === 183) {
      throw codedError("Destination exists", "EEXIST");
    }
    if (error === 112) throw new Error("disk full");
    throw new Error(`Windows file publication failed (${error})`);
  }
}

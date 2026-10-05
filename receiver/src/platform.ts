import { join, parse, relative, resolve, sep, win32 } from "node:path";
export const windows = Deno.build.os === "windows";
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
  if (!windows) await Deno.chmod(path, mode);
}
function windowsLibrary() {
  const system = Deno.env.get("SystemRoot");
  if (!system || !parse(system).root) {
    throw new Error("Windows system directory is unavailable");
  }
  return Deno.dlopen(join(system, "System32", "kernel32.dll"), {
    MoveFileExW: { parameters: ["buffer", "buffer", "u32"], result: "i32" },
    GetLastError: { parameters: [], result: "u32" },
  });
}
let kernel: ReturnType<typeof windowsLibrary> | undefined;
function wide(value: string) {
  value = win32.toNamespacedPath(value);
  const data = new Uint16Array(value.length + 1);
  for (let i = 0; i < value.length; i++) data[i] = value.charCodeAt(i);
  return new Uint8Array(data.buffer);
}
/** Windows uses no-replace/write-through moves; POSIX publication uses hard links. */
export async function publishFile(
  source: string,
  target: string,
  replace = false,
) {
  if (!windows) {
    if (replace) await Deno.rename(source, target);
    else await Deno.link(source, target);
    return;
  }
  kernel ??= windowsLibrary();
  if (
    !kernel.symbols.MoveFileExW(
      wide(source),
      wide(target),
      0x8 | (replace ? 0x1 : 0),
    )
  ) {
    const error = kernel.symbols.GetLastError();
    if (error === 80 || error === 183) {
      throw new Deno.errors.AlreadyExists("Destination exists");
    }
    if (error === 112) throw new Error("disk full");
    throw new Error(`Windows file publication failed (${error})`);
  }
}

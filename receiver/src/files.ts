import { dirname, join, parse, resolve } from "node:path";
import { createHash } from "node:crypto";
import {
  lstat,
  mkdir,
  open,
  readdir,
  readFile,
  realpath,
  rm,
} from "node:fs/promises";
import { fail } from "./validation.ts";
import { pathIdentity, publishFile, windows } from "./platform.ts";
import { isNotFound } from "./runtime.ts";

export async function noLinks(path: string): Promise<void> {
  const absolute = resolve(path);
  let current = parse(absolute).root;
  for (
    const part of absolute.slice(current.length).split(/[\\/]/).filter(Boolean)
  ) {
    current = join(current, part);
    const info = await lstat(current);
    if (info.isSymbolicLink() || !info.isDirectory()) fail("unsafe_root", 409);
  }
  if (pathIdentity(await realpath(absolute)) !== pathIdentity(absolute)) {
    fail("unsafe_root", 409);
  }
}
export async function syncDirectory(path: string): Promise<void> {
  // Windows uses write-through publication; directory fsync is unsupported.
  if (windows) return;
  const file = await open(path, "r");
  try {
    await file.sync();
  } finally {
    await file.close();
  }
}
/** Write `data` to a new file (never an existing one), flushed to disk. */
export async function writeNewFile(
  path: string,
  data: Uint8Array,
  mode = 0o600,
): Promise<void> {
  const file = await open(path, "wx", mode);
  try {
    let at = 0;
    while (at < data.length) {
      at += (await file.write(data, at, data.length - at)).bytesWritten;
    }
    await file.sync();
  } finally {
    await file.close();
  }
}
export async function durableJson(path: string, value: unknown): Promise<void> {
  const tmp = `${path}.${crypto.randomUUID()}.tmp`;
  await writeNewFile(tmp, new TextEncoder().encode(JSON.stringify(value)));
  try {
    await publishFile(tmp, path, true);
    await syncDirectory(dirname(path));
  } catch (e) {
    await rm(tmp, { force: true }).catch(() => {});
    throw e;
  }
}
export async function readJson<T>(path: string): Promise<T | null> {
  try {
    const info = await lstat(path);
    if (info.isSymbolicLink() || !info.isFile() || info.size > 1024 * 1024) {
      fail("unsafe_state", 500);
    }
    return JSON.parse(await readFile(path, "utf8"));
  } catch (e) {
    if (isNotFound(e)) return null;
    throw e;
  }
}
export async function hashFile(
  path: string,
  expectedSize?: number,
): Promise<{ size: number; hash: string }> {
  const info = await lstat(path);
  if (
    !info.isFile() || info.isSymbolicLink() ||
    expectedSize !== undefined && info.size !== expectedSize
  ) fail("conflict", 409);
  const hash = createHash("sha256");
  let size = 0;
  const file = await open(path, "r");
  try {
    for await (const data of file.createReadStream({ autoClose: false })) {
      hash.update(data as Uint8Array);
      size += (data as Uint8Array).length;
    }
  } finally {
    await file.close().catch(() => {});
  }
  return { size, hash: hash.digest("hex") };
}
/** The names in a directory. */
export async function names(path: string): Promise<string[]> {
  return await readdir(path);
}
export async function portableDirectory(
  root: string,
  segments: string[],
  create: boolean,
): Promise<string> {
  await noLinks(root);
  let parent = root;
  for (const name of segments) {
    await noLinks(parent);
    const aliases = [];
    for (const entry of await names(parent)) {
      if (
        entry.normalize("NFC").toLowerCase() ===
          name.normalize("NFC").toLowerCase()
      ) aliases.push(entry);
    }
    if (
      aliases.length > 1 ||
      aliases.length === 1 &&
        (process.platform === "darwin"
          ? aliases[0].normalize("NFC") !== name.normalize("NFC")
          : aliases[0] !== name)
    ) {
      fail("conflict", 409);
    }
    const child = join(parent, name);
    if (!aliases.length && create) {
      await mkdir(child);
      await syncDirectory(parent);
    }
    await noLinks(child);
    parent = child;
  }
  return parent;
}
export async function filenameFree(
  parent: string,
  name: string,
): Promise<void> {
  await noLinks(parent);
  for (const entry of await names(parent)) {
    if (
      entry.normalize("NFC").toLowerCase() ===
        name.normalize("NFC").toLowerCase()
    ) fail("conflict", 409);
  }
}
export class Serial {
  private tail: Promise<unknown> = Promise.resolve();
  run<T>(fn: () => Promise<T>): Promise<T> {
    const result = this.tail.then(fn);
    this.tail = result.catch(() => {});
    return result;
  }
}

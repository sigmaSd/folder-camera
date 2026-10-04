import { dirname, join, parse, resolve } from "node:path";
import { createHash } from "node:crypto";
import { fail } from "./validation.ts";

export async function noLinks(path: string): Promise<void> {
  const absolute = resolve(path);
  let current = parse(absolute).root;
  for (
    const part of absolute.slice(current.length).split(/[\\/]/).filter(Boolean)
  ) {
    current = join(current, part);
    const info = await Deno.lstat(current);
    if (info.isSymlink || !info.isDirectory) fail("unsafe_root", 409);
  }
  if (await Deno.realPath(absolute) !== absolute) fail("unsafe_root", 409);
}
export async function syncDirectory(path: string): Promise<void> {
  const file = await Deno.open(path, { read: true });
  try {
    await file.sync();
  } finally {
    file.close();
  }
}
export async function durableJson(path: string, value: unknown): Promise<void> {
  const tmp = `${path}.${crypto.randomUUID()}.tmp`;
  const file = await Deno.open(tmp, {
    createNew: true,
    write: true,
    mode: 0o600,
  });
  try {
    const data = new TextEncoder().encode(JSON.stringify(value));
    let at = 0;
    while (at < data.length) at += await file.write(data.subarray(at));
    await file.sync();
  } finally {
    file.close();
  }
  try {
    await Deno.rename(tmp, path);
    await syncDirectory(dirname(path));
  } catch (e) {
    await Deno.remove(tmp).catch(() => {});
    throw e;
  }
}
export async function readJson<T>(path: string): Promise<T | null> {
  try {
    const info = await Deno.lstat(path);
    if (info.isSymlink || !info.isFile || info.size > 1024 * 1024) {
      fail("unsafe_state", 500);
    }
    return JSON.parse(await Deno.readTextFile(path));
  } catch (e) {
    if (e instanceof Deno.errors.NotFound) return null;
    throw e;
  }
}
export async function hashFile(
  path: string,
  expectedSize?: number,
): Promise<{ size: number; hash: string }> {
  const info = await Deno.lstat(path);
  if (
    !info.isFile || info.isSymlink ||
    expectedSize !== undefined && info.size !== expectedSize
  ) fail("conflict", 409);
  const hash = createHash("sha256");
  let size = 0;
  const file = await Deno.open(path, { read: true });
  try {
    for await (const data of file.readable) {
      hash.update(data);
      size += data.length;
    }
  } catch (e) {
    try {
      file.close();
    } catch { /* stream may have closed it */ }
    throw e;
  }
  return { size, hash: hash.digest("hex") };
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
    for await (const entry of Deno.readDir(parent)) {
      if (
        entry.name.normalize("NFC").toLowerCase() ===
          name.normalize("NFC").toLowerCase()
      ) aliases.push(entry.name);
    }
    if (aliases.length > 1 || aliases.length === 1 && aliases[0] !== name) {
      fail("conflict", 409);
    }
    const child = join(parent, name);
    if (!aliases.length && create) {
      await Deno.mkdir(child);
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
  for await (const entry of Deno.readDir(parent)) {
    if (
      entry.name.normalize("NFC").toLowerCase() ===
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

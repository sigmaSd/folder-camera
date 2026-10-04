import { isIP } from "node:net";

export class HttpError extends Error {
  constructor(public code: string, public status: number) {
    super(code);
  }
}
export function fail(code: string, status = 400): never {
  throw new HttpError(code, status);
}
const bytes = (s: string) => new TextEncoder().encode(s).length;
export function segment(s: string): void {
  if (
    !s || s === ".folder-camera-partials" || s === "." || s === ".." ||
    bytes(s) > 120 ||
    [...s].some((c) =>
      c.codePointAt(0)! < 32 ||
      (c.codePointAt(0)! >= 127 && c.codePointAt(0)! <= 159)
    ) || /[\\/:%*?"<>|]/u.test(s) ||
    /[. ]$/.test(s) ||
    /^(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(?:\..*)?$/i.test(s) ||
    /[\uD800-\uDFFF]/u.test(s)
  ) fail("invalid_path");
}
export function relativePath(s: unknown): asserts s is string {
  if (typeof s !== "string" || bytes(s) > 1024 || s.split("/").length > 32) {
    fail("invalid_path");
  }
  for (const part of s.split("/")) segment(part);
}
export function uuid(s: unknown): asserts s is string {
  if (
    typeof s !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
      .test(s)
  ) fail("invalid_metadata");
}
export interface Metadata {
  version: 1;
  photoId: string;
  relativePath: string;
  filename: string;
  mimeType: "image/jpeg";
  byteSize: number;
  sha256: string;
}
export interface Receipt extends Metadata {
  receiverId: string;
  receivedAt: number;
}
export function metadata(value: unknown, maxSize: number): Metadata {
  if (!value || typeof value !== "object") fail("invalid_metadata");
  const m = value as Metadata;
  uuid(m.photoId);
  relativePath(m.relativePath);
  if (typeof m.filename !== "string") fail("invalid_metadata");
  segment(m.filename);
  if (
    m.version !== 1 || !m.filename.endsWith(".jpg") ||
    m.mimeType !== "image/jpeg" || !Number.isSafeInteger(m.byteSize) ||
    m.byteSize < 4 || typeof m.sha256 !== "string" ||
    !/^[0-9a-f]{64}$/.test(m.sha256)
  ) fail("invalid_metadata");
  if (m.byteSize > maxSize) fail("excessive_size", 413);
  return {
    version: 1,
    photoId: m.photoId,
    relativePath: m.relativePath,
    filename: m.filename,
    mimeType: "image/jpeg",
    byteSize: m.byteSize,
    sha256: m.sha256,
  };
}
export function decodeMetadata(
  header: string | null,
  maxSize: number,
): Metadata {
  if (!header || header.length > 8192 || !/^[A-Za-z0-9_-]+$/.test(header)) {
    fail("invalid_metadata");
  }
  try {
    const decoded = Uint8Array.from(
      atob(header.replaceAll("-", "+").replaceAll("_", "/")),
      (c) => c.charCodeAt(0),
    );
    return metadata(
      JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(decoded)),
      maxSize,
    );
  } catch (e) {
    if (e instanceof HttpError) throw e;
    return fail("invalid_metadata");
  }
}
export function localIp(address: string): boolean {
  if (!isIP(address)) return false;
  if (address.includes(":")) {
    return address === "::1" || /^f[cd][0-9a-f]{2}:/i.test(address);
  }
  const [a, b] = address.split(".").map(Number);
  return a === 10 || a === 127 || a === 192 && b === 168 ||
    a === 172 && b >= 16 && b <= 31;
}
export function endpoint(address: string): string {
  if (address.length > 256) fail("invalid_endpoint");
  let u: URL;
  try {
    u = new URL(address);
  } catch {
    return fail("invalid_endpoint");
  }
  const host = u.hostname.replace(/^\[|\]$/g, "");
  if (
    u.protocol !== "https:" || u.username || u.password || u.pathname !== "/" ||
    u.search || u.hash || !localIp(host)
  ) fail("invalid_endpoint");
  return u.origin;
}
export const same = (a: Metadata, b: Metadata) =>
  a.photoId === b.photoId && a.relativePath === b.relativePath &&
  a.filename === b.filename && a.sha256 === b.sha256 &&
  a.byteSize === b.byteSize && a.mimeType === b.mimeType;

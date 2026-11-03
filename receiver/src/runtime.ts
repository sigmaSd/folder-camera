// What differs between Deno and Node for the receiver engine: the HTTPS listener, the state lock and error kinds.
// Under Deno (CLI, desktop app) the receiver keeps Deno.serve and flock exactly as before; under Node (an app that
// embeds the engine, such as MedStation) it uses node:https and a pid lock file. Everything else is node:* APIs,
// which both runtimes provide.

import { createServer, type Server } from "node:https";
import type { IncomingMessage, ServerResponse } from "node:http";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import type { ReadableStream as NodeReadableStream } from "node:stream/web";

interface DenoFile {
  tryLock(exclusive?: boolean): Promise<boolean>;
  unlock(): Promise<void>;
  truncate(len?: number): Promise<void>;
  write(data: Uint8Array): Promise<number>;
  close(): void;
}
interface DenoServer {
  shutdown(): Promise<void>;
  addr: { port: number };
}
/** The few Deno APIs the engine still uses when it runs on Deno. */
interface DenoLike {
  pid: number;
  open(path: string, options: Record<string, unknown>): Promise<DenoFile>;
  serve(
    options: Record<string, unknown>,
    handler: (request: Request) => Response | Promise<Response>,
  ): DenoServer;
  errors: Record<string, new (...args: never[]) => Error>;
}
export const deno = (globalThis as { Deno?: DenoLike }).Deno;

const code = (e: unknown) => (e as { code?: unknown } | null)?.code;
const denoError = (e: unknown, name: string) =>
  !!deno?.errors?.[name] && e instanceof deno.errors[name];
export const isNotFound = (e: unknown) =>
  code(e) === "ENOENT" || denoError(e, "NotFound");
export const isAlreadyExists = (e: unknown) =>
  code(e) === "EEXIST" || denoError(e, "AlreadyExists");
export const isAddrInUse = (e: unknown) =>
  code(e) === "EADDRINUSE" || denoError(e, "AddrInUse");

/** An error with a Node-style code, recognized by the checks above. */
export function codedError(message: string, errorCode: string): Error {
  return Object.assign(new Error(message), { code: errorCode });
}

// ---- the HTTPS listener ----

export interface TlsListener {
  port: number;
  shutdown(): Promise<void>;
}
type Handler = (request: Request) => Promise<Response>;

/** HTTPS on `hostname:port` (port 0: any free one); rejects with an AddrInUse error when the port is taken. */
export function serveTls(
  options: { hostname: string; port: number; cert: string; key: string },
  handler: Handler,
): Promise<TlsListener> {
  if (deno) {
    try {
      const server = deno.serve({ ...options, onListen() {} }, handler);
      return Promise.resolve({
        port: server.addr.port,
        shutdown: () => server.shutdown(),
      });
    } catch (e) {
      return Promise.reject(e);
    }
  }
  return serveNode(options, handler);
}

function serveNode(
  options: { hostname: string; port: number; cert: string; key: string },
  handler: Handler,
): Promise<TlsListener> {
  const server: Server = createServer(
    { cert: options.cert, key: options.key },
    (req, res) => void answer(req, res, handler, options.hostname),
  );
  server.on("tlsClientError", () => {}); // a phone that gave up mid-handshake
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(options.port, options.hostname, () => {
      server.off("error", reject);
      server.on("error", () => {});
      const address = server.address();
      resolve({
        port: typeof address === "object" && address
          ? address.port
          : options.port,
        shutdown: () => closeNode(server),
      });
    });
  });
}

/** Stop accepting at once; let transfers finish, cutting what is left after 5 s. */
function closeNode(server: Server): Promise<void> {
  return new Promise((resolve) => {
    const cut = setTimeout(() => server.closeAllConnections(), 5000);
    server.close(() => {
      clearTimeout(cut);
      resolve();
    });
    server.closeIdleConnections();
  });
}

async function answer(
  req: IncomingMessage,
  res: ServerResponse,
  handler: Handler,
  hostname: string,
) {
  try {
    const headers = new Headers();
    for (const [name, value] of Object.entries(req.headers)) {
      if (Array.isArray(value)) {
        for (const v of value) headers.append(name, v);
      } else if (value !== undefined) headers.set(name, value);
    }
    const host = hostname.includes(":") ? `[${hostname}]` : hostname;
    const body = req.method === "GET" || req.method === "HEAD"
      ? undefined
      : (Readable.toWeb(req) as unknown as ReadableStream<Uint8Array>);
    const request = new Request(`https://${host}${req.url ?? "/"}`, {
      method: req.method,
      headers,
      body,
      duplex: "half",
    } as RequestInit);
    const response = await handler(request);
    res.writeHead(response.status, Object.fromEntries(response.headers));
    if (response.body) {
      await pipeline(
        Readable.fromWeb(response.body as unknown as NodeReadableStream),
        res,
      );
    } else res.end();
  } catch {
    if (!res.headersSent) res.writeHead(500).end();
    else res.destroy();
  }
}

// ---- the state folder's lock ----

export interface StateLock {
  release(): Promise<void>;
}

/** The state folder for this process alone; null when another receiver holds it. */
export async function lockState(path: string): Promise<StateLock | null> {
  if (deno) {
    const file = await deno.open(path, {
      create: true,
      write: true,
      mode: 0o600,
    });
    if (!await file.tryLock(true)) {
      file.close();
      return null;
    }
    await file.truncate(0);
    await file.write(new TextEncoder().encode(String(deno.pid)));
    return {
      async release() {
        await file.unlock();
        file.close();
      },
    };
  }
  // Node has no flock: the pid of the receiver holding it, free when that process is gone — and, within this
  // process, the state folders already held
  const key = resolve(path);
  if (held.has(key)) return null;
  const holder = Number((await readFile(path, "utf8").catch(() => "")).trim());
  if (holder && holder !== process.pid && alive(holder)) return null;
  if (held.has(key)) return null; // taken while the file was read
  held.add(key);
  await writeFile(path, String(process.pid), { mode: 0o600 });
  return {
    async release() {
      const now = Number((await readFile(path, "utf8").catch(() => "")).trim());
      if (now === process.pid) await writeFile(path, "", { mode: 0o600 });
      held.delete(key);
    },
  };
}
const held = new Set<string>();

function alive(pid: number) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return code(e) === "EPERM";
  }
}

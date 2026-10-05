import type { Auth } from "./auth.ts";
import type { PhotoStore } from "./store.ts";
import { decodeMetadata, fail, HttpError, uuid } from "./validation.ts";

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
async function boundedJson(request: Request): Promise<unknown> {
  if (
    request.headers.get("content-type")?.split(";")[0] !== "application/json"
  ) fail("invalid_metadata");
  const reader = request.body?.getReader();
  if (!reader) fail("invalid_metadata");
  let size = 0;
  const parts: Uint8Array[] = [];
  const deadline = Date.now() + 10_000;
  try {
    while (true) {
      let timer: ReturnType<typeof setTimeout> | undefined;
      const result = await Promise.race([
        reader.read(),
        new Promise<never>((_, reject) => {
          timer = setTimeout(
            () => reject(new HttpError("request_timeout", 408)),
            Math.max(1, deadline - Date.now()),
          );
        }),
      ]).finally(() => clearTimeout(timer));
      if (result.done) break;
      size += result.value.length;
      if (size > 4096) fail("excessive_size", 413);
      parts.push(result.value);
    }
    const bytes = new Uint8Array(size);
    let at = 0;
    for (const part of parts) {
      bytes.set(part, at);
      at += part.length;
    }
    try {
      return JSON.parse(
        new TextDecoder("utf-8", { fatal: true }).decode(bytes),
      );
    } catch {
      return fail("invalid_metadata");
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
export function handler(
  auth: Auth,
  store: PhotoStore,
  maxConcurrency = 2,
  activity?: (delta: number) => void,
) {
  let active = 0;
  let requests = 0;
  return async (request: Request): Promise<Response> => {
    requests++;
    if (requests > 32) {
      requests--;
      await request.body?.cancel();
      return json({ version: 1, code: "busy" }, 429);
    }
    try {
      // There is no web management UI. Reject browser-origin requests to reduce hostile-site probing.
      if (request.headers.has("Origin")) fail("forbidden_origin", 403);
      const path = new URL(request.url).pathname;
      if (request.method === "POST" && path === "/v1/pair") {
        return json(await auth.pair(await boundedJson(request)));
      }
      auth.authenticate(request.headers.get("Authorization"));
      if (request.method === "GET" && path === "/v1/health") {
        return json({ version: 1, receiverId: auth.receiverId });
      }
      const match = /^\/v1\/photos\/([0-9a-f-]{36})$/.exec(path);
      if (!match) fail("not_found", 404);
      const id = match[1];
      uuid(id);
      if (request.method === "GET") {
        const receipt = await store.receipt(id);
        if (!receipt) fail("not_found", 404);
        return json(receipt);
      }
      if (request.method !== "PUT") fail("not_found", 404);
      const meta = decodeMetadata(
        request.headers.get("X-FolderCamera-Metadata"),
        store.maxSize,
      );
      if (
        meta.photoId !== id ||
        request.headers.get("Content-Type")?.split(";")[0] !== "image/jpeg"
      ) fail("invalid_metadata");
      const length = request.headers.get("Content-Length");
      if (length !== null && length !== String(meta.byteSize)) {
        fail("invalid_metadata");
      }
      if (active >= maxConcurrency) fail("busy", 429);
      active++;
      activity?.(1);
      try {
        return json(await store.put(meta, request.body));
      } finally {
        active--;
        activity?.(-1);
      }
    } catch (e) {
      const error = e instanceof HttpError
        ? e
        : new HttpError("transient_failure", 503);
      // Stable categories only: never request headers, tokens, secrets, or destination paths.
      console.error(`request_failed code=${error.code}`);
      await request.body?.cancel().catch(() => {});
      return json({ version: 1, code: error.code }, error.status);
    } finally {
      requests--;
    }
  };
}

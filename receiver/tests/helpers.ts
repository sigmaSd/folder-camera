import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { Auth } from "../src/auth.ts";
import { PhotoStore } from "../src/store.ts";
import type { Metadata } from "../src/validation.ts";
import { strict as assert } from "node:assert";
export { assert };
// A generated development-only 16×16 JPEG; real camera behavior still needs a device.
export const jpeg = Deno.readFileSync(
  new URL("./fixtures/photo.jpg", import.meta.url),
);
export function meta(overrides: Partial<Metadata> = {}): Metadata {
  return {
    version: 1,
    photoId: crypto.randomUUID(),
    relativePath: "Été/صور/Job A",
    filename: "20261004_test.jpg",
    mimeType: "image/jpeg",
    byteSize: jpeg.length,
    sha256: createHash("sha256").update(jpeg).digest("hex"),
    ...overrides,
  };
}
export const stream = (data = jpeg) =>
  new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(data);
      controller.close();
    },
  });
export async function setup() {
  const dir = fileURLToPath(new URL("../../.work/tests/", import.meta.url));
  await Deno.mkdir(dir, { recursive: true });
  const temp = await Deno.makeTempDir({ dir, prefix: "receiver-" });
  const root = join(temp, "photos");
  const state = join(temp, "state");
  await Deno.mkdir(state, { mode: 0o700 });
  const auth = new Auth(state);
  await auth.init();
  const store = new PhotoStore(root, state, auth.receiverId);
  await store.init();
  return {
    temp,
    root,
    state,
    auth,
    store,
    async cleanup() {
      await Deno.remove(temp, { recursive: true });
    },
  };
}
export async function rejected(fn: () => Promise<unknown>, code: string) {
  await assert.rejects(
    fn,
    (e: unknown) => e instanceof Error && e.message === code,
  );
}

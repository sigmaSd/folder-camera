import { join } from "node:path";
import { durableJson } from "../src/files.ts";
import { PhotoStore } from "../src/store.ts";
import { metadata, relativePath } from "../src/validation.ts";
import { assert, jpeg, meta, rejected, setup, stream } from "./helpers.ts";

Deno.test("portable paths preserve Unicode and reject traversal, encoded separators, reserved names and length limits", () => {
  for (const path of ["Projects/Job A/Before", "Été/صور/قبل"]) {
    relativePath(path);
  }
  for (
    const path of [
      "",
      "/a",
      "a/",
      "a//b",
      ".",
      "..",
      "a/../b",
      "C:/a",
      "a\\b",
      "CON.jpg",
      "COM1",
      "LPT².txt",
      "a.",
      "a ",
      "a%2fb",
      "a\x00",
      ".folder-camera-partials",
      "é".repeat(61),
      Array(33).fill("a").join("/"),
    ]
  ) assert.throws(() => relativePath(path));
  assert.throws(() => metadata(meta({ byteSize: 100 }), 99));
});
Deno.test("concurrent same-ID retries publish one file and stable receipt; ID and filename conflicts never overwrite", async () => {
  const f = await setup();
  try {
    const m = meta();
    const receipts = await Promise.all([
      f.store.put(m, stream()),
      f.store.put(m, stream()),
      f.store.put(m, stream()),
    ]);
    assert.deepEqual(receipts[0], receipts[1]);
    const altered = jpeg.slice();
    altered[20] ^= 1;
    await rejected(() => f.store.put(m, stream(altered)), "conflict");
    assert.deepEqual(receipts[1], receipts[2]);
    assert.deepEqual(
      await Deno.readFile(join(f.root, m.relativePath, m.filename)),
      jpeg,
    );
    await rejected(
      () => f.store.put({ ...m, filename: "other.jpg" }, stream()),
      "conflict",
    );
    await rejected(
      () => f.store.put(meta({ filename: m.filename }), stream()),
      "conflict",
    );
    assert.deepEqual(
      await Deno.readFile(join(f.root, m.relativePath, m.filename)),
      jpeg,
    );
    const restarted = new PhotoStore(f.root, f.state, f.auth.receiverId);
    await restarted.init();
    assert.deepEqual(await restarted.put(m, stream()), receipts[0]);
  } finally {
    await f.cleanup();
  }
});
Deno.test("interrupted and corrupt streams never get receipts or completed files", async () => {
  const f = await setup();
  try {
    const m = meta();
    await rejected(
      () => f.store.put(m, stream(jpeg.subarray(0, 5))),
      "content_mismatch",
    );
    const interrupted = new ReadableStream<Uint8Array>({
      pull(controller) {
        controller.error(new Error("disconnected"));
      },
    });
    await rejected(() => f.store.put(m, interrupted), "transient_failure");
    assert.equal(await f.store.receipt(m.photoId), null);
    const parts = [];
    for await (const entry of Deno.readDir(f.store.partials)) {
      parts.push(entry.name);
    }
    assert.deepEqual(parts, []);
    await f.store.put(m, stream());
    const bad = meta({ filename: "bad.jpg", sha256: "0".repeat(64) });
    await rejected(() => f.store.put(bad, stream()), "content_mismatch");
  } finally {
    await f.cleanup();
  }
});
Deno.test("symlink roots, symlink descendants, case aliases and file symlinks are rejected", async () => {
  const f = await setup();
  try {
    const outside = join(f.temp, "outside");
    await Deno.mkdir(outside);
    await Deno.symlink(outside, join(f.root, "Escape"));
    await rejected(
      () => f.store.put(meta({ relativePath: "Escape" }), stream()),
      "unsafe_root",
    );
    await Deno.mkdir(join(f.root, "Case"));
    await rejected(
      () => f.store.put(meta({ relativePath: "case" }), stream()),
      "conflict",
    );
    await Deno.writeFile(join(outside, "external.jpg"), jpeg);
    await Deno.symlink(
      join(outside, "external.jpg"),
      join(f.root, "occupied.jpg"),
    );
    await Deno.symlink(
      join(outside, "external.jpg"),
      join(f.root, "Case", "occupied.jpg"),
    );
    await rejected(
      () =>
        f.store.put(
          meta({ relativePath: "Case", filename: "occupied.jpg" }),
          stream(),
        ),
      "conflict",
    );
    const entries = [];
    for await (const entry of Deno.readDir(outside)) entries.push(entry.name);
    assert.deepEqual(entries, ["external.jpg"]);
    const rootLink = join(f.temp, "link");
    await Deno.symlink(f.root, rootLink);
    await rejected(
      () => new PhotoStore(rootLink, f.state, f.auth.receiverId).init(),
      "unsafe_root",
    );
  } finally {
    await f.cleanup();
  }
});
Deno.test("publication-before-receipt crash window repairs durable receipt on startup; missing or changed bytes never acknowledge", async () => {
  const f = await setup();
  try {
    const m = meta();
    const receipt = await f.store.put(m, stream());
    await Deno.remove(join(f.state, "receipts", `${m.photoId}.json`));
    await durableJson(join(f.state, "journals", `${m.photoId}.json`), {
      metadata: m,
      temp: `${crypto.randomUUID()}.part`,
      receivedAt: receipt.receivedAt,
      committed: false,
    });
    await Deno.writeFile(
      join(f.store.partials, `${crypto.randomUUID()}.part`),
      jpeg.subarray(0, 5),
    );
    const restart = new PhotoStore(f.root, f.state, f.auth.receiverId);
    await restart.init();
    assert.deepEqual(await restart.receipt(m.photoId), receipt);
    await Deno.writeFile(
      join(f.root, m.relativePath, m.filename),
      new Uint8Array([1, 2, 3]),
    );
    await rejected(() => restart.receipt(m.photoId), "receipt_unavailable");
    const parts = [];
    for await (const entry of Deno.readDir(restart.partials)) {
      parts.push(entry.name);
    }
    assert.deepEqual(parts, []);
  } finally {
    await f.cleanup();
  }
});

Deno.test("failure persisting receipt after publication is repaired and never acknowledged early", async () => {
  const f = await setup();
  try {
    const m = meta();
    class FailingReceiptStore extends PhotoStore {
      protected override persistReceipt(): Promise<void> {
        return Promise.reject(
          new Deno.errors.PermissionDenied("Injected storage failure"),
        );
      }
    }
    const failing = new FailingReceiptStore(f.root, f.state, f.auth.receiverId);
    await rejected(() => failing.put(m, stream()), "transient_failure");
    assert.deepEqual(
      await Deno.readFile(join(f.root, m.relativePath, m.filename)),
      jpeg,
    );
    assert.equal(await f.store.receipt(m.photoId), null);
    const restarted = new PhotoStore(f.root, f.state, f.auth.receiverId);
    await restarted.init();
    assert.equal((await restarted.receipt(m.photoId))?.sha256, m.sha256);
    assert.deepEqual(
      await restarted.put(m, stream()),
      await restarted.receipt(m.photoId),
    );
  } finally {
    await f.cleanup();
  }
});

Deno.test("bounded size, invalid JPEG and idle-upload timeout leave no permanent files", async () => {
  const f = await setup();
  try {
    const limited = new PhotoStore(
      f.root,
      f.state,
      f.auth.receiverId,
      jpeg.length - 1,
    );
    assert.throws(() => limited.put(meta(), stream()));
    const nonJpeg = new Uint8Array(jpeg.length);
    const hash = (await import("node:crypto")).createHash("sha256").update(
      nonJpeg,
    ).digest("hex");
    await rejected(
      () => f.store.put(meta({ sha256: hash }), stream(nonJpeg)),
      "invalid_content",
    );
    const timed = new PhotoStore(f.root, f.state, f.auth.receiverId, 65536, 20);
    await rejected(
      () => timed.put(meta(), new ReadableStream({})),
      "upload_timeout",
    );
    assert.equal(
      (await Array.fromAsync(Deno.readDir(f.store.partials))).length,
      0,
    );
  } finally {
    await f.cleanup();
  }
});

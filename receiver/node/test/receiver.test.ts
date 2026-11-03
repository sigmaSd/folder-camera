// The compiled engine (dist/), on Node, as an embedding app runs it: real HTTPS, the phone's requests, durable
// files. The Deno tests (../tests) cover the same engine on Deno in depth; these cover what differs on Node — the
// node:https listener, the pid lock, node:fs publication — end to end.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, X509Certificate } from "node:crypto";
import { request } from "node:https";
import { createServer } from "node:net";
import { mkdir, mkdtemp, readFile, realpath, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { Receiver } from "../dist/index.js";

const jpeg = await readFile(new URL("../../tests/fixtures/photo.jpg", import.meta.url));

// In the repository's .work folder, as the Deno tests: the system's temp folder can sit behind a symlink (macOS'
// /var) or a short name (Windows' RUNNER~1), which the receiver rightly refuses as a root.
const work = fileURLToPath(new URL("../../../.work/node-tests/", import.meta.url));
async function setup() {
  await mkdir(work, { recursive: true });
  const temp = await realpath(await mkdtemp(join(work, "receiver-")));
  return { temp, root: join(temp, "photos"), state: join(temp, "state") };
}

/** A request as the phone makes it: HTTPS pinned to the receiver's own certificate. */
function call(
  url: string,
  cert: string,
  method: string,
  path: string,
  headers: Record<string, string> = {},
  body?: Uint8Array | string,
): Promise<{ status: number; json: any; fingerprint: string }> {
  return new Promise((resolve, reject) => {
    const req = request(`${url}${path}`, { method, headers, ca: cert }, (res) => {
      const chunks: Buffer[] = [];
      const fingerprint = (res.socket as any).getPeerCertificate().fingerprint256;
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const text = Buffer.concat(chunks).toString();
        resolve({ status: res.statusCode!, json: text ? JSON.parse(text) : null, fingerprint });
      });
    });
    req.on("error", reject);
    req.end(body);
  });
}

function photo() {
  const meta = {
    version: 1,
    photoId: crypto.randomUUID(),
    relativePath: "Été/Job A",
    filename: "20261103_test.jpg",
    mimeType: "image/jpeg",
    byteSize: jpeg.length,
    sha256: createHash("sha256").update(jpeg).digest("hex"),
  };
  const encoded = Buffer.from(JSON.stringify(meta)).toString("base64url");
  return { meta, encoded };
}

test("pairs a phone, receives a photo durably, survives a restart, and holds its state alone", async () => {
  const f = await setup();
  const options = () =>
    new Map([["--root", f.root], ["--bind", "127.0.0.1"], ["--port", "19611"]]);
  let r = new Receiver(f.state, options());
  try {
    await r.init();
    await r.start();
    const started = r.snapshot();
    assert.equal(started.status, "ready");
    assert.equal(started.endpoint, "https://127.0.0.1:19611");
    const cert = await readFile(join(f.state, "certificate.pem"), "utf8");
    const url = started.endpoint!;

    // one receiver per state folder
    await assert.rejects(new Receiver(f.state, options()).init(), /in use/);

    // pairing, as the phone does with the QR code's payload
    const { pairing } = await r.pair();
    const paired = await call(url, cert, "POST", "/v1/pair", { "Content-Type": "application/json" }, JSON.stringify({ ...pairing, deviceName: "Node phone" }));
    assert.equal(paired.status, 200);
    assert.equal(paired.json.receiverId, r.auth.receiverId);
    // the certificate served is the one the QR code pins
    assert.equal(paired.fingerprint.replaceAll(":", "").toLowerCase(), started.fingerprint);
    assert.equal(new X509Certificate(cert).fingerprint256.replaceAll(":", "").toLowerCase(), started.fingerprint);
    const auth = { Authorization: `Bearer ${paired.json.token}` };
    assert.equal((await call(url, cert, "GET", "/v1/health", auth)).status, 200);
    assert.equal((await call(url, cert, "GET", "/v1/health")).status, 401);

    // a photo: in its folder, byte for byte, with a receipt
    const { meta, encoded } = photo();
    const put = () =>
      call(url, cert, "PUT", `/v1/photos/${meta.photoId}`, {
        ...auth,
        "Content-Type": "image/jpeg",
        "Content-Length": String(jpeg.length),
        "X-FolderCamera-Metadata": encoded,
      }, jpeg);
    const sent = await put();
    assert.equal(sent.status, 200);
    assert.equal(sent.json.sha256, meta.sha256);
    assert.deepEqual(await readFile(join(f.root, "Été", "Job A", meta.filename)), jpeg);
    const receipt = await call(url, cert, "GET", `/v1/photos/${meta.photoId}`, auth);
    assert.equal(receipt.status, 200);
    assert.equal(receipt.json.photoId, meta.photoId);
    assert.equal(r.snapshot().recent[0].photoId, meta.photoId);
    // sent again (a retry): the same receipt, no second file
    assert.deepEqual((await put()).json, sent.json);

    // other bytes than the metadata says (same length, one changed): refused, nothing published
    const other = photo();
    const altered = Uint8Array.from(jpeg);
    altered[100] ^= 0xff;
    const bad = await call(url, cert, "PUT", `/v1/photos/${other.meta.photoId}`, {
      ...auth,
      "Content-Type": "image/jpeg",
      "X-FolderCamera-Metadata": other.encoded,
    }, altered);
    assert.equal(bad.status, 422);
    assert.equal(bad.json.code, "content_mismatch");

    // stop and start: same address, same certificate
    await r.stop();
    await assert.rejects(call(url, cert, "GET", "/v1/health", auth));
    await r.start();
    assert.equal(r.snapshot().fingerprint, started.fingerprint);
    assert.equal((await call(url, cert, "GET", "/v1/health", auth)).status, 200);

    // closed and opened again (the app restarted): identity, phone and certificate kept
    await r.close();
    r = new Receiver(f.state, options());
    await r.init();
    await r.start();
    assert.equal(r.snapshot().receiverId, started.receiverId);
    assert.equal(r.snapshot().fingerprint, started.fingerprint);
    assert.equal(r.snapshot().devices.length, 1);
    assert.equal((await call(url, cert, "GET", "/v1/health", auth)).status, 200);

    // the phone removed: it can no longer send
    await r.revoke(r.snapshot().devices[0].id);
    assert.equal((await call(url, cert, "GET", "/v1/health", auth)).status, 401);
  } finally {
    await r.close().catch(() => {});
    await rm(f.temp, { recursive: true, force: true });
  }
});

test("a saved port taken by something else: the next free one, kept", async () => {
  const f = await setup();
  const busy = createServer();
  await new Promise<void>((resolve) => busy.listen(19621, "127.0.0.1", resolve));
  await mkdir(f.state, { recursive: true, mode: 0o700 });
  await writeFile(
    join(f.state, "receiver-config.json"),
    JSON.stringify({ version: 1, root: f.root, port: 19621, bind: "127.0.0.1" }),
  );
  const r = new Receiver(f.state);
  try {
    await r.init();
    await r.start();
    assert.equal(r.snapshot().endpoint, "https://127.0.0.1:19622");
    const saved = JSON.parse(await readFile(join(f.state, "receiver-config.json"), "utf8"));
    assert.equal(saved.port, 19622);
  } finally {
    await r.close().catch(() => {});
    busy.close();
    await rm(f.temp, { recursive: true, force: true });
  }
});

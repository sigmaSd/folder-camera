import { join } from "node:path";
import {
  controlArrival,
  controlDevice,
  controlHandler,
  type ControlStatus,
  type ControlTarget,
} from "../desktop/control.ts";
import { networkAction } from "../src/lan.ts";
import { Receiver } from "../src/receiver.ts";
import { assert, setup } from "./helpers.ts";

const token = "a".repeat(64);
const call = (
  handler: (request: Request) => Promise<Response>,
  method: string,
  path: string,
  headers: Record<string, string> = { Authorization: "Bearer " + token },
) => handler(new Request("http://127.0.0.1:1" + path, { method, headers }));
function statusOf(r: Receiver): ControlStatus {
  const s = r.snapshot();
  return {
    version: 1,
    status: s.status,
    error: s.error,
    address: s.endpoint,
    root: s.root,
    devices: s.devices.length,
    pairing: s.pairing ? { expiresAt: s.pairing.expiresAt } : null,
  };
}
/** The desktop app's control target (desktop/main.ts) for an initialized receiver. */
function targetOf(r: Receiver, activate = () => {}): ControlTarget {
  return {
    activate,
    pair: async () => (await r.pair()).pairing,
    status: () => statusOf(r),
    devices: () => r.snapshot().devices.map(controlDevice),
    async revoke(id) {
      if (!r.auth.list().some((d) => d.id === id && !d.revoked)) return false;
      await r.revoke(id);
      return true;
    },
    arrivals: () => r.snapshot().recent.map(controlArrival),
    start: async () => void await r.start(),
    stop: async () => void await r.stop(),
  };
}
const unused = () => Promise.reject(new Error("not reached"));

Deno.test("control API requires the token, refuses browsers and hands out the exact QR payload", async () => {
  const f = await setup();
  const r = new Receiver(
    f.state,
    new Map([["--root", f.root], ["--bind", "127.0.0.1"], ["--port", "19523"]]),
  );
  let activated = 0;
  const handler = controlHandler(token, targetOf(r, () => activated++));
  try {
    await r.init();
    for (
      const [method, path] of [
        ["GET", "/status"],
        ["POST", "/pairing"],
        ["POST", "/activate"],
        ["GET", "/devices"],
        ["POST", `/devices/${crypto.randomUUID()}/revoke`],
        ["GET", "/arrivals"],
        ["POST", "/start"],
        ["POST", "/stop"],
      ]
    ) {
      assert.equal((await call(handler, method, path, {})).status, 403);
      assert.equal(
        (await call(handler, method, path, {
          Authorization: "Bearer " + "b".repeat(64),
        })).status,
        403,
      );
      assert.equal(
        (await call(handler, method, path, {
          Authorization: "Bearer " + token,
          Origin: "http://evil.example",
        })).status,
        403,
      );
    }
    assert.equal(activated, 0);

    const stopped = await call(handler, "POST", "/pairing");
    assert.equal(stopped.status, 409);
    assert.equal((await stopped.json()).code, "not_running");

    await r.start();
    const first = r.snapshot().pairing!;
    const response = await call(handler, "POST", "/pairing");
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("Content-Type"), "application/json");
    const payload = await response.json();
    assert.deepEqual(Object.keys(payload).sort(), [
      "endpoint",
      "expiresAt",
      "fingerprint",
      "receiverId",
      "secret",
      "version",
    ]);
    assert.equal(payload.version, 1);
    assert.equal(payload.receiverId, r.auth.receiverId);
    assert.equal(payload.endpoint, "https://127.0.0.1:19523");
    assert.equal(payload.fingerprint, r.fingerprint);
    assert.match(payload.secret, /^[A-Za-z0-9_-]{43}$/);
    // A fresh single-use code replaces the one shown at start, and the window shows the same one.
    assert.notEqual(payload.secret, first.secret);
    assert.deepEqual(r.snapshot().pairing, payload);
    await assert.rejects(r.auth.pair({ ...first, deviceName: "Old code" }));
    const paired = await r.auth.pair({ ...payload, deviceName: "Phone" });
    assert.ok(paired.token);

    const status = await call(handler, "GET", "/status");
    assert.equal(status.status, 200);
    assert.deepEqual(await status.json(), {
      version: 1,
      status: "ready",
      error: null,
      address: "https://127.0.0.1:19523",
      root: f.root,
      devices: 1,
      pairing: null,
    });

    assert.equal((await call(handler, "POST", "/activate")).status, 204);
    assert.equal(activated, 1);
    assert.equal((await call(handler, "GET", "/pairing")).status, 404);
    assert.equal((await call(handler, "DELETE", "/status")).status, 404);
  } finally {
    await r.close().catch(() => {});
    await f.cleanup();
  }
});

Deno.test("control API reports a starting receiver as unavailable", async () => {
  const starting: ControlStatus = {
    version: 1,
    status: "starting",
    error: null,
    address: null,
    root: null,
    devices: 0,
    pairing: null,
  };
  const handler = controlHandler(token, {
    activate() {},
    pair: unused,
    status: () => starting,
    devices: () => [],
    revoke: unused,
    arrivals: () => [],
    start: unused,
    stop: unused,
  });
  const response = await call(handler, "POST", "/pairing");
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    version: 1,
    code: "not_running",
    status: "starting",
  });
});

Deno.test("control API lists and revokes phones, lists arrivals, and starts and stops the receiver", async () => {
  const f = await setup();
  const arrival = {
    version: 1,
    photoId: crypto.randomUUID(),
    relativePath: "Été/Job A",
    filename: "20261103_test.jpg",
    mimeType: "image/jpeg",
    byteSize: 1234,
    sha256: "c".repeat(64),
    receiverId: crypto.randomUUID(),
    receivedAt: 1793750000000,
    root: f.root,
  };
  await Deno.writeTextFile(
    join(f.state, "activity.json"),
    JSON.stringify([arrival]),
  );
  const r = new Receiver(
    f.state,
    new Map([["--root", f.root], ["--bind", "127.0.0.1"], ["--port", "19525"]]),
  );
  const handler = controlHandler(token, targetOf(r));
  try {
    await r.init();
    const status = async () =>
      (await (await call(handler, "GET", "/status")).json()).status;

    const started = await call(handler, "POST", "/start");
    assert.equal(started.status, 200);
    assert.equal((await started.json()).status, "ready");
    const code = await (await call(handler, "POST", "/pairing")).json();
    await r.auth.pair({ ...code, deviceName: "Pixel" });

    const listed = await (await call(handler, "GET", "/devices")).json();
    assert.equal(listed.version, 1);
    assert.equal(listed.devices.length, 1);
    const [phone] = listed.devices;
    assert.deepEqual(Object.keys(phone).sort(), ["id", "name", "pairedAt"]);
    assert.equal(phone.name, "Pixel");
    assert.ok(Number.isSafeInteger(phone.pairedAt));

    assert.deepEqual(await (await call(handler, "GET", "/arrivals")).json(), {
      version: 1,
      arrivals: [{
        photoId: arrival.photoId,
        filename: arrival.filename,
        relativePath: arrival.relativePath,
        root: f.root,
        byteSize: arrival.byteSize,
        receivedAt: arrival.receivedAt,
      }],
    });

    const unknown = await call(
      handler,
      "POST",
      `/devices/${crypto.randomUUID()}/revoke`,
    );
    assert.equal(unknown.status, 404);
    assert.equal((await unknown.json()).code, "unknown_device");
    assert.equal(
      (await call(handler, "POST", `/devices/${phone.id}/revoke`)).status,
      204,
    );
    assert.deepEqual(await (await call(handler, "GET", "/devices")).json(), {
      version: 1,
      devices: [],
    });
    assert.equal(
      (await call(handler, "POST", `/devices/${phone.id}/revoke`)).status,
      404,
    );
    assert.equal(
      (await call(handler, "POST", "/devices/not-an-id/revoke")).status,
      404,
    );

    const stopped = await call(handler, "POST", "/stop");
    assert.equal((await stopped.json()).status, "stopped");
    await assert.rejects(
      fetch("https://127.0.0.1:19525/v1/health", {
        signal: AbortSignal.timeout(2000),
      }),
    );
    assert.equal(await status(), "stopped");
    assert.equal(
      (await (await call(handler, "POST", "/start")).json()).status,
      "ready",
    );
  } finally {
    await r.close().catch(() => {});
    await f.cleanup();
  }
});

Deno.test("automatic bind keeps, moves or drops the listener as the LAN address changes", () => {
  assert.equal(networkAction("10.48.142.213", "10.48.142.213"), "keep");
  assert.equal(networkAction("10.48.142.213", "10.124.7.88"), "restart");
  assert.equal(networkAction(null, "10.124.7.88"), "restart");
  assert.equal(networkAction("10.48.142.213", null), "lost");
  assert.equal(networkAction(null, null), "lost");
});

Deno.test("receiver follows its LAN address back with the same certificate and identity", async () => {
  const f = await setup();
  let lan: string | null = "127.0.0.1";
  const r = new Receiver(
    f.state,
    new Map([["--root", f.root], ["--bind", "auto"], ["--port", "19524"]]),
    () =>
      lan
        ? Promise.resolve({ address: lan, interfaceName: "wlan0" })
        : Promise.reject(new Error("No usable private LAN address found.")),
  );
  try {
    await r.init();
    await r.start();
    const { endpoint, fingerprint, receiverId } = r.snapshot();
    assert.equal(endpoint, "https://127.0.0.1:19524");

    await r.refreshNetwork();
    assert.equal(r.snapshot().status, "ready");

    lan = null;
    await r.refreshNetwork();
    assert.equal(r.snapshot().status, "error");
    await assert.rejects(
      fetch("https://127.0.0.1:19524/v1/health", {
        signal: AbortSignal.timeout(2000),
      }),
    );

    lan = "127.0.0.1";
    await r.refreshNetwork();
    const back = r.snapshot();
    assert.equal(back.status, "ready");
    assert.equal(back.endpoint, endpoint);
    assert.equal(back.fingerprint, fingerprint);
    assert.equal(back.receiverId, receiverId);
  } finally {
    await r.close().catch(() => {});
    await f.cleanup();
  }
});

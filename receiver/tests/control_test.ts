import { controlHandler, type ControlStatus } from "../desktop/control.ts";
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

Deno.test("control API requires the token, refuses browsers and hands out the exact QR payload", async () => {
  const f = await setup();
  const r = new Receiver(
    f.state,
    new Map([["--root", f.root], ["--bind", "127.0.0.1"], ["--port", "19523"]]),
  );
  let activated = 0;
  const handler = controlHandler(token, {
    activate: () => activated++,
    pair: async () => (await r.pair()).pairing,
    status: () => statusOf(r),
  });
  try {
    await r.init();
    for (
      const [method, path] of [["GET", "/status"], ["POST", "/pairing"], [
        "POST",
        "/activate",
      ]]
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
    pair: () => Promise.reject(new Error("not reached")),
    status: () => starting,
  });
  const response = await call(handler, "POST", "/pairing");
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    version: 1,
    code: "not_running",
    status: "starting",
  });
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

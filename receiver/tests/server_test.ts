import { Auth } from "../src/auth.ts";
import { handler } from "../src/server.ts";
import { certificate } from "../src/tls.ts";
import { assert, jpeg, meta, rejected, setup } from "./helpers.ts";

Deno.test("pairing is single-use, expiring, receiver-bound, persistent and revocable", async () => {
  const f = await setup();
  try {
    const p = await f.auth.startPairing(
      "https://127.0.0.1:8443",
      "a".repeat(64),
    );
    await rejected(
      () =>
        f.auth.pair({
          ...p,
          receiverId: crypto.randomUUID(),
          deviceName: "test",
        }),
      "pairing_invalid",
    );
    const attempts = await Promise.allSettled([
      f.auth.pair({ ...p, deviceName: "A" }),
      f.auth.pair({ ...p, deviceName: "B" }),
    ]);
    assert.equal(attempts.filter((r) => r.status === "fulfilled").length, 1);
    const success = attempts.find((r) =>
      r.status === "fulfilled"
    ) as PromiseFulfilledResult<{ token: string; deviceId: string }>;
    const header = `Bearer ${success.value.token}`;
    assert.equal(f.auth.authenticate(header), success.value.deviceId);
    await rejected(
      () => f.auth.pair({ ...p, deviceName: "reused" }),
      "pairing_invalid",
    );
    const again = new Auth(f.state);
    await again.init();
    assert.equal(again.receiverId, f.auth.receiverId);
    again.authenticate(header);
    const identity = await Deno.readTextFile(`${f.state}/identity.json`);
    assert.equal(identity.includes(success.value.token), false);
    assert.equal(identity.includes(p.secret), false);
    await again.revoke(success.value.deviceId);
    assert.throws(() => again.authenticate(header));
    const expired = await again.startPairing(
      "https://127.0.0.1:8443",
      "a".repeat(64),
    );
    const originalNow = Date.now;
    Date.now = () => originalNow() + 301_000;
    try {
      await rejected(
        () => again.pair({ ...expired, deviceName: "expired" }),
        "pairing_invalid",
      );
    } finally {
      Date.now = originalNow;
    }
  } finally {
    await f.cleanup();
  }
});
Deno.test("protocol bounds metadata, auth, MIME and browser-origin requests", async () => {
  const f = await setup();
  try {
    const h = handler(f.auth, f.store);
    const p = await f.auth.startPairing(
      "https://127.0.0.1:8443",
      "a".repeat(64),
    );
    const paired = await f.auth.pair({ ...p, deviceName: "test" });
    const m = meta();
    const encoded = btoa(unescape(encodeURIComponent(JSON.stringify(m))))
      .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
    const url = `https://127.0.0.1/v1/photos/${m.photoId}`;
    assert.equal(
      (await h(new Request(url, { method: "PUT", body: jpeg }))).status,
      401,
    );
    assert.equal(
      (await h(
        new Request(url, {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${paired.token}`,
            "Content-Type": "image/jpeg",
            "X-FolderCamera-Metadata": encoded,
          },
          body: jpeg,
        }),
      )).status,
      200,
    );
    const receipt = await h(
      new Request(url, {
        headers: { Authorization: `Bearer ${paired.token}` },
      }),
    );
    assert.equal((await receipt.json()).sha256, m.sha256);
    assert.equal(
      (await h(
        new Request(url, {
          headers: {
            Authorization: `Bearer ${paired.token}`,
            Origin: "https://hostile.example",
          },
        }),
      )).status,
      403,
    );
    const invalid = new Request(url, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${paired.token}`,
        "Content-Type": "image/jpeg",
        "X-FolderCamera-Metadata": "%2f",
      },
      body: jpeg,
    });
    assert.equal((await h(invalid)).status, 400);
    const oversized = new Request("https://127.0.0.1/v1/pair", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "x".repeat(4097),
    });
    assert.equal((await h(oversized)).status, 413);
  } finally {
    await f.cleanup();
  }
});
Deno.test("real HTTPS listener pairs and uploads; untrusted certificate fails; identity and certificate survive restart", async () => {
  const f = await setup();
  let server: Deno.HttpServer | undefined;
  let client: Deno.HttpClient | undefined;
  try {
    const tls = await certificate(f.state, "127.0.0.1");
    server = Deno.serve({
      hostname: "127.0.0.1",
      port: 0,
      cert: tls.cert,
      key: tls.key,
      onListen() {},
    }, handler(f.auth, f.store));
    const url = `https://127.0.0.1:${(server.addr as Deno.NetAddr).port}`;
    await assert.rejects(() => fetch(`${url}/v1/health`));
    client = Deno.createHttpClient({ caCerts: [tls.cert], poolIdleTimeout: 0 });
    const p = await f.auth.startPairing(url, tls.fingerprint);
    const pairResponse = await fetch(`${url}/v1/pair`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...p, deviceName: "HTTPS test" }),
      client,
    });
    assert.equal(pairResponse.status, 200);
    const paired = await pairResponse.json();
    const health = await fetch(`${url}/v1/health`, {
      headers: { Authorization: `Bearer ${paired.token}` },
      client,
    });
    assert.equal((await health.json()).receiverId, f.auth.receiverId);
    const m = meta();
    const encoded = btoa(unescape(encodeURIComponent(JSON.stringify(m))))
      .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
    const response = await fetch(`${url}/v1/photos/${m.photoId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${paired.token}`,
        "Content-Type": "image/jpeg",
        "X-FolderCamera-Metadata": encoded,
      },
      body: jpeg,
      client,
    });
    assert.equal(response.status, 200);
    const received = await response.json();
    assert.equal(received.relativePath, m.relativePath);
    assert.deepEqual(await certificate(f.state, "127.0.0.1"), tls);
  } finally {
    client?.close();
    await server?.shutdown();
    await f.cleanup();
  }
});

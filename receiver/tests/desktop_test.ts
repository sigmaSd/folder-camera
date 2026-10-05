import { join } from "node:path";
import { Receiver } from "../src/receiver.ts";
import { PhotoStore } from "../src/store.ts";
import { certificate } from "../src/tls.ts";
import { independentRoots } from "../src/platform.ts";
import { stateDirectory } from "../src/state-directory.ts";
import { assert, jpeg, meta, setup, stream } from "./helpers.ts";

Deno.test("desktop folder changes preserve legacy receipts, old files and paired identity", async () => {
  const f = await setup();
  const first = meta();
  const receipt = await f.store.put(first, stream());
  // A pre-GUI receipt did not record its base directory.
  await Deno.writeTextFile(
    join(f.state, "receipts", first.photoId + ".json"),
    JSON.stringify(receipt),
  );
  const r = new Receiver(
    f.state,
    new Map([["--root", f.root], ["--bind", "127.0.0.1"], ["--port", "19521"]]),
  );
  let client: Deno.HttpClient | undefined;
  try {
    await r.init();
    await r.start();
    const identity = r.auth.receiverId;
    const pairing = r.snapshot().pairing!;
    const paired = await r.auth.pair({
      ...pairing,
      deviceName: "Desktop fixture phone",
    });
    const tls = await certificate(f.state, "127.0.0.1");
    client = Deno.createHttpClient({ caCerts: [tls.cert], poolIdleTimeout: 0 });
    const target = join(f.temp, "another-destination");
    await r.setRoot(target);
    const response = await fetch(r.address + "/v1/photos/" + first.photoId, {
      client,
      headers: { Authorization: "Bearer " + paired.token },
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), receipt);
    assert.deepEqual(
      await Deno.readFile(join(f.root, first.relativePath, first.filename)),
      jpeg,
    );
    assert.equal(r.auth.receiverId, identity);
    assert.equal(r.snapshot().devices.length, 1);
    assert.equal(
      (await certificate(f.state, "127.0.0.1")).fingerprint,
      tls.fingerprint,
    );
    const newStore = new PhotoStore(target, f.state, identity);
    await newStore.init();
    const second = meta({ filename: "next.jpg" });
    await newStore.put(second, stream());
    assert.deepEqual(
      await Deno.readFile(join(target, second.relativePath, second.filename)),
      jpeg,
    );
    await r.stop();
    await r.start();
    assert.equal(r.snapshot().pairing, null);
    await r.revoke(paired.deviceId);
    assert.equal(r.snapshot().devices.length, 0);
  } finally {
    client?.close();
    await r.close().catch(() => {});
    await f.cleanup();
  }
});
Deno.test("desktop state lock excludes a second process and releases after close", async () => {
  const f = await setup();
  const values = new Map([["--root", f.root], ["--bind", "127.0.0.1"], [
    "--port",
    "19522",
  ]]);
  const one = new Receiver(f.state, values),
    two = new Receiver(f.state, values);
  try {
    await one.init();
    await assert.rejects(() => two.init(), /in use/);
    await two.close();
    await one.close();
    await two.init();
  } finally {
    await one.close().catch(() => {});
    await two.close().catch(() => {});
    await f.cleanup();
  }
});
Deno.test("platform roots cannot overlap even through dot-prefixed descendants", async () => {
  const f = await setup();
  try {
    assert.equal(independentRoots(f.root, join(f.root, "..state")), false);
    assert.equal(independentRoots(f.root, f.state), true);
  } finally {
    await f.cleanup();
  }
});
Deno.test("Windows and macOS use native state locations", () => {
  assert.equal(
    stateDirectory(undefined, {
      platform: "win32",
      home: "C:\\Users\\Test",
      localAppData: "C:\\Users\\Test\\AppData\\Local",
    }),
    "C:\\Users\\Test\\AppData\\Local\\FolderCamera",
  );
  assert.equal(
    stateDirectory(undefined, { platform: "darwin", home: "/Users/Test" }),
    "/Users/Test/Library/Application Support/FolderCamera",
  );
});

import { strict as assert } from "node:assert";
import { defaultRoutes, selectLanAddress } from "../src/lan.ts";
import { pairingNeeded, receiverSettings } from "../src/receiver-settings.ts";

Deno.test("automatic LAN selects physical Wi-Fi, skipping Docker, VPNs, loopback and public addresses", () => {
  const selected = selectLanAddress([
    { name: "lo", address: "127.0.0.1", internal: true },
    { name: "tailscale0", address: "fd7a:115c:a1e0::1" },
    { name: "docker0", address: "172.17.0.1" },
    { name: "br-123abc", address: "172.18.0.1" },
    { name: "tun0", address: "10.0.0.1" },
    { name: "nordlynx", address: "10.5.0.1" },
    { name: "custom-vpn", address: "10.6.0.1", mac: "00:00:00:00:00:00" },
    { name: "eth9", address: "203.0.113.10" },
    { name: "wlan0", address: "192.168.1.38" },
  ]);
  assert.deepEqual(selected, {
    address: "192.168.1.38",
    interfaceName: "wlan0",
  });
});
Deno.test("LAN routing metric selects Ethernet or Wi-Fi; no internet route is required", () => {
  const devices = [{ name: "wlan0", address: "192.168.1.20" }, {
    name: "enp2s0",
    address: "10.1.0.20",
  }];
  assert.equal(
    selectLanAddress(devices, [{ name: "enp2s0", metric: 100 }, {
      name: "wlan0",
      metric: 600,
    }]).interfaceName,
    "enp2s0",
  );
  assert.equal(
    selectLanAddress(devices, [{ name: "wlan0", metric: 50 }, {
      name: "enp2s0",
      metric: 100,
    }]).interfaceName,
    "wlan0",
  );
  assert.equal(selectLanAddress(devices).interfaceName, "wlan0");
  assert.deepEqual(
    selectLanAddress([{ name: "en0", address: "fd12:3456::20" }]),
    { address: "fd12:3456::20", interfaceName: "en0" },
  );
  assert.throws(
    () => selectLanAddress([{ name: "lo", address: "127.0.0.1" }]),
    /No usable private LAN/,
  );
});
Deno.test("Linux default-route parsing rejects down and non-default routes", () => {
  const routes = defaultRoutes(
    "Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT\nwlan0 00000000 0101A8C0 0003 0 0 600 00000000 0 0 0\nenp0 00000000 0100000A 0003 0 0 100 00000000 0 0 0\neth1 00000000 0100000A 0000 0 0 1 00000000 0 0 0\nwlan0 0001A8C0 00000000 0001 0 0 600 00FFFFFF 0 0 0",
  );
  assert.deepEqual(routes, [{ name: "enp0", metric: 100 }, {
    name: "wlan0",
    metric: 600,
  }]);
});
Deno.test("zero-option startup defaults are persistent, overrides are remembered and bind auto restores discovery", () => {
  const defaults = receiverSettings(new Map(), null, "/home/test");
  assert.deepEqual(defaults, {
    version: 1,
    root: "/home/test/Captures",
    port: 8443,
    bind: "auto",
  });
  const saved = receiverSettings(
    new Map([["--root", "/captures"], ["--port", "9443"], [
      "--bind",
      "127.0.0.1",
    ]]),
    defaults,
    "/home/test",
  );
  assert.deepEqual(
    receiverSettings(new Map(), saved, "/different/home"),
    saved,
  );
  assert.equal(
    receiverSettings(new Map([["--bind", "auto"]]), saved).bind,
    "auto",
  );
  assert.throws(() =>
    receiverSettings(new Map([["--port", "NaN"]]), null, "/home/test")
  );
  assert.throws(() => receiverSettings(new Map(), { root: "relative" }));
  assert.equal(pairingNeeded([], false), true);
  assert.equal(pairingNeeded([{ revoked: true }], false), true);
  assert.equal(pairingNeeded([{ revoked: false }], false), false);
  assert.equal(pairingNeeded([{ revoked: false }], true), true);
});

Deno.test("automatic occupied port advances; explicit ports and other failures never get hidden", async () => {
  const { listenWithFallback } = await import("../src/listen.ts");
  const attempts: number[] = [];
  const chosen = listenWithFallback(8443, false, (port) => {
    attempts.push(port);
    if (port < 8445) throw new Deno.errors.AddrInUse();
    return "listener";
  });
  assert.deepEqual(attempts, [8443, 8444, 8445]);
  assert.deepEqual(chosen, { server: "listener", port: 8445 });
  assert.throws(() =>
    listenWithFallback(8443, true, () => {
      throw new Deno.errors.AddrInUse();
    }), Deno.errors.AddrInUse);
  assert.throws(() =>
    listenWithFallback(8443, false, () => {
      throw new Deno.errors.PermissionDenied();
    }), Deno.errors.PermissionDenied);
});

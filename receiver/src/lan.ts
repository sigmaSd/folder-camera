import { networkInterfaces } from "node:os";
import { isIP } from "node:net";
import { localIp } from "./validation.ts";

export interface InterfaceAddress {
  name: string;
  address: string;
  internal?: boolean;
  mac?: string;
}
export interface DefaultRoute {
  name: string;
  metric: number;
}
export interface LanAddress {
  address: string;
  interfaceName: string;
}
const virtual =
  /^(?:lo$|docker|virbr|veth|br-[0-9a-f]+$|tailscale|tun\d|tap\d|wg(?:\d|[-_])|nordlynx|ppp|tunnel|utun|vpn|vmnet|vboxnet|zt[0-9a-z])/i;
export function defaultRoutes(text: string): DefaultRoute[] {
  return text.split(/\r?\n/).slice(1).flatMap((line) => {
    const fields = line.trim().split(/\s+/);
    const flags = Number.parseInt(fields[3], 16), metric = Number(fields[6]);
    return fields.length >= 8 && fields[1] === "00000000" &&
        fields[7] === "00000000" && (flags & 1) !== 0 && Number.isFinite(metric)
      ? [{ name: fields[0], metric }]
      : [];
  }).sort((a, b) => a.metric - b.metric || a.name.localeCompare(b.name));
}
/** Never tests internet reachability; only local interfaces and Linux routing data. */
export function selectLanAddress(
  addresses: InterfaceAddress[],
  routes: DefaultRoute[] = [],
): LanAddress {
  const candidates = addresses.filter((item) =>
    !item.internal && item.mac !== "00:00:00:00:00:00" &&
    !virtual.test(item.name) && localIp(item.address) &&
    item.address !== "::1" && !item.address.startsWith("127.")
  );
  const rank = (item: InterfaceAddress) => {
    const route = routes.find((r) => r.name === item.name);
    return [
      isIP(item.address) === 4 ? 0 : 1,
      route ? 0 : 1,
      route?.metric ?? 0,
      /^(wl|wlan|wifi)/i.test(item.name)
        ? 0
        : /^(en|eth)/i.test(item.name)
        ? 1
        : 2,
    ];
  };
  candidates.sort((a, b) => {
    const x = rank(a), y = rank(b);
    for (let i = 0; i < x.length; i++) if (x[i] !== y[i]) return x[i] - y[i];
    return a.name.localeCompare(b.name) || a.address.localeCompare(b.address);
  });
  const selected = candidates[0];
  if (!selected) {
    throw new Error(
      "No usable private LAN address found. Connect the PC to Wi-Fi/Ethernet, or use --bind PRIVATE-IP for a custom interface.",
    );
  }
  return { address: selected.address, interfaceName: selected.name };
}
export async function detectLanAddress(): Promise<LanAddress> {
  const addresses = Object.entries(networkInterfaces()).flatMap((
    [name, items],
  ) =>
    (items ?? []).map((item) => ({
      name,
      address: item.address,
      internal: item.internal,
      mac: item.mac,
    }))
  );
  const routes = Deno.build.os === "linux"
    ? defaultRoutes(await Deno.readTextFile("/proc/net/route").catch(() => ""))
    : [];
  return selectLanAddress(addresses, routes);
}

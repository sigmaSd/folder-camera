import type { Pairing } from "../src/auth.ts";

/** What another app on this PC may read through the loopback control API. */
export interface ControlStatus {
  version: 1;
  status: string;
  error: string | null;
  address: string | null;
  root: string | null;
  devices: number;
  pairing: { expiresAt: number } | null;
}
/** A paired phone that is not revoked. */
export interface ControlDevice {
  id: string;
  name: string;
  pairedAt: number;
}
/** A photo received, newest first; `relativePath` is the folder under `root`. */
export interface ControlArrival {
  photoId: string;
  filename: string;
  relativePath: string;
  root: string;
  byteSize: number;
  receivedAt: number;
}
export const controlDevice = (
  { id, name, createdAt }: { id: string; name: string; createdAt: number },
): ControlDevice => ({ id, name, pairedAt: createdAt });
export const controlArrival = (
  { photoId, filename, relativePath, root, byteSize, receivedAt }:
    ControlArrival,
): ControlArrival => ({
  photoId,
  filename,
  relativePath,
  root,
  byteSize,
  receivedAt,
});
export interface ControlTarget {
  activate(): void;
  /** Resolves to null when the receiver is not running, so no code can be issued. */
  pair(): Promise<Pairing | null>;
  status(): ControlStatus;
  devices(): ControlDevice[];
  /** Resolves to false for a phone that isn't paired (or is already revoked). */
  revoke(id: string): Promise<boolean>;
  arrivals(): ControlArrival[];
  /** Start or stop listening for phones; a start that fails shows in the status afterwards. */
  start(): Promise<void>;
  stop(): Promise<void>;
}
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
const revokeRoute = /^POST \/devices\/([0-9a-f-]{36})\/revoke$/;
/**
 * Loopback-only control routes. Every request needs the bearer token stored in
 * desktop-instance.json; browser requests (any Origin header) are refused.
 */
export function controlHandler(token: string, target: ControlTarget) {
  return async (request: Request): Promise<Response> => {
    if (
      request.headers.has("Origin") ||
      request.headers.get("Authorization") !== "Bearer " + token
    ) return new Response("Forbidden", { status: 403 });
    const route = `${request.method} ${new URL(request.url).pathname}`;
    if (route === "POST /activate") {
      target.activate();
      return new Response(null, { status: 204 });
    }
    if (route === "GET /status") return json(target.status());
    if (route === "POST /pairing") {
      const before = target.status();
      if (before.status !== "ready") {
        return json(
          { version: 1, code: "not_running", status: before.status },
          before.status === "starting" ? 503 : 409,
        );
      }
      const pairing = await target.pair().catch(() => null);
      return pairing ? json(pairing) : json({
        version: 1,
        code: "not_running",
        status: target.status().status,
      }, 409);
    }
    if (route === "GET /devices") {
      return json({ version: 1, devices: target.devices() });
    }
    const revoke = route.match(revokeRoute);
    if (revoke) {
      return await target.revoke(revoke[1])
        ? new Response(null, { status: 204 })
        : json({ version: 1, code: "unknown_device" }, 404);
    }
    if (route === "GET /arrivals") {
      return json({ version: 1, arrivals: target.arrivals() });
    }
    if (route === "POST /start" || route === "POST /stop") {
      // A start that fails is reported by the status, like one at launch.
      await (route === "POST /start" ? target.start() : target.stop()).catch(
        () => {},
      );
      return json(target.status());
    }
    return json({ version: 1, code: "not_found" }, 404);
  };
}

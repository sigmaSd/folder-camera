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
export interface ControlTarget {
  activate(): void;
  /** Resolves to null when the receiver is not running, so no code can be issued. */
  pair(): Promise<Pairing | null>;
  status(): ControlStatus;
}
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
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
    return json({ version: 1, code: "not_found" }, 404);
  };
}

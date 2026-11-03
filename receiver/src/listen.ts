import { isAddrInUse } from "./runtime.ts";

/** Automatic ports recover from a busy socket; explicit --port stays exact. */
export function listenWithFallback<T>(
  preferred: number,
  fixed: boolean,
  listen: (port: number) => T,
): { server: T; port: number } {
  const last = fixed ? preferred : Math.min(65535, preferred + 100);
  for (let port = preferred; port <= last; port++) {
    try {
      return { server: listen(port), port };
    } catch (e) {
      if (fixed || !isAddrInUse(e) || port === last) {
        throw e;
      }
    }
  }
  throw new Error("No available receiver port");
}
/** The same, for a listener that starts asynchronously (node:https). */
export async function listenWithFallbackAsync<T>(
  preferred: number,
  fixed: boolean,
  listen: (port: number) => Promise<T>,
): Promise<{ server: T; port: number }> {
  const last = fixed ? preferred : Math.min(65535, preferred + 100);
  for (let port = preferred; port <= last; port++) {
    try {
      return { server: await listen(port), port };
    } catch (e) {
      if (fixed || !isAddrInUse(e) || port === last) {
        throw e;
      }
    }
  }
  throw new Error("No available receiver port");
}

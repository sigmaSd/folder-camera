import { homedir } from "node:os";
import { isAbsolute, join, resolve } from "node:path";

export interface ReceiverSettings {
  version: 1;
  root: string;
  port: number;
  bind: string;
}
export function integerOption(
  name: string,
  value: string | number | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  const result = Number(value ?? fallback);
  if (!Number.isInteger(result) || result < min || result > max) {
    throw new Error(`Invalid ${name}`);
  }
  return result;
}
export function receiverSettings(
  values: Map<string, string>,
  saved: Partial<ReceiverSettings> | null,
  home?: string,
): ReceiverSettings {
  if (saved?.version !== undefined && saved.version !== 1) {
    throw new Error("Unsupported receiver configuration version");
  }
  if (
    saved?.root !== undefined &&
    (typeof saved.root !== "string" || !isAbsolute(saved.root))
  ) throw new Error("Invalid saved destination");
  if (saved?.bind !== undefined && typeof saved.bind !== "string") {
    throw new Error("Invalid saved bind preference");
  }
  const chosenRoot = values.get("--root") ?? saved?.root ??
    join(home ?? homedir(), "Captures");
  if (!chosenRoot) throw new Error("--root must not be empty");
  return {
    version: 1,
    root: resolve(chosenRoot),
    port: integerOption(
      "--port",
      values.get("--port") ?? saved?.port,
      8443,
      1024,
      65535,
    ),
    bind: values.get("--bind") ?? saved?.bind ?? "auto",
  };
}
export function pairingNeeded(
  devices: { revoked: boolean }[],
  explicitlyRequested: boolean,
): boolean {
  return explicitlyRequested || !devices.some((device) => !device.revoked);
}

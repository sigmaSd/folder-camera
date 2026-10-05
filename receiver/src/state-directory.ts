import { homedir } from "node:os";
import { posix, win32 } from "node:path";

/** Linux defaults follow XDG; an explicit --state always wins. */
export function stateDirectory(
  explicit: string | undefined,
  environment: {
    xdgStateHome?: string;
    home?: string;
    platform?: string;
    localAppData?: string;
  } = {},
): string {
  const paths = environment.platform === "win32" ||
      !environment.platform && Deno.build.os === "windows"
    ? win32
    : posix;
  const { isAbsolute, join, resolve } = paths;
  if (explicit !== undefined) {
    if (!explicit) throw new Error("--state must not be empty");
    return resolve(explicit);
  }
  const home = environment.home && isAbsolute(environment.home)
    ? environment.home
    : homedir();
  if (environment.platform === "win32") {
    const local = environment.localAppData;
    return join(
      local && isAbsolute(local) ? local : join(home, "AppData", "Local"),
      "FolderCamera",
    );
  }
  if (environment.platform === "darwin") {
    return join(home, "Library", "Application Support", "FolderCamera");
  }
  const xdg = environment.xdgStateHome;
  return join(
    xdg && isAbsolute(xdg) ? xdg : join(home, ".local", "state"),
    "folder-camera",
  );
}

import { homedir } from "node:os";
import { isAbsolute, join, resolve } from "node:path";

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
  const xdg = environment.xdgStateHome;
  return join(
    xdg && isAbsolute(xdg) ? xdg : join(home, ".local", "state"),
    "folder-camera",
  );
}

import { dirname, isAbsolute, join } from "node:path";
import { homedir } from "node:os";
import { noLinks, syncDirectory } from "../src/files.ts";
import { privateMode } from "../src/platform.ts";
const nativeName = "FolderCameraReceiver";
async function command(
  program: string,
  args: string[],
  env?: Record<string, string>,
) {
  return await new Deno.Command(program, {
    args,
    env,
    stdin: "null",
    stdout: "piped",
    stderr: "piped",
  }).output();
}
export async function chooseFolder(
  root: string,
): Promise<{ path?: string; fallback?: boolean }> {
  if (Deno.build.os === "windows") {
    const script =
      '[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.FolderBrowserDialog; $d.Description = "Choose a folder for Folder Camera photos"; $d.SelectedPath = $env:FOLDER_CAMERA_INITIAL; if ($d.ShowDialog() -eq "OK") { [Console]::Write($d.SelectedPath) }; $d.Dispose()';
    try {
      const result = await command("powershell.exe", [
        "-NoProfile",
        "-STA",
        "-Command",
        script,
      ], { FOLDER_CAMERA_INITIAL: root });
      if (result.success) {
        return { path: new TextDecoder().decode(result.stdout) || undefined };
      }
    } catch { /* Built-in chooser remains available on locked-down hosts. */ }
  } else if (Deno.build.os === "darwin") {
    const script =
      'on run argv\ntry\nreturn POSIX path of (choose folder with prompt "Choose a folder for Folder Camera photos" default location (POSIX file (item 1 of argv)))\non error number -128\nreturn ""\nend try\nend run';
    try {
      const result = await command("osascript", ["-e", script, root]);
      if (result.success) {
        return {
          path: new TextDecoder().decode(result.stdout).replace(/\r?\n$/, "") ||
            undefined,
        };
      }
    } catch { /* Fall through to built-in chooser. */ }
  } else {
    for (
      const [program, args] of [["zenity", [
        "--file-selection",
        "--directory",
        "--title=Choose a capture folder",
        `--filename=${root}/`,
      ]], ["kdialog", [
        "--getexistingdirectory",
        root,
        "--title",
        "Choose a capture folder",
      ]]] as const
    ) {
      try {
        const result = await command(program, [...args]);
        if (result.success || result.code === 1) {
          return {
            path:
              new TextDecoder().decode(result.stdout).replace(/\r?\n$/, "") ||
              undefined,
          };
        }
      } catch (e) {
        if (!(e instanceof Deno.errors.NotFound)) break;
      }
    }
  }
  return { fallback: true };
}
export async function browseFolders(path?: string) {
  const current = path ?? homedir();
  if (
    !isAbsolute(current) || current.length > 4096 ||
    [...current].some((c) => c.codePointAt(0)! < 32)
  ) throw new Error("Enter a complete folder path.");
  await noLinks(current);
  const folders = [];
  for await (const entry of Deno.readDir(current)) {
    if (entry.isDirectory && !entry.isSymlink && !entry.name.startsWith(".")) {
      folders.push({ name: entry.name, path: join(current, entry.name) });
    }
  }
  folders.sort((a, b) => a.name.localeCompare(b.name));
  return {
    path: current,
    parent: dirname(current),
    folders: folders.slice(0, 2000),
  };
}
export async function openFolder(root: string) {
  await noLinks(root);
  const program = Deno.build.os === "windows"
    ? "explorer.exe"
    : Deno.build.os === "darwin"
    ? "open"
    : "xdg-open";
  const child = new Deno.Command(program, {
    args: [root],
    stdin: "null",
    stdout: "null",
    stderr: "null",
  }).spawn();
  child.unref();
}
function startupPath() {
  return Deno.build.os === "darwin"
    ? join(
      homedir(),
      "Library",
      "LaunchAgents",
      "io.github.sigmasd.foldercamera.receiver.plist",
    )
    : join(
      Deno.env.get("XDG_CONFIG_HOME") || join(homedir(), ".config"),
      "autostart",
      "folder-camera-receiver.desktop",
    );
}
const launcher = () => Deno.env.get("APPIMAGE") || Deno.execPath();
export async function autostartEnabled(): Promise<boolean> {
  if (Deno.build.os === "windows") {
    const output = await command("reg.exe", [
      "query",
      "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
      "/v",
      nativeName,
    ]);
    return output.success;
  }
  return await Deno.lstat(startupPath()).then((info) =>
    info.isFile && !info.isSymlink
  ).catch(() => false);
}
export async function setAutostart(enabled: boolean) {
  const app = launcher();
  if (!isAbsolute(app) || [...app].some((c) => c.codePointAt(0)! < 32)) {
    throw new Error("Install the receiver before enabling launch at login.");
  }
  if (Deno.build.os === "windows") {
    const path = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    const output = await command(
      "reg.exe",
      enabled
        ? [
          "add",
          path,
          "/v",
          nativeName,
          "/t",
          "REG_SZ",
          "/d",
          `"${app}" --background`,
          "/f",
        ]
        : ["delete", path, "/v", nativeName, "/f"],
    );
    if (!output.success && (enabled || await autostartEnabled())) {
      throw new Error("Windows could not update launch-at-login settings.");
    }
    return;
  }
  const path = startupPath();
  await Deno.mkdir(dirname(path), { recursive: true, mode: 0o700 });
  await noLinks(dirname(path));
  try {
    const info = await Deno.lstat(path);
    if (info.isSymlink || !info.isFile) {
      throw new Error("Unsafe startup setting");
    }
  } catch (e) {
    if (!(e instanceof Deno.errors.NotFound)) throw e;
  }
  if (!enabled) {
    await Deno.remove(path).catch((e) => {
      if (!(e instanceof Deno.errors.NotFound)) throw e;
    });
    await syncDirectory(dirname(path));
    return;
  }
  const xml = (value: string) =>
    value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(
      ">",
      "&gt;",
    ).replaceAll('"', "&quot;");
  const quoted = app.replaceAll("\\", "\\\\").replaceAll('"', '\\"').replaceAll(
    "`",
    "\\`",
  ).replaceAll("$", "\\$").replaceAll("%", "%%");
  const text = Deno.build.os === "darwin"
    ? `<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>Label</key><string>io.github.sigmasd.foldercamera.receiver</string><key>ProgramArguments</key><array><string>${
      xml(app)
    }</string><string>--background</string></array><key>RunAtLoad</key><true/></dict></plist>`
    : `[Desktop Entry]\nType=Application\nName=Folder Camera Receiver\nExec="${quoted}" --background\nTerminal=false\nX-GNOME-Autostart-enabled=true\n`;
  const file = await Deno.open(path, {
    create: true,
    write: true,
    truncate: true,
    mode: 0o600,
  });
  try {
    const bytes = new TextEncoder().encode(text);
    let at = 0;
    while (at < bytes.length) at += await file.write(bytes.subarray(at));
    await file.sync();
  } finally {
    file.close();
  }
  await privateMode(path, 0o600);
  await syncDirectory(dirname(path));
}

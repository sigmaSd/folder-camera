# Desktop receiver release

The GUI uses Deno Desktop 2.9.7 and the same protocol/storage/authentication engine as the CLI. The first Deno.serve listener serves bundled GUI assets on Deno's loopback port; the second listener serves certificate-scoped HTTPS on the chosen private LAN interface. This behavior was verified in a packaged native probe, not inferred from the documentation. Privileged GUI controls are per-window native bindings. The only HTTP control surface is the token-protected loopback listener described under [Local control API](#local-control-api); it cannot revoke phones, change folders or stop the receiver.

The implemented interface includes destination selection/opening, expiring QR pairing, paired-phone revocation, active transfers/recent arrivals, pause/start, optional launch-at-login and optional close-to-tray. A native OS folder dialog is used where available; the app's own folder browser remains available when a platform helper is unavailable. The desktop runtime and certificate generation are bundled; users do not install Deno or OpenSSL.

Existing standard CLI profiles, identities and certificates are retained. On macOS a legacy XDG-style profile is reused when present. Receipt records bind saved photos to their original base directory before a folder change; earlier files are not moved and receipt/retry lookup does not silently redirect them.

The [1.1.0 release](https://github.com/sigmaSd/folder-camera/releases/tag/receiver-v1.1.0) contains all six validated installers, SHA256SUMS and a build manifest; [1.0.0](https://github.com/sigmaSd/folder-camera/releases/tag/receiver-v1.0.0) was the first stable release. The [product website](https://sigmasd.github.io/folder-camera/) links the signed Android APK and each desktop architecture. The owner tested the receiver and approved stable release on 2026-10-05. Publisher signing/notarization remains separate and pending.

## Headless mode

`--headless` runs the receiver with no window and no tray icon. It uses the same state folder and single-instance lock as the GUI; `--state`, `--root`, `--bind` and `--port` work as usual. Starting a second instance on the same state exits after notifying the running one. If the receiver cannot start (for example, the state is locked by the CLI), headless mode prints the error to stderr and exits with status 1. `SIGINT`/`SIGTERM` stop it cleanly and remove `desktop-instance.json`. `--background` only hides the window when a tray exists; `--headless` never creates one.

## Local control API

Other apps on the same PC (for example, one that draws the pairing QR itself) can use the desktop receiver's loopback control listener. This is a stable, versioned local API.

**Finding it.** Once the receiver has started, it writes `desktop-instance.json` (mode 0600) to its state folder: `{"port": 41234, "token": "<64 hex chars>"}`. The port is random on each launch; read the file again after a restart. The file is removed on a clean exit. The default state folder is `$XDG_STATE_HOME/folder-camera` or `~/.local/state/folder-camera` on Linux, `%LOCALAPPDATA%\FolderCamera` on Windows and `~/Library/Application Support/FolderCamera` on macOS, unless `--state` is given.

**Rules.** The listener binds `http://127.0.0.1:<port>` only. Every request must send `Authorization: Bearer <token>`. Requests with any `Origin` header are refused, so web pages cannot use it. A missing or wrong token, or an `Origin` header, gets `403` with no body. Unknown routes get `404 {"version":1,"code":"not_found"}`. JSON responses use `Content-Type: application/json` and `Cache-Control: no-store`.

| Route | Success | Body |
| --- | --- | --- |
| `GET /status` | 200 | `{"version":1,"status":"ready","error":null,"address":"https://192.168.1.38:8443","root":"/home/user/Captures","devices":1,"pairing":{"expiresAt":1792760000000}}` |
| `POST /pairing` | 200 | `{"version":1,"receiverId":"…","endpoint":"https://192.168.1.38:8443","fingerprint":"<64 hex>","secret":"<43 chars>","expiresAt":1792760000000}` |
| `POST /activate` | 204 | none; shows and focuses the window (does nothing in headless mode) |

`GET /status`: `status` is `starting`, `ready`, `pausing`, `stopped` or `error`; `error` is a human-readable message or `null`; `address` is the HTTPS endpoint phones use, or `null` while not listening; `root` is the destination folder or `null` before one is set; `devices` counts paired phones that are not revoked; `pairing` is `{"expiresAt": <ms since epoch>}` while a pairing code is pending, else `null`.

`POST /pairing` issues a fresh single-use code that expires in five minutes, replacing any pending one (the window's QR, if shown, switches to it). Encode the response body, unchanged, as the QR text: it is exactly the payload the Android app scans. Treat `secret` as sensitive and do not log it. When the receiver is not listening, the response is `409 {"version":1,"code":"not_running","status":"<status>"}`, or `503` with the same body while it is still starting; retry after `GET /status` reports `ready`.

When the PC changes network, `address` changes. A code issued before the change carries the old address, so request a new one.

## Packaging

Run `python scripts/build_desktop.py` for a native directory, or select --format AppImage/rpm/deb/msi/app/dmg and --target. All build temporary files remain under .work/tmp. macOS DMG packaging requires a macOS host. GitHub Actions builds/tests Linux x64/ARM64, Windows x64/ARM64 and macOS Intel/Apple Silicon separately. Distribution links are added only for artifacts that actually exist. Linux AppImages require WebKitGTK 4.1 and GTK 3 system libraries; Ubuntu 24.04 is the tested baseline. Windows requires the standard WebView2 runtime provided on current Windows 10/11 installations. ARM64 MSI metadata requires Installer 5.0; our finalization step corrects Deno’s 2.0 default.

Publisher signing/notarization credentials have not been supplied. Windows/macOS packages are unsigned or ad-hoc signed and may trigger OS trust prompts. A native build/test result does not imply notarization or trusted publisher signing.

## Current validation

- Native Deno Desktop probe proves the second server retains its specified port.
- Packaged Linux GUI startup initializes the native window/bindings and independent TLS receiver in an isolated fixture profile.
- Twenty receiver tests pass locally, including folder-change/legacy-receipt migration, certificate continuity, state-lock exclusion, restart repair and no-overwrite behavior.
- Native CI verifies rendered readiness, TLS startup and clean exit, then launches the application from each actual AppImage/MSI/DMG. Windows installs/uninstalls the MSI at its actual per-user destination. A read-only Program Files check reproduced upstream WebView2 E_ACCESSDENIED; the MSI therefore installs under Local AppData without administrator elevation, rather than distributing that broken per-machine package. All six native platform and installer checks passed in [acceptance run 37270255254](https://github.com/sigmaSd/folder-camera/actions/runs/37270255254), source e4b4364fd0f2842d358b1963a15a5251709678a7.

The fixture preview is a development tool and is not imported into packaged applications. It contains no real photos or live receiver credentials.

Deno 2.9.3 had broken native callback wrappers (upstream #36065). The project uses Deno 2.9.7, which includes that fix and subsequent desktop lifecycle fixes. The native smoke verifies the rendered Ready state through actual callbacks, not just backend initialization. It also requires clean process shutdown. Deno 2.9.7 ships pinned Laufey 0.7.0 backends, including native Windows ARM64; earlier compatibility builds have been superseded.

# Changelog

## 1.0.4

- Pair, check and send photos even when a VPN that can't be bypassed (such as Tailscale) stops the app from using the Wi-Fi network directly: the app retries once on the default route, with the same certificate pin.
- Say why pairing failed: an expired code (or a wrong phone clock), an unreachable PC, a certificate that doesn't match, a code already used, or too many attempts.
- Swipe between a folder's photos in the viewer, which shows the photo's position (for example "2 / 24").
- List photos newest first; folders stay in A→Z order above them.
- Add an "Open with" button that opens the current photo in your gallery or any other viewer app.

## Receiver engine 1.3.0 (Node package)

- The receiver engine (`receiver/src`) runs on Node as well as Deno: files through `node:fs`, and on Node an HTTPS listener from `node:https` and a pid-file state lock. On Deno the desktop app and the CLI keep `Deno.serve`, `flock` and Windows' write-through moves, unchanged.
- `receiver/node` packages it for apps that embed a receiver of their own (compiled JavaScript with type declarations), released as a tarball (`receiver-engine-v1.3.0`). Same protocol and state folder format: a state folder moves between the desktop app, the CLI and an embedding app with its phones still paired. See receiver/node/README.md.
- Known issue in the desktop app 1.2.0: `--headless` still opens a window. The desktop runtime puts the first `Deno.serve` in a window of its own, and the app's interface server starts even when headless. Apps should embed the engine instead.

## Receiver 1.2.0

- The local control API manages the receiver without its window: `GET /devices`, `POST /devices/<id>/revoke`, `GET /arrivals`, `POST /start` and `POST /stop`. See docs/desktop-receiver.md.
- `--exit-with-stdin` lets the app that started the receiver own it: the receiver quits when that app's end of stdin closes, even if the app crashed.
- Documented: each `--state` folder is a separate receiver, so another app can run its own next to the user's.

## Receiver 1.1.0

- `--headless` runs the desktop receiver with no window or tray icon.
- A local control API on the token-protected loopback listener (`GET /status`, `POST /pairing`) lets other apps on the PC draw the pairing QR themselves. See docs/desktop-receiver.md.
- Automatic binds follow the PC to a new network address, without a restart by hand.

## 1.0.3

- Add a desktop receiver download button in Settings → PC sync, linking directly to Windows, macOS and Linux installers.
- Explain how to install the receiver and pair it with the phone.

## 1.0.2

- Add a shutter animation and haptic feedback when capture starts.
- Give Paths more room and remove the confusing cancel line.
- Add folder search and a full-screen photo viewer that preserves your place.
- Fix switching between paired computers and retrying transfers after address changes.
- Summarize transfers per computer and keep photo-import choices tied to the selected receiver.
- Move folder metadata and photo decoding off the UI thread.

## 1.0.1

- Enable R8 code optimization and resource shrinking for a smaller Android release.
- Retain the same application/signing identity and existing folder, photo and pairing data.

## 1.0.0 — release candidate

- Native offline folder camera with explicit destination confirmation and exact nested Unicode paths.
- Complete searchable Paths catalog, photo browser/share/export, zoom and continuous-light controls.
- Verified local-save recovery and durable optional certificate-verified LAN delivery.
- Automatic receiver address, storage/port defaults and first-use pairing QR.
- Production/debug app identities separated; privacy/license access and release tooling added.

The 0.2.1 development build was tested on the owner's Android 16 phone. Public store publication is tracked in release/README.md and requires the remaining account and release gates.

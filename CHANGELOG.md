# Changelog

## 1.0.4

- Pair, check and send photos even when a VPN that can't be bypassed (such as Tailscale) stops the app from using the Wi-Fi network directly: the app retries once on the default route, with the same certificate pin.
- Say why pairing failed: an expired code (or a wrong phone clock), an unreachable PC, a certificate that doesn't match, a code already used, or too many attempts.

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

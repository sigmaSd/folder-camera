# Desktop receiver release

The GUI uses Deno Desktop 2.9.4 and the same protocol/storage/authentication engine as the CLI. The first Deno.serve listener serves bundled GUI assets on Deno's loopback port; the second listener serves certificate-scoped HTTPS on the chosen private LAN interface. This behavior was verified in a packaged native probe, not inferred from the documentation. Privileged GUI controls are per-window native bindings; there is no HTTP management endpoint.

The implemented interface includes destination selection/opening, expiring QR pairing, paired-phone revocation, active transfers/recent arrivals, pause/start, optional launch-at-login and optional close-to-tray. A native OS folder dialog is used where available; the app's own folder browser remains available when a platform helper is unavailable. The desktop runtime and certificate generation are bundled; users do not install Deno or OpenSSL.

Existing standard CLI profiles, identities and certificates are retained. On macOS a legacy XDG-style profile is reused when present. Receipt records bind saved photos to their original base directory before a folder change; earlier files are not moved and receipt/retry lookup does not silently redirect them.

## Packaging

Run `python scripts/build_desktop.py` for a native directory, or select --format AppImage/rpm/deb/msi/app/dmg and --target. All build temporary files remain under .work/tmp. macOS DMG packaging requires a macOS host. GitHub Actions builds/tests Linux x64/ARM64, Windows x64/ARM64 and macOS Intel/Apple Silicon separately. Distribution links are added only for artifacts that actually exist.

Publisher signing/notarization credentials have not been supplied. Windows/macOS candidates are unsigned or ad-hoc signed and may trigger OS trust prompts. A native build/test result does not imply notarization or trusted publisher signing.

## Current validation

- Native Deno Desktop probe proves the second server retains its specified port.
- Packaged Linux GUI startup initializes the native window/bindings and independent TLS receiver in an isolated fixture profile.
- Twenty receiver tests pass locally, including folder-change/legacy-receipt migration, certificate continuity, state-lock exclusion, restart repair and no-overwrite behavior.
- Native platform CI and installer validation are in progress; platform readiness is not claimed before those results pass.

The fixture preview is a development tool and is not imported into packaged applications. It contains no real photos or live receiver credentials.

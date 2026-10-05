# Desktop receiver release

The GUI uses Deno Desktop 2.9.7 and the same protocol/storage/authentication engine as the CLI. The first Deno.serve listener serves bundled GUI assets on Deno's loopback port; the second listener serves certificate-scoped HTTPS on the chosen private LAN interface. This behavior was verified in a packaged native probe, not inferred from the documentation. Privileged GUI controls are per-window native bindings; there is no HTTP management endpoint.

The implemented interface includes destination selection/opening, expiring QR pairing, paired-phone revocation, active transfers/recent arrivals, pause/start, optional launch-at-login and optional close-to-tray. A native OS folder dialog is used where available; the app's own folder browser remains available when a platform helper is unavailable. The desktop runtime and certificate generation are bundled; users do not install Deno or OpenSSL.

Existing standard CLI profiles, identities and certificates are retained. On macOS a legacy XDG-style profile is reused when present. Receipt records bind saved photos to their original base directory before a folder change; earlier files are not moved and receipt/retry lookup does not silently redirect them.

The [1.0.0 release](https://github.com/sigmaSd/folder-camera/releases/tag/receiver-v1.0.0) contains all six validated installers, SHA256SUMS and a build manifest. The [product website](https://sigmasd.github.io/folder-camera/) links the signed Android APK and each desktop architecture. The owner tested the receiver and approved stable release on 2026-10-05. Publisher signing/notarization remains separate and pending.

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

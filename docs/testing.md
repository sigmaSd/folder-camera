# Verification and acceptance checklist

The 0.2.0 UX update adds standard local receiver state, complete searchable Paths, CameraX torch modes/zoom, and a redesigned interface. Its updated checks are recorded in ux-update-results.txt. The automatic receiver startup checks are recorded in automatic-startup-results.txt (16 tests plus actual private-interface HTTPS/CLI tests). The initial-run results below remain historical.

Results recorded 2026-10-04, Africa/Tunis. Only generated development JPEGs were used. No real user photo was transmitted and no public server was deployed.

## Automated commands and results

| Check | Result |
|---|---|
| Gradle wrapper JAR SHA-256 vs official distribution | Matched `55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c` |
| `:app:assembleDebug` | Passed; installable development APK |
| `:app:assembleRelease` | Passed; unsigned APK, no release signing requested |
| `:app:assembleDebugAndroidTest` | Passed; device test APK builds |
| `:app:testDebugUnitTest` | Ten passed: path/gate/backoff, real JVM self-signed TLS pin, Robolectric SAF/Room recovery |
| `:app:lintDebug` / release vital lint | Passed with no errors; advisory warnings remain |
| `deno task check`, `deno task lint`, `deno fmt --check` | Passed |
| `deno task test` | Ten tests passed, including real loopback HTTPS |
| CLI smoke (`python3 receiver/tests/cli_smoke.py` after caching) | Passed offline/cached startup, QR encoding, pairing, Unicode mirrored upload, duplicate retry, restart/receipt persistence, state lock and revocation |
| Android runtime dependency tree and receiver lock/license audit | Completed; source/package license notices copied; no prohibited proprietary runtime groups found |
| `:app:connectedDebugAndroidTest` | Blocked: `DeviceException: No connected devices!` |

Final test counts and artifact hashes are recorded in `build-results.txt` beside this document. Source test locations:

- `android/app/src/test/.../CoreTest.kt`: portable paths, Unicode preservation, fresh destination gate, cancel/confirmation, lease/backoff policy.
- `android/app/src/test/.../TlsTest.kt`: real loopback TLS server; matching exact self-signed fingerprint succeeds, wrong fingerprint fails, unsafe endpoints fail.
- `android/app/src/test/.../HostRecoveryTest.kt`: injected permission/write/readback failures, retained staging, completed-copy crash recovery, interrupted capture, repeated folders without overwrite, Room claim concurrency/expiry/stale-completion protection and original receiver binding.
- `android/app/src/androidTest/.../GateUiTest.kt`: fresh gate, access to settings without capture, confirmed folder changes/cancel and Activity recreation. Built but not executed here.
- `android/app/src/androidTest/.../RecoveryTest.kt`: device equivalents of storage/queue tests. Built but not executed here.
- `receiver/tests/`: traversal/encoded separators/reserved names, metadata/size bounds, pairing expiration/reuse/identity/token/revocation, concurrent duplicate uploads, changed retry body/ID/filename conflicts, symlinks/case aliases, corrupt/interrupted uploads, idle timeout, crash journals and publication/receipt-write failure, real HTTPS certificate trust, protocol/header/origin validation.

Robolectric tests use a debug-only DocumentsProvider and explicitly model its own tree-prefix URI grant and Android O query adapter, which the host resolver does not fully reproduce. They test actual PhotoStore/Room logic, not physical provider behavior or a real system picker. The debug provider is protected by MANAGE_DOCUMENTS; it and all test fixtures/test libraries are excluded from the release APK.

## Actual blockers and limits

- No physical Android device is attached. An isolated API-34 emulator was created from the available system image. It segfaulted in `qemu-system-x86_64-headless` before ADB became available with both SwiftShader and GPU-off/Vulkan-disabled configurations. Coredump inspection confirmed SIGSEGV. Therefore no device installation, Compose instrumentation, real camera, rotation UI, actual SAF grant revocation, Android Keystore hardware or Android-17 LAN permission exercise is claimed.
- Host source/build checks use target SDK 37.0, but the available emulator image is API 34. Android 17 behavior needs a working API-37 device/emulator.
- Receiver tests used loopback HTTPS on the Fedora host, not a phone-to-PC LAN or two physical devices. No Fedora firewall, Wi-Fi outage/reconnect, mobile-data-plus-offline-Wi-Fi routing, or large real-photo responsiveness test was performed.
- Local/PC out-of-space behavior was fault-injected as write/receipt persistence failure. An actual full disk or filesystem power-loss test was not performed.
- Linux static symlink protection is tested. Concurrent hostile filesystem mutation is limited by path-based Deno calls; protect root/ancestors/state. Windows junction/reparse-point and fsync/hard-link behavior are unsupported, not tested guarantees.
- SAF providers can leave a visible incomplete document while writing, and there is no universal atomic rename/fsync contract. Ambiguous orphan conflicts retain staging and require resolving the conflicting provider document before retry. Unrecoverable originals can be explicitly exported from retained staging. Provider-specific offline behavior needs device checks.
- F-Droid submission, release signing, public source hosting, Fastlane screenshots/tags, and bit-for-bit reproducibility are deferred to release preparation. At implementation start the directory had no usable Git repository. Git was initialized later, and the completed source is now committed locally. No PR or public release was created.

## Manual device/LAN acceptance run

Use a working physical camera device, an on-device SAF root, and a PC root independent from receiver state. Use dummy photos only during this run.

1. **Gate and local folders:** fresh task/process → destination screen; no shutter before confirmation. Pick root, enter `Projects/Job A/Before`, then `Été/صور/قبل`; inspect exact nested names with another file manager. Make three captures in one folder, reopen it, make more; confirm unique filenames and intact originals. Verify long paths wrap and controls remain reachable in landscape and with large text.
2. **Session continuity:** rotate and background/foreground while saving; the confirmed session stays. Change folder, edit then cancel → previous destination. Confirm → new path. Close task/kill process → new path gate with suggestion only. Settings/pairing/browser remain accessible before confirmation.
3. **Offline standalone:** disable PC sync and all connectivity. Capture, preview, share/export; no network pending/failure indicator. Uninstall only after verifying permanent copies via another app; public photos survive, private staging does not.
4. **Local recovery:** revoke the selected grant, fill storage, use an offline provider, and kill during capture/copy/state update. Expect truthful Local save failed, retained recoverable staging, regrant-original-root/retry/export controls. A finalized copy must be adopted without duplicate files. Incomplete orphans must not replace occupied files.
5. **Pairing trust:** grant Android-17 LAN permission; scan PC QR offline, pair once, and verify expired/reused secrets/wrong token/certificate/identity fail. Test manual fingerprint entry and a verified address change. Review logs for leaked permanent tokens or secrets beyond intentional temporary pairing display.
6. **Live LAN:** enable Future photos only; old captures stay local. Explicitly select an existing folder to include it. Capture several photos while PC receives them automatically into exact relative paths. Use a Wi-Fi LAN without internet, including mobile data active; shutter remains responsive and transport does not require validated internet.
7. **Queue continuity:** cut Wi-Fi, change folders, capture more, reconnect. Each pending task must retain its original root/path/filename/receiver. Pause/resume sync; originals remain. Confirm a running transfer may commit after pausing, and no new transfer starts while paused.
8. **Crash/idempotency:** kill Android during upload; restart after lease expiry. Interrupt receiver while writing and after file publication; restart and retry a lost acknowledgement. Exactly one completed file and the correct durable receipt must result; partial files must not appear as committed photos.
9. **Actionable errors and replacement:** fill PC disk, revoke device credential, occupy a filename, and change certificate. Verify distinct failure instructions and explicit retry. Replace active receiver with outstanding tasks: require discard/keep decision and never silently redirect tasks. Select local folders explicitly if sending them to the new receiver.
10. **Load and security:** test maximum-sized JPEGs and a backlog, concurrent duplicate requests, ID/destination conflicts, symlink descendants, reserved names/traversal/encoded-separator attempts, offline runtime, and backup/device-transfer exclusions. Do not claim Windows support from Linux results.

## Optimized Android release — 2026-10-06

Android 1.0.1/versionCode 5 enables R8 and optimized resource shrinking with the standard Android optimization rules. Library consumer rules preserve CameraDatabase/CameraDatabase_Impl and the persistent UploadWorker name; no blanket keep/dontwarn rules were added.

Release build/lint and all 13 host tests pass. Native Android 16 instrumentation passes. The black-box optimized-APK check on the 16-KB emulator selects storage through the actual SAF picker, saves and verifies a JPEG, restarts the app and verifies settings/photo persistence: https://github.com/sigmaSd/folder-camera/actions/runs/37428464134/job/112153459739. The 4-KB optimized check also passes in run 37429882212 after bounded waits for transient null accessibility roots. Android 17 emulator platform failures remain documented separately.

Clean official F-Droid/JDK 21 builds and source/APK scans pass. A second independent clean build reproduces the developer-signed APK byte-for-byte; the full F-Droid build also downloads the public 1.0.1 reference and verifies both the binary comparison and allowed signing certificate. APK size is 2,742,454 bytes. Signature, production manifest, license assets and 16-KB ZIP/native ELF alignment pass.

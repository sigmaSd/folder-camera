# Verification and acceptance checklist

## Desktop receiver download link — Android 1.0.3, 2026-10-18

Settings → PC sync has a persistent Get desktop receiver button and brief installation/pairing instructions, visible before pairing. The link opens `https://sigmasd.github.io/folder-camera/#desktop-downloads`; the live website's destination anchor was verified.

Release source `4c981879c01c4b8e553e657a1ef2f9316f47603c` passes all 16 host tests, lint and [Android/receiver CI](https://github.com/sigmaSd/folder-camera/actions/runs/39141242116). The pinned official F-Droid image/JDK 21/Gradle 9.4.0 environment builds versionCode 7 with clean source and APK scans. A second clean build plus the developer signature reproduces identical signed bytes. The downloaded public APK also passes F-Droid's source/reference comparison and allowed-signer verification. Production manifest, legal assets, signature and 16-KB ZIP/ELF alignment checks pass. The F-Droid update checker detects 1.0.3 and generates its pinned code-7 build; metadata parsing and lint pass. [Native acceptance](https://github.com/sigmaSd/folder-camera/actions/runs/39141242121) is still running; earlier Android 16 results and the Android 17 emulator limitation are documented below.

## Camera, folder browsing and receiver switching — 2026-10-18

- Capture feedback uses CameraX's actual capture-start callback for a brief shutter blackout, button pulse and system-respecting haptic feedback. It does not resize the camera preview or claim the local save has completed.
- Paths has a dedicated scrolling list with search and subfolder navigation; the editable destination and Start camera action remain outside that list. The persistent cancel sentence is removed; the header camera action still returns to the confirmed session without applying a draft path.
- Folders adds current-folder search and a full-screen, fit-to-screen JPEG viewer with close/share/export. Closing restores the list position and search. Directory queries and image decoding run off the main thread; access and decode failures show visible messages.
- QR and manual pairing use one receiver replacement flow. Changing the address of the same PC still verifies its original certificate/identity; a different PC must be paired. Replacing a PC requires an explicit old-queue discard decision, preserves local photos and the sync setting, and never sends old tasks to the new PC. Renewed same-PC credentials/addresses release failed and deferred tasks.
- Transfer status is summarized once for the current PC, with delivered/waiting/sending/failed counts and an optional per-photo queue. Previous PCs and discarded tasks do not pollute current status.

Local validation: debug APK, optimized release APK, instrumentation APK, all 16 host tests and lint pass (0 errors; advisory warnings remain). New host regressions cover receiver replacement consent, stale receipt rejection after discard, same-PC credential/address renewal, and per-PC queue summary counts. Existing-folder import choices reset per PC, and explicit imports retain the receiver selected when the action was requested.

Native Android 16: all 11 instrumentation tests and the optimized APK capture/restart check pass on both 4-KB and 16-KB emulators ([matrix run](https://github.com/sigmaSd/folder-camera/actions/runs/39114992490)). The final application commit also passes the full native and optimized acceptance checks on Android 16 ([final application run](https://github.com/sigmaSd/folder-camera/actions/runs/39116037224), source `55420867e0cbfa54e88b60305390c901f0b9ccce`). The new tests exercise a 70-folder list, current-folder search, opening a photo below the viewport, restoring its scroll/search state, and receiver replacement confirmation. Native fixture screenshots were inspected.

The first 16-KB image run killed foreground instrumentation under guest memory pressure, so those test guests now use 6 GB instead of 3 GB; this is a CI image setting, not an app RAM requirement. Android 17 then exposed a separate credential-storage-locked startup race. Runner readiness now requires three consecutive checks with services available and user storage unlocked; manual CI dispatch can select one emulator configuration. The [targeted Android 17 run](https://github.com/sigmaSd/folder-camera/actions/runs/39115818708) exhausted the ten-minute readiness deadline: Android services and unlocked user storage never became ready. It did not execute native app tests, so Android 17 runtime validation remains blocked; this result is not counted as a pass.

The local API-34 emulator segfaults before boot with both SwiftShader and GPU/Vulkan disabled. Physical camera/haptic feel and actual two-PC LAN delivery remain manual checks.

Android 1.0.2/versionCode 6 was subsequently released from `2467113340075c6abb84b6c107b0e62387364b63` ([stable release](https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.2)). The exact release source passes [Android/receiver CI](https://github.com/sigmaSd/folder-camera/actions/runs/39121155018), 16 host tests and lint (0 errors, 47 advisory warnings). Both Android 16 variants pass 11 native tests and optimized capture/restart acceptance in [release acceptance run](https://github.com/sigmaSd/folder-camera/actions/runs/39121154712); the Android 17 job remains separate from those successful jobs. Clean official F-Droid image `sha256:9cb68105642ca4e7b295f0ceab10f069f5b3247dc18fa7c36046e9d81aa469a8`, JDK 21.0.12.1, Gradle 9.4.0 and fdroidserver 2.4.5 produce a source/APK-scan-clean build. A second clean source build plus copied developer signature is cryptographically valid and byte-identical. Production identity/signature/legal assets and 16-KB ZIP/native alignment pass. The published APK is 2,746,550 bytes with SHA-256 `c5ce8c83207d809adcbcc40715c096634935e6f5a60bbcae246fe5781fcb0bf8`; its public download matches. A further full F-Droid build downloads the public release APK, passes reference comparison and verifies the allowed production signer. The official F-Droid update checker detects the new tag and generates code 6 automatically. The separate upload-key AAB is prepared locally for a future Play account.

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

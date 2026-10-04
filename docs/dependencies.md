# Dependency and F-Droid readiness audit

Checked on 2026-10-04 against official documentation and resolved Maven/npm metadata. Direct dependencies are pinned, the Gradle wrapper has an official distribution SHA-256, and its wrapper JAR checksum was independently matched to the official Gradle checksum. Room schema is checked in. Receiver dependencies have integrity hashes in deno.lock.

| Component | Selected version | Basis |
|---|---|---|
| Gradle / AGP | 9.4.0 / 9.1.1 | Stable; AGP supports API 37 and built-in Kotlin |
| Kotlin Compose compiler / KSP | 2.2.10 / 2.3.12 | Matches AGP Kotlin; KSP supports built-in Kotlin source registration |
| Compose | BOM 2026.09.00 | Official stable BOM |
| CameraX / Room / WorkManager | 1.6.2 / 2.8.5 / 2.11.2 | Supported stable releases; newer stable releases may exist |
| OkHttp / ZXing | 4.12.0 / 3.5.3 | Supported pinned FOSS implementations |
| Robolectric | 4.17 | Test-only; Android SDK 34 host simulation |
| Deno / qrcode | 2.9.3 / 1.5.4 | Tested runtime; pinned MIT QR encoder |

The resolved runtime inventory is [android-runtime-dependencies.md](android-runtime-dependencies.md); the receiver lock/license inventory is [receiver-dependencies.md](receiver-dependencies.md). Original package notices are retained in `third_party/licenses`. Android's debug provider and all test libraries/fixtures are excluded from the release source set. AndroidX's native CameraX/Compose libraries come from standard source-available Maven artifacts; no opaque app-specific binary SDK was bundled.

Runtime dependency scan found no Google Play Services, Firebase, proprietary ML Kit, tracker, advertising, updater, proprietary crash reporter or online QR dependency. Camera uses CAMERA; LAN uses INTERNET, ACCESS_NETWORK_STATE and Android 17 ACCESS_LOCAL_NETWORK only on user-enabled optional operations. No all-files or location permission. Android backup/device-transfer rules exclude app metadata, credentials and staging. The permanent selected SAF tree remains outside app backup and uninstall.

F-Droid's current policy requires FOSS source/build dependencies and forbids proprietary runtime requirements. This project has no mandatory server/account/vendor or cloud endpoint and is intended to be compatible; admission is not guaranteed. Publication is not requested. A release still needs a public source repository, reviewed release name/application ID, real-device validation, release tags, Fastlane store metadata and an F-Droid build recipe. Bit-for-bit reproducibility has not been tested.

Sources:

- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/jetpack/androidx/versions/stable-channel
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/about/versions/12/backup-restore
- https://docs.deno.com/api/deno/file-system/
- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/docs/FAQ_-_App_Developers/

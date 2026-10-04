# New app: Folder Camera

Application ID: `io.github.sigmasd.foldercamera`
Author/publisher: sigmasd
License: GPL-3.0-or-later
Source: https://github.com/sigmasd/folder-camera
Website/privacy: https://sigmasd.github.io/folder-camera/
Reference APK: https://github.com/sigmasd/folder-camera/releases/download/v1.0.0/folder-camera-1.0.0.apk

Folder Camera is a native Kotlin/Compose folder camera. Capture works offline without a PC or account. Optional LAN transfer uses a FOSS Linux receiver, certificate-verified TLS, durable delivery receipts and no vendor/cloud backend. No advertising, analytics, Play Services, Firebase, proprietary QR service or licensing SDK.

The maintainer owns/approved this submission. Upstream Fastlane-compatible metadata, public release tag, full licenses/third-party notices and source are present. The standalone APK uses the developer-controlled signing key so reproducible F-Droid distribution can retain the same identity intended for Google Play. Google Play is planned as a paid download of the same GPL app; no proprietary runtime feature is required.

Validation performed locally in the official F-Droid buildserver image pinned to `sha256:9cb68105642ca4e7b295f0ceab10f069f5b3247dc18fa7c36046e9d81aa469a8`, using the current fdroidserver source and JDK 21:

- Metadata lint passes.
- Clean public-source build succeeds.
- Source and APK scans pass.
- Downloaded developer APK matches the independently rebuilt APK through signature-copy verification.
- Allowed app signing certificate SHA-256: `2e119cce85d89feb92aa7577835ed75d5fc3b48755fb12097a33535aec9cb17e`.
- Production APK signature/manifest/16-KB ZIP and native ELF checks pass.
- Android 16 native acceptance passes on both 4-KB and 16-KB images. Android 17 emulator validation has not passed: expanding userdata fixes installation, but emulator SurfaceFlinger/RegionSampling aborts repeatedly in mapper.ranchu; system services restart and the instrumentation process is killed under memory pressure. Zero app tests complete.

The MR adds only `metadata/io.github.sigmasd.foldercamera.yml`. English Fastlane descriptions, changelog, icon, feature graphic and two native screenshots are in the upstream source. The recipe pins full source commit `b169eb51a8f195c05051001c906ef4d4e90dedf5`, newer than v1.0.0 solely to include those assets and test/documentation changes. Production app sources/dependencies are unchanged, and the clean build from this exact commit passed comparison with the published v1.0.0 APK. The public tag has not been moved.

The APK is currently labelled an upstream release candidate while store preparation is completed. The official recipe is included with automatic stable-tag update detection. No private signing material is committed.


## Submission checklist

- [x] Author-authorized GPL-3.0-or-later app; inclusion policy reviewed; no non-free runtime services, trackers, advertisements or licensing SDK.
- [x] Contribution guide, metadata reference, templates and quick-start guide reviewed.
- [x] One app, public fork, separate submission branch, issue tracker and public author contact.
- [x] Only a valid LF YAML recipe is added; all English listing text and images remain upstream.
- [x] Full source hash pinned; tagged upstream release and automatic future tag updates configured.
- [x] Developer-signature reproducibility enabled and independently verified; current version only, no disabled builds.
- [x] APK is approximately 13 MB; retaining one universal reproducible upstream APK for this initial version. No external source repositories/submodules are needed.
- [x] GitLab pipeline passes and any report warnings are resolved or explained. Local official-buildserver lint/build/scans/reference verification pass. If GitLab requires account phone/card verification, please trigger the F-Droid FOSS runner instead.

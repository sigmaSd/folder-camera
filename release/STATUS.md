# Store publication status

Updated 2026-10-04 (Africa/Tunis). This is a factual checklist, not a claim that either store listing is live.

## Completed

- Public source: https://github.com/sigmasd/folder-camera
- Product/privacy/support site: https://sigmasd.github.io/folder-camera/
- Production ID io.github.sigmasd.foldercamera; debug is separate.
- In-app offline privacy policy, source/license access and adaptive icon.
- Signed developer APK and separate upload-key-signed AAB; private credentials kept outside Git with verified encrypted recovery backup.
- Production signature/manifest/16-KB ZIP/native ELF checks passed.
- Official Google bundletool validation and JAR signature verification passed for the AAB.
- Android/receiver CI and 13 Android host tests/16 receiver tests passed.
- Eight native Android 16 tests passed on both 4-KB and 16-KB emulator images.
- Official F-Droid metadata lint, clean source build and source/binary scans passed.
- Official F-Droid independently rebuilt v1.0.0 and verified it against the downloaded upstream APK with the allowed developer signing certificate.
- Public signed upstream candidate: https://github.com/sigmasd/folder-camera/releases/tag/v1.0.0 (marked prerelease).
- Fastlane-compatible title, descriptions, changelog, icon, feature graphic and two visually checked native UI screenshots prepared.
- Main CI: https://github.com/sigmasd/folder-camera/actions/runs/37225852712
- Native Android 16 results: https://github.com/sigmasd/folder-camera/actions/runs/37225852701 (both API 36 jobs pass; API 37.2 cannot complete instrumentation).
- F-Droid submission: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190 — open for maintainer review. One recipe file, branch `folder-camera`, commit `21579bffd0a9aac3b7657ddeb9614df811a3fd3a`; listing assets stay upstream.
- Exact pinned source `b169eb51a8f195c05051001c906ef4d4e90dedf5` clean build, source/APK scans, lint and reference-signature reproduction passed.
- GitLab pipeline: https://gitlab.com/sigmaSd/fdroiddata/-/pipelines/2911353093 — all nine jobs passed, including full build and APK check.
- Thirteen report entries (required permissions, no R8 marker, and informational signing/ABI/assets notices) are explained in the MR comment: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190#note_3951938192

## Validation blocker

- Android 17 / API 37.2: expanding userdata to 8 GB fixed installation (6.8 GB free). The emulator then repeatedly aborts SurfaceFlinger/RegionSampling in mapper.ranchu (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`), restarts system services and kills the instrumentation app under memory pressure. Zero app tests complete. Graphics compatibility and a stable API-37 emulator/device still need validation; no API 37 pass is claimed.

## External requirements

- Official F-Droid maintainer review and build/publication. Submission is filed; admission is not yet confirmed.
- Verified SARL organization Play account: legal identity/D-U-N-S/website, owner terms/registration payment, merchant/bank verification and release access.
- Owner decision on one-time Play price and final public company support email/phone/legal details.
- Owner must copy signing-backup.fcbackup and recovery-password.txt to separate safe offline locations. Do not send key passwords or banking/identity documents in chat or Git.

The official F-Droid merge request is filed. No Google Play upload or production rollout has been filed yet. The current GitHub APK is an explicitly marked release candidate. Official F-Droid publication depends on its maintainers accepting the merge request; Play publication depends on Google review and the account gates above.

## Desktop receiver release — 2026-10-05

- Published all six native installers: https://github.com/sigmaSd/folder-camera/releases/tag/receiver-v1.0.0.
- Native source/tests, rendered GUI startup/clean exit and actual installer checks pass on Linux x64/ARM64, Windows x64/ARM64 and macOS Intel/Apple Silicon: https://github.com/sigmaSd/folder-camera/actions/runs/37270255254.
- Twenty receiver tests pass on each target; cached CLI HTTPS/restart/revocation smoke and local native Linux GUI smoke pass.
- Windows installs per user to avoid the reproduced upstream WebView2 Program Files cache failure. ARM64 MSI schema is corrected to Installer 5.0. macOS closes its window and naturally drains the runtime rather than exiting it abruptly.
- SHA256SUMS and source/CI provenance accompany the binaries; published asset digests were verified against the accepted artifacts. No Android APK changes were made for this receiver release.
- README and product website include native Android fixture screenshots and a labelled desktop interface preview. The F-Droid description now embeds its two native Android screenshots; its recipe and pinned source remain unchanged.
- The owner tested the receiver and approved promotion to stable on 2026-10-05. Desktop trusted publisher signing/Apple notarization remains pending. Linux requires WebKitGTK 4.1/GTK 3 (Ubuntu 24.04 tested); Windows requires WebView2. Automatic Android mDNS address rediscovery remains deferred as specified.

## F-Droid review correction — 2026-10-06

- linsui requested R8 and closed !51190 for not using the official template. The exact App inclusion checklist is now used; the owner's disclosure and screenshots are preserved.
- Android 1.0.1/versionCode 5 enables R8 and resource shrinking. Signed APK size is 2,742,454 bytes, down from 13,097,794. Published at https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.1.
- Recipe retains only the latest version; source ba0ed7f4c503f0594e8b5af738d193709cbd23aa; fork commit 9fea7c2e49c6d8aa7467a47d44d003f9e38d3226.
- Release build/lint and thirteen host tests pass. Native Android 16 tests and optimized-APK capture/restart check pass on the 16-KB image. The 4-KB optimized capture/restart check also passes after bounded accessibility synchronization; Android 17 emulator remains blocked by known platform failures.
- Official F-Droid source/APK scans, independent byte-identical reproduction, full published-reference comparison/allowed signer, signature/identity/licenses and 16-KB alignment all pass. New GitLab submission pipeline is pending.
- Signing identity and existing v1.0.0 tag/assets are retained. The 1.0.1 AAB is prepared locally for a future owner-verified Play account, without upload.

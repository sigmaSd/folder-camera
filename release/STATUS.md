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

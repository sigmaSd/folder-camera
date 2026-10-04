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
- Main CI: https://github.com/sigmasd/folder-camera/actions/runs/37224921023
- Native Android 16 results: https://github.com/sigmasd/folder-camera/actions/runs/37224921037 (both API 36 jobs pass; API 37.2 fails before test execution).

## In progress

- Android 17 maintenance-image native validation: test APK installation reports "Requested internal only, but not enough space", with system media storage mount errors. Retrying once with an explicit 8-GB userdata partition and preserving filesystem diagnostics. No API 37 pass is claimed.
- F-Droid merge request body and recipe prepared under release/fdroid.

## External requirements

- GitLab authenticated session to fork fdroiddata and submit the official merge request. GitLab sign-in is open in the browser; no account credentials were entered by the agent.
- Verified SARL organization Play account: legal identity/D-U-N-S/website, owner terms/registration payment, merchant/bank verification and release access.
- Owner decision on one-time Play price and final public company support email/phone/legal details.
- Owner must copy signing-backup.fcbackup and recovery-password.txt to separate safe offline locations. Do not send key passwords or banking/identity documents in chat or Git.

No Google Play upload, production rollout or official F-Droid merge request has been filed yet. The current GitHub APK is an explicitly marked release candidate. Official F-Droid publication depends on its maintainers accepting the merge request; Play publication depends on Google review and the account gates above.

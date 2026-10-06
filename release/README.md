# Release preparation

Target identity: Folder Camera, publisher sigmasd, application ID `io.github.sigmasd.foldercamera`. Current Android 1.0.1/versionCode 5 enables R8 and resource shrinking. The signed APK passes clean F-Droid source/reference comparison and optimized-APK runtime checks; store publication remains subject to review. Debug uses `.debug` and keeps the existing prototype app separate.

Distribution: same GPL-3.0-or-later app, free on official F-Droid and one-time paid download on Play. No in-app billing SDK, proprietary license checks or runtime Play dependency. Price/company contacts remain owner decisions.

## Signing

Private app/upload PKCS12 files and credentials are outside this repository at `~/.local/share/folder-camera/signing`. Public certificates/fingerprints here are safe to distribute. The verified AES-GCM/Scrypt recovery backup and its password must be copied by the owner to separate safe offline locations. Never place private keys/passwords in Git, tickets, store descriptions or screenshots.

`scripts/create_signing_keys.py` creates keys once and refuses accidental replacement. `scripts/build_release.py` signs APK with the app key and AAB with the separate upload key. Use the app key for Play App Signing import (via Google's PEPK tool and Console-provided encryption key); do not let Google generate a different app-signing identity if cross-store upgrades are required. No private key has been uploaded to Google.

`scripts/verify_release.py APK --sdk SDK_PATH` checks signature, production manifest, legal assets, 16-KB ZIP and 64-bit native ELF alignment. Official F-Droid build/scan/reference verification has passed for v1.0.0. `scripts/verify_reproducibility.py` separately confirms that copying the developer signature onto the independently built unsigned APK yields byte-identical signed bytes. Compatible upgrades from a Google-served installation remain unclaimed until tested.

Use `scripts/build_release.py --unsigned-apk CANONICAL_APK` to sign the verified F-Droid/JDK-21 build. APK signing uses SDK apksigner with alignment preserved; re-signing through Gradle changes the ZIP and breaks reproduction. The signed candidate is public at https://github.com/sigmasd/folder-camera/releases/tag/v1.0.0; checksums are in artifact-checksums.json.

## Publication gates

- Public GitHub source/CI and privacy URL operational.
- Complete emulator/device acceptance, including API 37 and 16-KB image; store screenshots generated using fixtures only.
- Clean F-Droid build/scan/metadata and reproducible APK comparison.
- Official F-Droid MR filed and all nine CI jobs passed: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190; maintainer review/publication pending.
- Verified SARL organization Play account, owner terms/payment/merchant verification.
- Final price, support mailbox/phone, legal publisher details and store declarations reviewed.
- APK/AAB and candidate listings reviewed before public store release.

Historical prototype commits were rewritten before public publication to remove the static TLS key fixture. An ignored local bundle preserves the old history; public master contains no private-key fixture. TLS tests now generate short-lived credentials at runtime.

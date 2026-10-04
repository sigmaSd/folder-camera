# Release preparation

Target identity: Folder Camera, publisher sigmasd, application ID `io.github.sigmasd.foldercamera`. Current 1.0.0/versionCode 4 artifacts are release candidates until device/store validation is complete. Debug uses `.debug` and keeps the existing prototype app separate.

Distribution: same GPL-3.0-or-later app, free on official F-Droid and one-time paid download on Play. No in-app billing SDK, proprietary license checks or runtime Play dependency. Price/company contacts remain owner decisions.

## Signing

Private app/upload PKCS12 files and credentials are outside this repository at `~/.local/share/folder-camera/signing`. Public certificates/fingerprints here are safe to distribute. The verified AES-GCM/Scrypt recovery backup and its password must be copied by the owner to separate safe offline locations. Never place private keys/passwords in Git, tickets, store descriptions or screenshots.

`scripts/create_signing_keys.py` creates keys once and refuses accidental replacement. `scripts/build_release.py` signs APK with the app key and AAB with the separate upload key. Use the app key for Play App Signing import (via Google's PEPK tool and Console-provided encryption key); do not let Google generate a different app-signing identity if cross-store upgrades are required. No private key has been uploaded to Google.

`scripts/verify_release.py APK --sdk SDK_PATH` checks signature, production manifest, legal assets, 16-KB ZIP and 64-bit native ELF alignment. F-Droid must reproduce the unsigned upstream APK before distributing the developer signature; compatible store upgrades remain unclaimed until tested.

## Publication gates

- Public GitHub source/CI and privacy URL operational.
- Complete emulator/device acceptance, including API 37 and 16-KB image; store screenshots generated using fixtures only.
- Clean F-Droid build/scan/metadata and reproducible APK comparison.
- GitLab account access for the official fdroiddata merge request.
- Verified SARL organization Play account, owner terms/payment/merchant verification.
- Final price, support mailbox/phone, legal publisher details and store declarations reviewed.
- APK/AAB and candidate listings reviewed before public store release.

Historical prototype commits were rewritten before public publication to remove the static TLS key fixture. An ignored local bundle preserves the old history; public master contains no private-key fixture. TLS tests now generate short-lived credentials at runtime.

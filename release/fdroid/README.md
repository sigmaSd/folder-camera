# Official F-Droid distribution

The app is [listed on official F-Droid](https://f-droid.org/en/packages/io.github.sigmasd.foldercamera/). The original [submission MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190) was merged on 2026-10-08; version 1.0.1/versionCode 5 was published on 2026-10-12.

The official recipe uses `AutoUpdateMode: Version` and `UpdateCheckMode: Tags ^v[0-9.]+$`. A stable Android tag with a higher version code is detected automatically; desktop receiver tags are excluded. The public developer-signed APK must remain at `https://github.com/sigmasd/folder-camera/releases/download/v%v/folder-camera-%v.apk`. F-Droid rebuilds it and verifies the production signing certificate before publication. Detection, a successful build and appearance in the store are separate steps.

Android 1.0.4/versionCode 8 is [published upstream](https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.4). The F-Droid update checker should detect the tag and generate a build pinned to `6864045514c54f3b73277f4ef32eedf7c4f8a0d1`, preserving the earlier recipes. Official build/index publication is pending. This release adds the swipeable, newest-first photo viewer with "Open with", pairing behind a VPN that can't be bypassed, and specific pairing errors. Release verification is recorded in [STATUS.md](../STATUS.md); the recipe snapshot is [io.github.sigmasd.foldercamera.yml](io.github.sigmasd.foldercamera.yml).

The original submission description is preserved in `merge-request.md` as history. Do not reopen that merged MR for version updates. Preserve the unrelated local `config.yml` modification in `.work/fdroid/data` and never publish private signing files or phone data.

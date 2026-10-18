# Official F-Droid distribution

The app is [listed on official F-Droid](https://f-droid.org/en/packages/io.github.sigmasd.foldercamera/). The original [submission MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190) was merged on 2026-10-08; version 1.0.1/versionCode 5 was published on 2026-10-12.

The official recipe uses `AutoUpdateMode: Version` and `UpdateCheckMode: Tags ^v[0-9.]+$`. A stable Android tag with a higher version code is detected automatically; desktop receiver tags are excluded. The public developer-signed APK must remain at `https://github.com/sigmasd/folder-camera/releases/download/v%v/folder-camera-%v.apk`. F-Droid rebuilds it and verifies the production signing certificate before publication. Detection, a successful build and appearance in the store are separate steps.

Android 1.0.3/versionCode 7 is [published upstream](https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.3). The F-Droid update checker detects the tag and automatically generates a build pinned to `4c981879c01c4b8e553e657a1ef2f9316f47603c`, preserving the earlier 1.0.1 recipe. Official build/index publication is pending. This release adds the desktop receiver download link and includes the camera, browser and receiver pairing fixes from 1.0.2. Release verification is recorded in [STATUS.md](../STATUS.md); the recipe snapshot is [io.github.sigmasd.foldercamera.yml](io.github.sigmasd.foldercamera.yml).

The original submission description is preserved in `merge-request.md` as history. Do not reopen that merged MR for version updates. Preserve the unrelated local `config.yml` modification in `.work/fdroid/data` and never publish private signing files or phone data.

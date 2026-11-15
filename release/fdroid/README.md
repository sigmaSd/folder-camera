# Official F-Droid distribution

The app is [listed on official F-Droid](https://f-droid.org/en/packages/io.github.sigmasd.foldercamera/). The original [submission MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190) was merged on 2026-10-08; version 1.0.1/versionCode 5 was published on 2026-10-12.

The official recipe uses `AutoUpdateMode: Version` and `UpdateCheckMode: Tags ^v[0-9.]+$`. A stable Android tag with a higher version code is detected automatically; desktop receiver tags are excluded. The public developer-signed APK must remain at `https://github.com/sigmasd/folder-camera/releases/download/v%v/folder-camera-%v.apk`. F-Droid rebuilds it and verifies the production signing certificate before publication. Detection, a successful build and appearance in the store are separate steps.

Android 1.0.6/versionCode 10 is [published upstream](https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.6). The F-Droid update checker should detect the tag and generate a build pinned to `613b293d486b145070c298660451c29c7d7b64a1`, preserving the earlier recipes. Official build/index publication is pending. This release makes the back button in the camera return to the folder choice. Release verification is recorded in [STATUS.md](../STATUS.md); the recipe snapshot is [io.github.sigmasd.foldercamera.yml](io.github.sigmasd.foldercamera.yml).

The original submission description is preserved in `merge-request.md` as history. Do not reopen that merged MR for version updates. Preserve the unrelated local `config.yml` modification in `.work/fdroid/data` and never publish private signing files or phone data.

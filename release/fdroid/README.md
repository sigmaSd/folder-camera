# Official F-Droid submission

The source recipe and merge-request body in this directory are prepared and validated. No merge request has been filed yet; GitLab authentication is required.

Submit to https://gitlab.com/fdroid/fdroiddata after forking it under the maintainer account. A local submission branch is already prepared in `.work/fdroid/data` (branch `folder-camera-submission`, commit `30f23b396`). Its unrelated local config.yml change is excluded from the commit. If rebuilding the submission checkout, create a branch, copy `io.github.sigmasd.foldercamera.yml` into `metadata/`, and copy `fastlane/metadata/android/en-US/` into `metadata/io.github.sigmasd.foldercamera/en-US/`. Commit only those app files. Use `merge-request.md` as the request description and record its URL in ../STATUS.md after filing.

The English sidecars include two real native UI screenshots with synthetic fixture data. They are needed for the first submission because the stable-shaped v1.0.0 source tag predates screenshots. The tag has not been moved. Future tagged releases can import upstream Fastlane metadata automatically.

The app was rebuilt from the public v1.0.0 tag inside the official buildserver image and passed source scanning, APK scanning, allowed signing certificate checks, and the upstream reference APK comparison. Maintainers still perform their own review/build and decide admission; local success does not imply an official listing.

# Official F-Droid submission

MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190
Fork branch: sigmaSd/fdroiddata, folder-camera
Updated submission commit: 9fea7c2e49c6d8aa7467a47d44d003f9e38d3226
Android: 1.0.1 / versionCode 5
Pinned source: ba0ed7f4c503f0594e8b5af738d193709cbd23aa
Reference: https://github.com/sigmasd/folder-camera/releases/download/v1.0.1/folder-camera-1.0.1.apk

On 2026-10-06 linsui requested R8 and closed the MR because the official template was not followed. The earlier custom checklist was replaced using GitLab's actual App inclusion template, with its wording preserved. The owner's introduction/disclosure and screenshots remain. R8 code optimization and resource shrinking are enabled. The approximately 2.8-MB universal APK retains the signing identity; only the latest version is in the recipe.

Local release build/lint and all thirteen host tests pass. Native Android 16 instrumentation passes, and the actual optimized APK selects a SAF folder, saves a JPEG, and preserves settings/photos across restart on both the 4-KB and 16-KB emulators. Clean official F-Droid/JDK 21 source builds/scans pass. A fresh independent source rebuild with the copied developer signature is byte-identical; full F-Droid download/reference/allowed-signer verification also passes. Signature, production identity, licenses and 16-KB alignment pass. Android 17 emulator testing remains blocked by the documented platform renderer/service failures.

The corrected MR has been reopened; all nine jobs pass in pipeline 2916670653: https://gitlab.com/sigmaSd/fdroiddata/-/pipelines/2916670653. The old nine-job passing pipeline 2911353093 validates only the previous 1.0.0 recipe. The corrected R8 recipe now passes all nine checks.

Only metadata/io.github.sigmasd.foldercamera.yml is submitted. Descriptions/changelog/images remain upstream. The unrelated local config.yml change in .work/fdroid/data is preserved and excluded. Do not push the superseded folder-camera-submission branch or its listing sidecars.

Maintainers decide admission. A passing build and a corrected MR do not mean an official listing exists. Preserve the owner's disclosure on future edits. merge-request.md records the current description.

# Official F-Droid submission

Filed: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190
Pipeline: https://gitlab.com/sigmaSd/fdroiddata/-/pipelines/2911353093
Fork branch: sigmaSd/fdroiddata, folder-camera
Submission commit: 21579bffd0a9aac3b7657ddeb9614df811a3fd3a
CI: all nine jobs pass, including full build and APK check.
Report notes: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51190#note_3951938192

The MR adds only metadata/io.github.sigmasd.foldercamera.yml. All English listing text, icon, feature graphic, changelog and native fixture screenshots remain in the upstream fastlane directory, following the App inclusion template. No listing sidecars are submitted to fdroiddata.

The recipe pins full upstream source commit b169eb51a8f195c05051001c906ef4d4e90dedf5. This commit includes the screenshots added after the v1.0.0 tag. Production app sources and build dependencies are unchanged. A clean build from this exact commit in the official F-Droid buildserver image passes source/APK scans, allowed signing certificate checks and reproducibility comparison against the existing public v1.0.0 APK. The public tag has not been moved. Future tagged versions use automatic updates.

The local checkout is .work/fdroid/data. Its unrelated local config.yml change is excluded from the submission commit. The older unsubmitted folder-camera-submission branch with sidecars is superseded and must not be pushed.

Maintainers still perform review/build and decide admission. A filed MR and local verification do not imply an official app listing. The owner added a personal introduction to the live MR description; preserve it in any future edits. merge-request.md records the generated submission text, and report-notes.md records the posted findings explanation.

Record any subsequent review changes and the final admission/publication result in ../STATUS.md.

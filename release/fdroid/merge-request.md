## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. You can update git submodules without opening an MR in this repo and the submodule is covered by our scanner.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). We'll use your signature for improved security/reliability, also allowing users to switch between different channels. Do note that if you don't enable reproducible build then the apk will be signed with our key so you can't enable it later. If you can't enable this, please add the reasons here.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller.
* [x] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.


## App and review changes

Application ID: `io.github.sigmasd.foldercamera` · GPL-3.0-or-later · Publisher: sigmasd.
Source: https://github.com/sigmaSd/folder-camera
Website/privacy: https://sigmasd.github.io/folder-camera/

R8 code optimization and resource shrinking are enabled in [Android 1.0.1](https://github.com/sigmaSd/folder-camera/releases/tag/v1.0.1), versionCode 5. The signed universal APK is 2,742,454 bytes (about 2.8 MB), compared with 13.1 MB in 1.0.0. The recipe now retains only the latest version and pins full source commit `ba0ed7f4c503f0594e8b5af738d193709cbd23aa`.

Validation: release builds/lint and all 13 host tests pass; native Android 16 instrumentation passes; the actual optimized APK selects a SAF folder, captures a JPEG and retains settings/photos across restart on both Android 16 4-KB and 16-KB emulators ([latest runtime checks](https://github.com/sigmaSd/folder-camera/actions/runs/37429882212)). The official pinned F-Droid/JDK 21 clean source build and source/APK scans pass. A fresh independent rebuild reproduces the developer-signed bytes exactly; F-Droid also downloads the public 1.0.1 reference and verifies the comparison and allowed signer. Signature, production manifest, bundled licenses and 16-KB alignment checks pass. Android 17 emulator validation remains blocked by the previously documented system renderer/service failures.

[Reference APK](https://github.com/sigmaSd/folder-camera/releases/download/v1.0.1/folder-camera-1.0.1.apk) · APK SHA-256: `a2b25596a2d1325de208017b6f82792bffabd7dee23cc3f174d4063b5bd92548`. The developer certificate remains `2e119cce85d89feb92aa7577835ed75d5fc3b48755fb12097a33535aec9cb17e`.

Only `metadata/io.github.sigmasd.foldercamera.yml` is submitted. Fastlane descriptions, changelog, icon and images stay upstream. There are no external repos/submodules or related RFP issues. The small universal APK does not need ABI splitting. All nine jobs pass in [pipeline 2916670653](https://gitlab.com/sigmaSd/fdroiddata/-/pipelines/2916670653), including source build, APK/source checks, formatting, schema and lint. The MR is reopened for maintainer review.

## Current report findings

The 13 findings are expected permissions and informational evidence. CAMERA supports capture and local QR scanning. INTERNET, ACCESS_NETWORK_STATE and ACCESS_LOCAL_NETWORK support explicitly enabled LAN sync; standalone capture is offline, and Android 17 LAN permission is requested at pairing/sync. WAKE_LOCK, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE come from AndroidX WorkManager for the durable queue. DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is AndroidX receiver protection. The remaining informational entries report the verified developer signer, small universal APK/ABIs, full-mode R8 9.1.31 marker, upstream Fastlane assets and reproduced reference binary. The previous missing-R8 concern is resolved.

## Author introduction and disclosure

Hi I sent sol 6.1 to upload this apk, the rest is written by it, I didn't check though what fdroid AI policy is, if this is not ok feel free to close

The app is useful, the core idea:
- select a folder and photo gets saved immediatly to it
- and you can rapidly change folders
this allows organizing photos quickly  like having "Home" photos "Bills" photos etc
- also it has another feature where it can be connected to a local pc (lan) and it syncs the photo directly to the pc

## Screenshots

Native Android captures with generated sample folders/photos. These images are already included in the pinned upstream Fastlane assets.

<p>
<img src="https://raw.githubusercontent.com/sigmaSd/folder-camera/b169eb51a8f195c05051001c906ef4d4e90dedf5/fastlane/metadata/android/en-US/images/phoneScreenshots/1-paths.png" width="260" alt="Android folder and path selection">
<img src="https://raw.githubusercontent.com/sigmaSd/folder-camera/b169eb51a8f195c05051001c906ef4d4e90dedf5/fastlane/metadata/android/en-US/images/phoneScreenshots/2-settings.png" width="260" alt="Android local storage and optional PC sync settings">
</p>
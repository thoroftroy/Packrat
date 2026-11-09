# Packrat for Android

An independent Android project from Wonderdesk by Rednave. This is the existing Android preview, not a full port of the former Linux application.

## Build

Install JDK 17 or 21 and Android SDK 35, then set `ANDROID_HOME` or configure `sdk.dir` in an untracked `local.properties`. Run:

```sh
./gradlew :packrat:assembleDebug
```

The debug APK is in `packrat/build/outputs/apk/debug/`. It is not a production-signed release. Debug builds use your local Android debug key; they cannot update an existing installation signed with a different key. Back up any important app data before changing signing keys or uninstalling. The application ID and version are preserved from the Wonderdesk Android monorepo. `versions.json` is a local snapshot; change the relevant app version there when releasing. The catalog and ecosystem version under `shared/src/main/assets/wonderdesk/` are also snapshots, not a live service.

## Appearance

Aqua Bliss is the default. Open About to choose Aqua Bliss, Bliss, Dark Bliss, or Super Bliss independently in this app. These are initial Compose adaptations inspired by BlissPlayer's color and material direction; they are not yet exact ports of its typography, animations, dialogs, or every control. Existing feature and platform limitations are described in the app's About screen. Artwork may still be temporary.

The `shared` module is vendored so this repository builds without the monorepo or other Wonderdesk repositories. Changes to shared code must currently be propagated to the other repositories manually. Third-party license notices are in `shared/src/main/assets/licenses/`.

## Publishing privately

In GitHub Desktop choose **File → Add local repository**, select this folder, review the files and create an initial commit. Choose **Publish repository** and ensure **Keep this code private** is checked before publishing. No remote has been configured and nothing is uploaded automatically.

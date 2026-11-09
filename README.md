# Packrat for Android

An independent Android project from Wonderdesk by Rednave. This is the existing Android preview, not a full port of the former Linux application.

Inspect, create, verify, and extract supported ZIP archives. The archive list includes counts, uncompressed size, and a case-insensitive path filter, plus path/size sorting; filtering and sorting do not change what is extracted.

## Build

Install JDK 17 or 21 and Android SDK 35, then set `ANDROID_HOME` or configure `sdk.dir` in an untracked `local.properties`. Run:

```sh
./gradlew :packrat:assembleDebug
```

The debug APK is in `packrat/build/outputs/apk/debug/`. It is not a production-signed release. Debug builds use your local Android debug key; they cannot update an existing installation signed with a different key. Back up any important app data before changing signing keys or uninstalling. The application ID is preserved from the Wonderdesk Android monorepo. `versions.json` is a local snapshot; change the relevant app version there when releasing. The catalog and ecosystem version under `shared/src/main/assets/wonderdesk/` are also snapshots, not a live service.

## Appearance

Aqua is the default. Open About to choose Aqua, Bliss, Dark Bliss, or Super and adjust motion independently in this app. Aqua and Super have ambient motion and tap bursts when Full motion and Android animations are enabled; Reduced uses smaller bursts with no ambient drift, and Off is still. Type now uses the owner's Bliss, Aqua and Super fonts (Dark Bliss uses the system font), dialogs are themed per look, and Aqua/Super show owner brand art in the header. These Compose adaptations are not yet exact ports of BlissPlayer's dialogs, click effects, or every control. Existing feature and platform limitations are described in the app's About screen. Artwork may still be temporary, but the launcher icon is owner-supplied (rasterized from the Wonderdesk delivery SVG).

The `shared` module is vendored so this repository builds without the monorepo or other Wonderdesk repositories. Changes to shared code must currently be propagated to the other repositories manually. Third-party license notices are in `shared/src/main/assets/licenses/`.

## Publishing privately

In GitHub Desktop choose **File → Add local repository**, select this folder, review the files and create an initial commit. Choose **Publish repository** and ensure **Keep this code private** is checked before publishing. No remote has been configured and nothing is uploaded automatically.

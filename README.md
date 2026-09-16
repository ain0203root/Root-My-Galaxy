# Root My Galaxy

<img width="108" height="108" alt="sprout_icon_108" src="https://github.com/user-attachments/assets/2ba0e360-0876-489c-b256-f75df7589785" />


Root My Galaxy is a one-click installer for explicitly
supported Samsung model and kernel combinations. The application itself is kept separate
from device offsets, native exploit payloads, and KernelSU build artifacts.


[Latest release](https://github.com/BuSung-dev/Root-My-Galaxy/releases)

The device feed and native payloads are maintained in
[Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads).

## Application

<img width="200" src="https://github.com/user-attachments/assets/da6d0a0a-e5aa-41c3-9b38-7ec01c08c3bd" />
<img width="200" src="https://github.com/user-attachments/assets/a9142c17-4e2f-4b18-8c03-fe17e531aa3a" />
<img width="200" src="https://github.com/user-attachments/assets/7af17f0c-7827-47c8-ab23-a985a019e972" />

The app selects a payload whose model list and three-part kernel version match
the phone. For example, `6.6.98-android15-8-...` matches `6.6.98`. Advanced
mode filters the catalog by both values and allows manual selection with model
and kernel-version warnings.

## Build

Requirements:

- JDK 21
- Android SDK 37 (`platforms;android-37.0`)
- Android NDK 28 or newer
- CMake 3.22.1

Point Gradle at your SDK by setting `sdk.dir` in `local.properties`, or export
`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) before building.

### Linux / macOS

```bash
# One-time setup (adjust the SDK path as needed)
export JAVA_HOME="$JAVA_HOME"        # e.g. /usr/lib/jvm/java-21-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager --install "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "cmake;3.22.1" "ndk;28.0.13004108"

./gradlew :app:assembleDebug
```

### Windows

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Payload sources

Advanced mode adds a **Payload sources** entry under Settings. It lists the GitHub
`owner/repository` and branch of every catalog the app may use, with a checkbox per entry to
enable or disable it and a delete action to drop it. The built-in feed
([Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)) is the
default entry and can be restored with one button, so pointing the list at a testing branch
for payloads that are not upstream yet does not cost you the official catalog.

Each enabled source is fetched on its own: its branch is resolved to a commit, that commit's
`support/targets-v3.json` is read, and every target is pinned to that commit and tagged with
the source it came from. Sources therefore do not shadow one another — when two of them offer
the same payload id, both appear in the selection sheet with their source underneath, and a
source that is unreachable or malformed is reported there and contributes nothing while the
others keep working. Installation only fails when no enabled source yields any target.

Payloads are cached per source, so the same payload id from two sources never shares a
download directory.

A target may set `"requiresFreshP0Session": true` in the manifest. Payloads built for a
device whose P0 page address is only valid for the attempt that leaked it must not be
handed the app's cached offset or its attempt/timeout overrides, so marked targets run one
payload-native attempt per run, ignore the cached slide offset, and are left to their own
internal retry pacing — the run's overall deadline still applies. The flag defaults to
false, and eight profiles in the official feed already set it.

## Local payload

Advanced mode also adds a **Local payload** entry, for testing an exploit `.so` built on this
device against a target the feed does not cover yet. The file is copied into app storage when
you pick it, not referenced by document URI: a run can be started by the boot service, where an
activity's grant on a picked document does not exist, and a persisted URI grant can be revoked
or its provider uninstalled long after the payload was chosen. Importing validates the file —
`.so` name, a 16 MiB cap, and an ELF header read from the copy — and a rejected file leaves the
previously imported payload in place, so a bad pick cannot leave the next run without an
exploit.

While a payload is imported, every run uses it in place of the downloaded exploit; KernelSU
still comes from the source matched to this device, because importing an exploit says nothing
about which `ksud` this kernel needs. The run log names the imported file, and **Remove
payload** returns to downloading the exploit from the enabled sources. Nothing else changes: the
same profile resolution, the same verification of the KernelSU artifact.

An artifact may also carry `"verifySize": false` in the manifest. That is for a source whose
declared size for an artifact is not trustworthy, and it is deliberately per artifact rather
than an app-wide switch: it is the only form of that escape hatch that cannot silently turn off
size verification for every other payload from every source at once. It defaults to true.

## Signing

`assembleRelease` needs the repository release key and fails instead of producing an
unsigned APK. Locally, create the gitignored `keystore/keystore.properties`:

```properties
storeFile=keystore/rootmygalaxy-ci.jks
storeType=PKCS12
storePassword=...
keyAlias=rootmygalaxy
keyPassword=...
```

CI provides the same values as environment variables from repository secrets:
`KEYSTORE_BASE64` (the keystore, base64-encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. Both workflows verify the built APK against the key before publishing, so a
silently mis-signed artifact fails the run.

Because every build shares one key, APKs from the `CI Build` pre-releases and from tagged
`Release Build` releases update over each other without uninstalling. Upstream's app is
signed differently, so switching from it needs one uninstall.

## Releases

- `CI Build` publishes a pre-release per run: tag `ci-<version>-<run number>`, marked as a
  pre-release so it never becomes the repository's "Latest" release.
- `Release Build` publishes the tagged releases it manages (`v<version>`).

Use only on devices you own or are explicitly authorized to test.

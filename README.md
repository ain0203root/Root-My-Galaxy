# Root My Galaxy

<img width="108" height="108" alt="sprout_icon_108" src="https://github.com/user-attachments/assets/2ba0e360-0876-489c-b256-f75df7589785" />


Root My Galaxy is a one-click installer for explicitly
supported Samsung model and kernel combinations. The application itself is kept separate
from device offsets, native exploit payloads, and KernelSU build artifacts.


[Latest release](https://github.com/BuSung-dev/Root-My-Galaxy/releases)

The device feed and native payloads are maintained in
[Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads).

## Application


<img width="200" alt="KakaoTalk_20260718_170922353" src="https://github.com/user-attachments/assets/3f562ea4-8c39-4ade-bfd3-93eea1a1cc24" />
<img width="200" alt="KakaoTalk_20260718_171127319" src="https://github.com/user-attachments/assets/8dde0443-12cf-4058-ba76-0337aefb92a0" />
<img width="200" alt="KakaoTalk_20260718_171030202" src="https://github.com/user-attachments/assets/f656e8af-60a6-4fcb-a3db-d4232bede613" />

The app selects a payload whose model list and three-part kernel version match
the phone. For example, `6.6.98-android15-8-...` matches `6.6.98`. Advanced
mode filters the catalog by both values and allows manual selection with model
and kernel-version warnings.

## Build

Requirements:

- Android Studio JBR 21
- Android SDK 37
- Android NDK 28 or newer
- CMake 3.22.1

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

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

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Because every build shares one key, APKs from the `CI Build` pre-releases and from tagged
`Release Build` releases update over each other without uninstalling. Upstream's app is
signed differently, so switching from it needs one uninstall.

## Releases

- `CI Build` publishes a pre-release per run: tag `ci-<version>-<run number>`, marked as a
  pre-release so it never becomes the repository's "Latest" release.
- `Release Build` publishes the tagged releases it manages (`v<version>`).

Use only on devices you own or are explicitly authorized to test.

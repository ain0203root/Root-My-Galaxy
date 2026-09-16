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

Each candidate in that sheet also reports how its kernel versions line up with
the phone. Regional siblings share a model and a three-part version, so a
profile that lists this build's full release is marked **Exact kernel release
match** while one that only lists the three-part version is marked **Kernel
version match only (6.6.98)** — the difference between a feed that has tied
the payload to your build and one that has not. The sheet opens preselected on
the exact match when the feed offers one.

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

Advanced mode adds a **Payload sources** entry under Settings. It opens a bottom sheet that
lists the GitHub `owner/repository` and branch of every catalog the app may use, with a checkbox
per entry to enable or disable it and a delete action to drop it. The sheet is the right shape
for this form: it rises with the keyboard, so the repository and branch fields and their Add
button stay visible on a short screen, where an alert dialog's buttons end up behind the IME. The built-in feed
([Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads)) is the
default entry and can be restored with one button, so pointing the list at a testing branch
for payloads that are not upstream yet does not cost you the official catalog.

Adding a source reads it before it is saved: the repository is resolved to a commit, its
`support/targets-v3.json` is downloaded and parsed, and only then does it join the list, so a
repository that is unreachable, has no manifest, or serves a schema this app cannot read is
refused with the reason instead of becoming a source that fails on every later run. The check
also reports what the catalog covers — the revision it was read at, how many payloads it holds,
the models and kernel versions across all of them, and whether any payload fits this phone,
which is the question a list of models alone cannot answer. Any row can be re-checked with the
check button once more of the catalog has landed, or after the source it follows has moved.
Coverage is kept for the life of the sheet and is not part of what gets saved: a summary of a
branch is stale the moment the branch moves, and a pinned source is how a revision is frozen
rather than a comment about it.

Any source can be **pinned**. The lock button on a row opens a revision picker for that source: the
ref itself (no pin), the repository's tags, and its most recent commits, each showing the commit, the
first line of its message and its date, with the current head marked `current` and whatever the
source is frozen at marked `pinned`. Naming a branch, tag, or commit by hand covers anything not in
those lists, and a tag named there is resolved to the commit it points at now.

**Choosing a revision and pinning to it are two separate steps**, because a pin is the one decision a
catalog cannot take back. Selecting a revision — or resolving a name to one — reads that revision and
shows what it serves: how many payloads it holds, the models and kernel versions they cover, and
whether any of them fits this phone, with the revision named as the revision the summary is about.
The picker then applies it with *Pin* (or *Follow the branch* for the no-pin choice). Opening the
picker starts on what the source is on now, so the first thing shown is what the current pin means.
A revision whose catalog cannot be read still says so and can still be pinned: an unreachable
manifest is not a reason to be unable to move a source off a bad revision. Pinning stores that
commit, after which the source reads from the pinned revision instead of following the branch. That
is what stops a catalog moving under a test, and it has a second effect worth knowing: a pinned
source needs no GitHub API call to load, so it keeps working when the API is rate limited. Unpinning
puts the source back on its ref.

A pinned revision and the branch it came from are two different catalogs, so they carry
different ids and can be configured side by side, which is how a fixed feed and a moving one get
compared in the selection sheet. Pinning is by commit rather than by tag even when the ref is a
tag, because a tag can be moved onto a different commit, which would defeat the point.

A ref is resolved from `github.com/{owner}/{repo}/commits/{ref}.atom` where possible, which is the
same list the repository page shows and is not subject to the sixty-requests-an-hour limit an
unauthenticated REST client shares with every other app on the same address. That limit is what made
adding or pinning a source fail with `HTTP 403` while the repository itself was perfectly readable,
and it is spent by something the user cannot see. The first entry of a ref's feed *is* that ref's
head, for a branch and for a tag alike, so resolution is the same read as the revision list. The REST
API is kept as the fallback, both for the `application/vnd.github.sha` answer and, if that fails
too, in the message: a limited API now says so rather than reporting a status number.

A ref is also resolved to its commit by asking GitHub for `application/vnd.github.sha`, which
answers with the 40-character commit and nothing else. Asking for the commit object instead was
fragile in a way that had nothing to do with commits: that response carries the commit's file
list, so a source whose newest commit touched enough files grew past the app's response limit and
became unreadable — the built-in feed did exactly that. The bare SHA cannot grow. A server that
ignores the requested media type and sends the commit object is still understood, and anything
else is refused rather than trusted.

Each enabled source is fetched on its own: its branch is resolved to a commit, that commit's
`support/targets-v3.json` is read, and every target is pinned to that commit and tagged with
the source it came from. Sources therefore do not shadow one another — when two of them offer
the same payload id, both appear in the selection sheet with their source underneath, and a
source that is unreachable or malformed is reported there and contributes nothing while the
others keep working. Installation only fails when no enabled source yields any target.

Payloads are cached per source, so the same payload id from two sources never shares a
download directory.

Every run records the source it came from and the commit that source was read at, and Run
details shows both. That is what keeps a result traceable after the branch has moved on: the
artifacts were pinned to that commit when the run started, so the revision named in the history
is the revision whose payload actually executed, not whatever the branch holds now.

A target may set `"requiresFreshP0Session": true` in the manifest. Payloads built for a
device whose P0 page address is only valid for the attempt that leaked it must not be
handed the app's cached offset or its attempt/timeout overrides, so marked targets run one
payload-native attempt per run, ignore the cached slide offset, and are left to their own
internal retry pacing. The app's own ceiling for those runs is an hour rather than the fifteen
minutes a cached multi-attempt run gets, because a page scan that has to be won inside a
single session can legitimately take far longer than the retry loop ever needed; the payload's
own limits still end the attempt first. The flag defaults to false, and eight profiles in the
official feed already set it.

A profile may also carry a `"routePolicy"` object, which is where the numbers the app hands a run
come from: `slideRoute` (`default`, `auto`, `tracefs`, or `legacy`/`p0`, passed to the payload as
`SLIDE_SOURCE`), `attempts`, `attemptTimeoutSec`, `p0AttemptTimeoutSec`, and `p0OffsetCache`. The
policy is the single source of the environment, so the run-plan screen, the direct transport and
the Shizuku transport all read the same values rather than three copies of them, and the run log
states the policy it used before the payload starts. Fields are read one by one with the legacy
value as the fallback: a feed published by a newer workflow must not cost an older build the
target it can use, and one out-of-range number should not either. A profile that is both marked
fresh-session and carries a policy keeps both, because who paces the run and how the payload
finds the slide are different questions: such a profile still runs one attempt with no cached
offset, and still gets its `SLIDE_SOURCE`.

The ceiling is keyed on the profile declaring the flag, not on the kernel version. Kernel 5.15
is a tempting proxy for "this target scans pages", but in the official feed only two of the
four 5.15 profiles ask for a fresh session, and marked profiles also run on 5.10, 6.1 and
6.1.157 — so a version-keyed policy would impose an hour-long single-attempt budget on targets
that never asked for it.

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

The better answer for a source in that position is `"sha256"` on the artifact, the lowercase hex
digest of what it serves. It is verified while the bytes are read, before the file is moved into
place, and it is what a size cannot be: a statement about the content rather than about its
length. An artifact that declares one has its size no longer checked against the manifest at
all, because matching digest already proves the length — so a feed that cannot pin a size can
state a hash instead of switching verification off. An enforced size must also be positive,
which makes a zero a feed error that fails at parse time rather than a download that can never
succeed.

## Run plan

Advanced mode adds **Run plan**, which shows what the next run will be handed before it starts:
the target the catalog resolves for this device and which source it came from, whether that
profile needs a fresh P0 session, the transport, the exact environment variables set for the
payload, the arguments only the Shizuku transport adds, whether a slide offset is already cached
for this boot, and the app-side ceilings — silence before a run is treated as stalled, the whole
run, and one helper command.

Every value is assembled from the same constants the run itself uses, so the screen cannot drift
from the behaviour, and it answers "why did the run stop there?" before a boot is spent finding
out. Where a payload is left to its own pacing the screen says so rather than showing an app
ceiling that will not be applied.

## When a run fails

A failed run names the stage it stopped in — starting the transport, resolving the payload for
this device, downloading the payloads, running the kernel exploit, loading KernelSU, or verifying
the control channel — reports the reason the app itself reached, and shows the last lines the
payload printed. The stage matters because the same wording can come out of a download, the
exploit, or the KernelSU load, and only some of those are worth retrying straight away; the payload
tail matters because the payload is the only account of the kernel race, so a failure that is not
the app's own decision is otherwise unexplained.

A payload that was killed rather than exiting is reported as the signal that killed it — `137` is
read back as `signal 9 (SIGKILL)`, because that is the one thing a status can say about a payload
that died without choosing to — and the reason is reduced to a single short line before it reaches
the card. The payload's own words belong in the log and, clipped to the last few lines, on the
failure card; a message that carried them instead once turned the card into a page of text with the
stage nowhere in sight.

The stage and reason are stored with the run, so a failure from a previous boot still says where
it ended, and the full log stays attached to the run for export. An unattended run at boot has the
stage in its notification title, since that notification is the whole of the explanation available
when nobody is looking at the screen.

## KernelSU readiness

A successful `--late-load` says the command finished, not that KernelSU is reachable afterwards, so
the run states which independent reading confirmed the control channel instead of taking the exit
code for it:

- the in-process native probe seeing KernelSU from the app's own process;
- KernelSU's own `su` answering through the running Shizuku server, which also proves a usable root
  shell;
- the helper's control report — `KernelSU control verified version=… flags=0x… uapi=… features=0x…`
  — with a version of zero or a `control check failed` line deliberately not counting as one.

One reading is enough, and the log line names the ones that answered. A run of the old kind — the
helper exited cleanly and nothing answered afterwards — is now reported as `KernelSU loaded but no
control channel answered` rather than as a success. Privileged maintenance that runs *after* root
(the module directory move and restore) asks KernelSU for a root shell first and only falls back to
the helper's temporary handoff socket: a Samsung kernel may refuse new connects to that socket while
KernelSU itself is perfectly healthy.

## Shizuku without a computer

Shizuku is what this app runs the payload as shell through, and it is normally started by hand over
adb — so after a reboot the transport is gone until someone finds a cable. Once KernelSU is on the
device that is unnecessary: KernelSU's own root shell can run Shizuku's starter, which is what
**Start Shizuku now** and **Shizuku on boot** in Settings do.

- Current Shizuku builds expose their starter as a native library inside their own APK, run with the
  path of the APK it belongs to; older or manually installed builds may have dropped a `start.sh` on
  shared storage. The native route is preferred, the legacy script is a fallback, and a device with
  neither is reported once rather than as two failures.
- The start is serialized and re-probes the binder immediately before every launch, because a start
  racing the Shizuku app's own or a previous boot's attempt otherwise looks like one that never took
  effect. A binder that appears during a probe is reported as already running, not as a failure.
- On boot the start runs after a settle delay, through KernelSU's root shell, up to three times. It
  runs both when a boot already has root and after a boot-time install succeeded — the boot that has
  to re-establish root is exactly the boot that can then bring Shizuku back.

It says so plainly when it cannot work: with no root there is no way for an app to start a
privileged process, so the failure names that rather than pretending the button did something.
Switching the setting on starts Shizuku there and then, so the setting is proven on the device
instead of at the next reboot.

## Post-root repair

A rooted boot can come up unusable — a module that breaks the framework, a mount that needs the
runtime recreated, a state worth getting out of — and until now the only answers were a reboot or a
cable. **Recovery**, in Advanced mode, has three actions, and each is a hold rather than a tap: they
restart the Android runtime or the phone, they are the only repair available, and a tap that opened a
dialog is one tap away from closing everything open. Each card says what it costs before it is held.

- **Restart Zygote** recreates the Android runtime through init — `setprop ctl.restart zygote`, which
  asks init to restart the service it owns, where killing Zygote from the app would leave init to
  recover by accident. The secondary Zygote, when the device runs one, goes first, because it can be
  restarted without the framework going down and so a failure there is still reportable.
- **KernelSU soft reboot** hands the transition to the installed `ksud`, whose own command table
  lists `soft-reboot` as *Emulate system reboot*: it stops and restarts the userspace and walks the
  module lifecycle in its normal order. It takes a per-boot lock carrying the boot id and the owner's
  pid, so a second request in the same boot is told the first already owns it rather than racing it —
  and a lock whose owner is no longer running is taken over, because a keeper killed mid-transition
  would otherwise lock the boot out of every later attempt. The card is offered only when the
  installed daemon's own help lists the command, since a feed may serve a daemon without it;
  whole-token matching keeps the `emulated-soft-reboot` marker in that same binary from counting. A
  daemon that did not return is stopped, and its last line is what the failure quotes, so the cause
  comes from KernelSU rather than from us.
- **Reboot and unroot** clears *root on boot* first and then reboots, because a reboot that happened
  first would come back rooted; if the request is refused, the setting is put back and the screen
  follows the stored value rather than the value it hoped for.

None of it acquires bootstrap root, replays the exploit, or stages a daemon: they consume the root
the verified load installed. Each action runs its real work in a detached root shell that checks for
itself that it is root, that the boot id has not changed under it, and — for the restart — that
Zygote is actually running, and then writes an acknowledgement the app reads back. A successful fork
is deliberately not an action: an action the child did not acknowledge is reported as a failure,
because the app must never call something scheduled that never happened. The child's own worst case
is bounded to stay inside the window the app waits in, so the reverse cannot happen either — a
daemon that has not returned is stopped and reported rather than left to fire a userspace transition
the app already gave up on.

## Build identity

Two builds of the same version are otherwise indistinguishable once installed, so every build
carries a version name that says which one it is:

| build | version name | version code |
|---|---|---|
| CI | `0.2.65+ci.<run number>.<commit>` | base + seconds since 2026-01-01 UTC |
| local | `0.2.65+local.<commit>` | base + seconds since 2026-01-01 UTC |

`appVersionBase` in `app/build.gradle.kts` is the only version written by hand. Both workflows
read that literal out of the file, and a release tag is `v<base>`.

The version code is derived from the clock rather than from the CI run number so that it is
strictly larger on every build anywhere: Android refuses to install a lower version code over a
higher one, and a run-number code would be far below a local build's and so fail to install over
it.

That clock reading goes through a `ValueSource` on purpose. A configuration cache entry stores the
value it was configured with, so reading the clock directly made a local rebuild that changed only
source files reuse the previous build's version code — two different APKs under one identity.
Treating the reading as a build configuration input means every build reconfigures, which is the
cost of the code being unique per build and is deliberate: remove the value source and local builds
start sharing identities again.

Where to read it: **Settings → About** shows the build label on the row itself and
`version (code)` inside the dialog, and the first line of every run log is `<version name>
(<version code>)`, stored with the rest of the log in run history. The name alone would not
distinguish two builds of one commit; the code is what does, which is why both are shown. The
update check compares only the dotted numbers, so a build's own suffix is never offered back to it
as an update.

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

# Root My Galaxy · ain0203root fork

<p align="center">
  <img width="132" height="132" alt="Root My Galaxy" src="https://github.com/user-attachments/assets/2ba0e360-0876-489c-b256-f75df7589785" />
</p>

<p align="center">
  <strong>Samsung Galaxy research, payload engineering, diagnostics, and reproducible Android builds.</strong>
</p>

<p align="center">
  <a href="https://github.com/ain0203root/Root-My-Galaxy/releases">Releases</a> ·
  <a href="https://github.com/ain0203root/Root-My-Galaxy/actions">Actions</a> ·
  <a href="https://github.com/ain0203root/Root-My-Galaxy-Payloads">Payloads</a> ·
  <a href="https://github.com/BuSung-dev/Root-My-Galaxy/issues/622">S24 FE research</a>
</p>

---

## What this fork is

This repository is the **Android application side** of Root My Galaxy, maintained as a research-oriented fork around explicitly supported Samsung model/kernel combinations.

The project deliberately separates the application from the device-specific native layer. Firmware profiles, offsets, native payloads, KernelSU artifacts, and support metadata live in the companion [`Root-My-Galaxy-Payloads`](https://github.com/ain0203root/Root-My-Galaxy-Payloads) repository.

The current focus is the **Samsung Galaxy S24 FE**, including reproducible work around the `6.1.157` kernel family, TraceFS/KASLR discovery, P0-session handling, and an experimental Canadian `SM-S721W` profile.

## Project at a glance

| Area | What is here |
| --- | --- |
| Android client | Jetpack Compose application and install orchestration |
| Native integration | CVE-2026-43499 payload loading and KernelSU hand-off |
| Device selection | Manifest-driven model + kernel matching |
| S24 FE work | `SM-S721B` production profile and experimental `SM-S721W` Canada profile |
| Diagnostics | Forensic build path with structured execution traces |
| Fresh P0 | Explicit support for profiles that require a fresh P0 session |
| Offline mode | Opt-in bundled payload source via `-PrmgOfflinePayloads=true` |
| CI/CD | Reproducible Android builds, payload artifacts, and experimental releases |

## Current S24 FE work

### S721B · `S721BXXSCDZF3`

The companion payload repository carries a `6.1.157` profile for `SM-S721B`. The fork has been used to investigate both physical-P0 and TraceFS-based KASLR discovery and to preserve reproducible runtime evidence from real-device testing.

### S721W · `S721WVLSCDZF4`

An isolated experimental Canadian target exists for `SM-S721W`. It is intentionally kept separate from the known S721B profile so that W-specific offsets, fingerprint data, SLUB geometry, and later-stage behavior can be tuned without destabilizing the known-good target.

**Experimental means experimental:** the Canadian profile is not represented here as universally verified.

## Engineering work in this fork

### Fresh P0 session support

Upstream PR `#613` was backported into this fork as PR `#3`, adding a manifest-controlled `requiresFreshP0Session` mode. For such profiles, the app avoids reusing a cached P0 offset, runs a single exploit attempt, and avoids the normal short stall timeout path.

### Forensic APK builds

PR `#1` added a diagnostic-only forensic build workflow. It instruments the application at build time and records structured application/native observations without changing the production source path.

The forensic layer can capture application decisions, raw native output, command execution metadata, control-flow observations, comparisons, indirect calls, and timeout/rejection events.

### Offline payload mode

Upstream PR `#570` was cleanly backported as PR `#5`. The normal online path remains the default. An opt-in build flag can instead package the manifest and payload artifacts as application assets for an offline runtime path.

```text
./gradlew assembleRelease -PrmgOfflinePayloads=true
```

The backport also includes the upstream manifest conversion helper, preserving fields such as `requiresFreshP0Session`.

## Companion payload repository

**[`Root-My-Galaxy-Payloads`](https://github.com/ain0203root/Root-My-Galaxy-Payloads)** contains the device-specific native side:

- firmware-specific targets and offsets;
- CVE-2026-43499 native payload source and compiled payloads;
- app bootstrap/root helper sources;
- versioned KernelSU late-load artifacts;
- schema-v3 support metadata;
- reproducible porting and device-validation documentation;
- experimental S24 FE Canadian work kept separate from the verified S721B target.

The app resolves the companion repository's current commit before fetching the support manifest and artifacts, so the runtime feed is tied to an immutable revision.

## Screenshots

<p align="center">
  <img width="220" alt="Root My Galaxy screenshot 1" src="https://github.com/user-attachments/assets/3f562ea4-8c39-4ade-bfd3-93eea1a1cc24" />
  <img width="220" alt="Root My Galaxy screenshot 2" src="https://github.com/user-attachments/assets/8dde0443-12cf-4058-ba76-0337aefb92a0" />
  <img width="220" alt="Root My Galaxy screenshot 3" src="https://github.com/user-attachments/assets/f656e8af-60a6-4fcb-a3db-d4232bede613" />
</p>

## Build locally

Requirements:

- Android Studio JBR 21
- Android SDK 37
- Android NDK 28+
- CMake 3.22.1

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases & artifacts

Experimental S24 FE APKs are published separately from ordinary CI artifacts. The repository also keeps reproducible Actions artifacts for debugging and validation.

One existing experimental S24 FE APK artifact is `app-debug` from Actions run `34198115865`; it contains the APK plus a SHA-256 sidecar. The artifact was created from the June-profile S24 FE work and is intentionally described as experimental rather than universally compatible.

## Safety and scope

Use only on devices you own or are explicitly authorized to test. Experimental exploit research can fail, reboot a device, or behave differently across firmware revisions and execution contexts. This repository does not claim compatibility beyond the profiles and evidence documented in the payload repository.

---

### Credits

Root My Galaxy is based on the upstream project by [BuSung-dev](https://github.com/BuSung-dev). The native exploit work is based on the published CVE-2026-43499 exploit lineage referenced by the payload repository.

#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path.cwd()
PKG = ROOT / "app/src/main/java/dev/busung/s25uroot"
TRACE = PKG / "ForensicTrace.kt"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_forensic_trace.py <ForensicTrace.kt>")
TRACE.write_text(Path(sys.argv[1]).read_text(encoding="utf-8"), encoding="utf-8")

def patch(path, replacements):
    s = path.read_text(encoding="utf-8")
    original = s
    for old, new, label in replacements:
        if old not in s:
            raise SystemExit(f"ANCHOR NOT FOUND: {path}: {label}")
        s = s.replace(old, new, 1)
    if s == original:
        raise SystemExit(f"NO CHANGE: {path}")
    path.write_text(s, encoding="utf-8")
    print(f"patched {path}")

ivm = PKG / "InstallViewModel.kt"
patch(ivm, [
    ('class InstallViewModel(application: Application) : AndroidViewModel(application) {\n    private val app = application\n',
     'class InstallViewModel(application: Application) : AndroidViewModel(application) {\n    private val app = application\n    private val forensicSessionId = "${System.currentTimeMillis()}-${android.os.Process.myPid()}"\n',
     "constructor fields"),
    ('    init {\n        refresh()\n    }\n',
     '    init {\n        ForensicTrace.init(app, forensicSessionId)\n        ForensicTrace.event("APP", "viewmodel_init", fields = mapOf("version" to BuildConfig.VERSION_NAME))\n        refresh()\n    }\n',
     "init"),
    ('    fun install(profileId: String? = null) {\n        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) return\n',
     '    fun install(profileId: String? = null) {\n        ForensicTrace.event("INSTALL", "request", fields = mapOf("profile_id" to profileId))\n        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) {\n            ForensicTrace.decision("install_gate", mutableState.value.phase, "not_busy_and_not_installed", false, "install_already_active_or_installed")\n            return\n        }\n',
     "install gate"),
    ('            activeRunShizuku = AppPreferences.shizukuMode(app)\n            try {\n',
     '            activeRunShizuku = AppPreferences.shizukuMode(app)\n            ForensicTrace.event("INSTALL", "transport_selected", fields = mapOf("shizuku" to shizukuEnabled()))\n            try {\n',
     "transport"),
    ('                val profile = if (profileId == null) {\n                    repository.resolveTarget(DeviceSnapshot.current())\n                } else {\n                    repository.resolveTarget(profileId)\n                }\n',
     '                val snapshot = DeviceSnapshot.current()\n                ForensicTrace.event("TARGET", "device_snapshot", fields = mapOf("snapshot" to snapshot.toString()))\n                val profile = if (profileId == null) {\n                    repository.resolveTarget(snapshot)\n                } else {\n                    repository.resolveTarget(profileId)\n                }\n                ForensicTrace.event("TARGET", "profile_selected", fields = mapOf("profile_id" to profile.profileId, "profile" to profile.toString()))\n',
     "target selection"),
    ('                val payloads = repository.download(profile) { appendLog("[*] $it") }\n                appendLog(app.getString(R.string.log_download_verified))\n',
     '                val payloads = repository.download(profile) { appendLog("[*] $it") }\n                ForensicTrace.event("PAYLOAD", "download_complete", fields = mapOf(\n                    "exploit_path" to payloads.exploit.absolutePath,\n                    "exploit_size" to payloads.exploit.length(),\n                    "kernelsu_path" to payloads.kernelSu.absolutePath,\n                    "kernelsu_size" to payloads.kernelSu.length(),\n                ))\n                appendLog(app.getString(R.string.log_download_verified))\n',
     "payload completion"),
    ('        val process = if (shizuku) {\n            val stagedPayload = shizukuStage(payload, SHIZUKU_PAYLOAD_PATH, "755")\n',
     '        val process = if (shizuku) {\n            ForensicTrace.event("EXPLOIT", "prepare_process", fields = mapOf("transport" to "shizuku", "payload" to payload.absolutePath))\n            val stagedPayload = shizukuStage(payload, SHIZUKU_PAYLOAD_PATH, "755")\n',
     "shizuku process prep"),
    ('            ShizukuController.exec(\n                arrayOf("/system/bin/sh", "-c", "true"),\n',
     '            ForensicTrace.command(\n                transport = "shizuku",\n                argv = listOf("/system/bin/sh", "-c", "true"),\n                env = shizukuEnvironment(bootToken, stagedPayload.absolutePath, helper.absolutePath).associate { it.substringBefore("=") to it.substringAfter("=") },\n            )\n            ShizukuController.exec(\n                arrayOf("/system/bin/sh", "-c", "true"),\n',
     "shizuku command trace"),
    ('            processBuilder.environment().apply {\n                put("EXPLOIT_ATTEMPTS", EXPLOIT_ATTEMPTS)\n                put("P0_ATTEMPT_TIMEOUT_SEC", P0_ATTEMPT_TIMEOUT_SEC)\n                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", EXPLOIT_ATTEMPT_TIMEOUT_SEC)\n                cachedP0Offset(bootToken)?.let { put(P0_OFFSET_ENV, it) }\n            }\n',
     '            processBuilder.environment().apply {\n                put("EXPLOIT_ATTEMPTS", EXPLOIT_ATTEMPTS)\n                put("P0_ATTEMPT_TIMEOUT_SEC", P0_ATTEMPT_TIMEOUT_SEC)\n                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", EXPLOIT_ATTEMPT_TIMEOUT_SEC)\n                put("RMG_FORENSIC_TRACE", "1")\n                cachedP0Offset(bootToken)?.let { put(P0_OFFSET_ENV, it) }\n            }\n',
     "forensic env"),
    ('            processBuilder.start()\n        }\n',
     '            ForensicTrace.command(\n                transport = "standalone",\n                argv = listOf(helper.absolutePath, "--run-payload", payload.absolutePath, helper.absolutePath, logFile.absolutePath),\n                env = processBuilder.environment().filterKeys { it in setOf("EXPLOIT_ATTEMPTS", "P0_ATTEMPT_TIMEOUT_SEC", "EXPLOIT_ATTEMPT_TIMEOUT_SEC", P0_OFFSET_ENV, "RMG_FORENSIC_TRACE") },\n            )\n            processBuilder.start()\n        }\n',
     "standalone command trace"),
    ('                val rawLog = readLog()\n                if (rawLog != lastRawLog) {\n',
     '                val rawLog = readLog()\n                if (rawLog != lastRawLog) {\n                    val delta = rawLog.removePrefix(lastRawLog)\n                    ForensicTrace.raw("exploit", delta)\n',
     "exploit raw capture"),
    ('        add("EXPLOIT_ATTEMPT_TIMEOUT_SEC=$EXPLOIT_ATTEMPT_TIMEOUT_SEC")\n        add("CVE43499_ROOT_HELPER=$helperPath")\n        add("LD_PRELOAD=$payloadPath")\n',
     '        add("EXPLOIT_ATTEMPT_TIMEOUT_SEC=$EXPLOIT_ATTEMPT_TIMEOUT_SEC")\n        add("RMG_FORENSIC_TRACE=1")\n        add("CVE43499_ROOT_HELPER=$helperPath")\n        add("LD_PRELOAD=$payloadPath")\n',
     "shizuku forensic env"),
    ('            val rawLog = readLog()\n            cacheP0Offset(bootToken, rawLog)\n',
     '            val rawLog = readLog()\n            ForensicTrace.raw("exploit.final", rawLog.removePrefix(lastRawLog))\n            cacheP0Offset(bootToken, rawLog)\n',
     "final raw capture"),
    ('            val earlyOutput = captured.toString().trim()\n            require(exitCode == 0) {\n',
     '            val earlyOutput = captured.toString().trim()\n            ForensicTrace.event("EXPLOIT", "process_exit", fields = mapOf("exit_code" to exitCode, "captured_bytes" to earlyOutput.toByteArray(Charsets.UTF_8).size))\n            require(exitCode == 0) {\n',
     "exploit exit"),
    ('    private fun publishExploitLog(prefix: String, rawLog: String) {\n',
     '    private fun publishExploitLog(prefix: String, rawLog: String) {\n        ForensicTrace.event("EXPLOIT", "ui_log_publish", fields = mapOf("bytes" to rawLog.toByteArray(Charsets.UTF_8).size))\n',
     "ui log publish"),
    ('            drainProcessOutput(process, captured)\n            val exitCode = process.waitFor()\n            return CommandResult(exitCode, stripAnsi(captured.toString().trim()))\n',
     '            drainProcessOutput(process, captured)\n            val exitCode = process.waitFor()\n            val output = stripAnsi(captured.toString().trim())\n            ForensicTrace.commandResult(\n                transport = if (shizukuEnabled()) "shizuku" else "standalone",\n                exitCode = exitCode,\n                stdout = output,\n                stderr = "",\n                durationMs = SystemClock.elapsedRealtime() - startedAt,\n            )\n            return CommandResult(exitCode, output)\n',
     "helper result"),
    ('    private fun appendLog(line: String) {\n        val cleanLine = stripAnsi(line).trim()\n',
     '    private fun appendLog(line: String) {\n        ForensicTrace.raw("app", line)\n        val cleanLine = stripAnsi(line).trim()\n',
     "append log trace"),
])

for name, replacements in [
    ("ShizukuController.kt", [
        ('    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {\n        val binder = Shizuku.getBinder()\n',
         '    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {\n        ForensicTrace.command(\n            transport = "shizuku",\n            argv = cmd.toList(),\n            env = env?.associate { it.substringBefore("=") to it.substringAfter("=") },\n            cwd = dir,\n        )\n        val binder = Shizuku.getBinder()\n', "shizuku exec"),
    ]),
    ("PayloadRepository.kt", [
        ('    fun loadTargets(): List<TargetProfile> {\n        val commit = resolveMainCommit()\n',
         '    fun loadTargets(): List<TargetProfile> {\n        ForensicTrace.event("REPOSITORY", "load_targets_start")\n        val commit = resolveMainCommit()\n        ForensicTrace.event("REPOSITORY", "payload_commit", fields = mapOf("commit" to commit))\n', "repository load"),
        ('    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {\n        val directory = File(context.filesDir, "payloads/${profile.profileId}").apply { mkdirs() }\n',
         '    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {\n        ForensicTrace.event("REPOSITORY", "download_start", fields = mapOf("profile_id" to profile.profileId, "exploit_url" to profile.exploit.url, "kernelsu_url" to profile.kernelSu.url))\n        val directory = File(context.filesDir, "payloads/${profile.profileId}").apply { mkdirs() }\n', "repository download"),
        ('        onProgress(context.getString(R.string.repo_verified, label))\n        return destination\n',
         '        ForensicTrace.event("REPOSITORY", "artifact_ready", fields = mapOf("label" to label, "path" to destination.absolutePath, "size" to destination.length()))\n        onProgress(context.getString(R.string.repo_verified, label))\n        return destination\n', "artifact ready"),
        ('            connect()\n            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }\n',
         '            connect()\n            ForensicTrace.event("HTTP", "response", fields = mapOf("url" to url, "code" to responseCode, "content_length" to contentLengthLong))\n            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }\n', "http response"),
    ])
]:
    patch(PKG / name, replacements)

readme = ROOT / "README.md"
text = readme.read_text(encoding="utf-8")
if "## Forensic trace" not in text:
    text += "\n## Forensic trace\n\nThe diagnostic build can persist an application-side execution trace under `filesDir/forensic-trace/`. It records target resolution, repository/artifact events, process commands, selected environment metadata, native-log deltas, process exits, and observed decision lines. A diagnostic build of Payloads adds compiler-level execution callbacks.\n"
    readme.write_text(text, encoding="utf-8")

print("forensic application patch completed")

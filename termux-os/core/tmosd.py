#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import platform
import shutil
import subprocess
import sys
import time
from pathlib import Path

HOME = Path(os.environ.get("TMOS_HOME", Path.home() / ".tmos")).expanduser()
STATE = HOME / "state"
LOGS = HOME / "logs"
EVENTS = LOGS / "events.jsonl"
SERVICES = HOME / "services"
CONF = HOME / "services.conf"

for p in (STATE, LOGS, SERVICES):
    p.mkdir(parents=True, exist_ok=True)


def emit(kind: str, **data: object) -> None:
    record = {"ts": time.time(), "kind": kind, **data}
    with EVENTS.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def run(cmd: list[str]) -> tuple[int, str, str]:
    try:
        p = subprocess.run(cmd, text=True, capture_output=True, timeout=20, check=False)
        return p.returncode, p.stdout.strip(), p.stderr.strip()
    except Exception as exc:  # diagnostics must never crash the shell
        return 127, "", str(exc)


def cmd_status() -> None:
    load = run(["cat", "/proc/loadavg"])[1]
    disk = shutil.disk_usage(Path.home())
    payload = {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": platform.python_version(),
        "prefix": os.environ.get("PREFIX"),
        "home": str(Path.home()),
        "tmos_home": str(HOME),
        "loadavg": load,
        "disk_free_gib": round(disk.free / 1024**3, 2),
        "disk_total_gib": round(disk.total / 1024**3, 2),
        "services": service_state(),
    }
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    emit("status", **payload)


def service_state() -> dict[str, object]:
    result: dict[str, object] = {}
    if not CONF.exists():
        return result
    for line in CONF.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, _, command = line.partition("=")
        if name and command:
            pidfile = SERVICES / f"{name}.pid"
            pid = pidfile.read_text().strip() if pidfile.exists() else None
            alive = bool(pid and Path(f"/proc/{pid}").exists())
            result[name] = {"command": command, "pid": pid, "running": alive}
    return result


def cmd_services() -> None:
    print(json.dumps(service_state(), indent=2))


def cmd_doctor() -> None:
    required = ["bash", "python", "git", "curl", "jq", "tmux", "ssh"]
    checks = {name: shutil.which(name) for name in required}
    checks["termux"] = bool(os.environ.get("PREFIX", "").startswith("/data/data/com.termux"))
    checks["proc"] = Path("/proc").exists()
    checks["storage"] = Path.home().exists()
    missing = [name for name, value in checks.items() if not value]
    result = {"ok": not missing, "checks": checks, "missing": missing}
    print(json.dumps(result, indent=2))
    emit("doctor", **result)
    if missing:
        raise SystemExit(1)


def cmd_event(message: str) -> None:
    emit("user", message=message)
    print("recorded")


def command_for(name: str) -> str | None:
    for key, value in service_state().items():
        if key == name:
            return str(value["command"])
    return None


def cmd_service(action: str, name: str) -> None:
    command = command_for(name)
    if not command:
        raise SystemExit(f"unknown service: {name}")
    pidfile = SERVICES / f"{name}.pid"
    if action == "stop" or action == "restart":
        if pidfile.exists():
            pid = pidfile.read_text().strip()
            run(["kill", pid])
            pidfile.unlink(missing_ok=True)
            emit("service_stop", name=name, pid=pid)
        if action == "stop":
            print(f"stopped {name}")
            return
    log = LOGS / f"service-{name}.log"
    with log.open("ab") as fh:
        proc = subprocess.Popen(command, shell=True, stdout=fh, stderr=subprocess.STDOUT, start_new_session=True)
    pidfile.write_text(str(proc.pid))
    emit("service_start", name=name, pid=proc.pid, command=command)
    print(f"started {name} pid={proc.pid}")


def cmd_exec(argv: list[str]) -> None:
    if not argv:
        raise SystemExit("tmosctl exec requires a command")
    emit("exec", argv=argv)
    os.execvp(argv[0], argv)


def cmd_snapshot() -> None:
    snap = STATE / f"snapshot-{int(time.time())}.json"
    payload = {
        "status": "snapshot",
        "created_at": time.time(),
        "env": {k: v for k, v in os.environ.items() if k in {"HOME", "PREFIX", "PATH", "TMOS_HOME", "TMOS_CONFIG"}},
        "status_data": service_state(),
    }
    snap.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(snap)


def cmd_health() -> None:
    print(json.dumps({"ok": True, "time": time.time(), "events": EVENTS.exists()}, indent=2))


def main(argv: list[str]) -> None:
    action = argv[0] if argv else "status"
    if action == "status": cmd_status()
    elif action == "doctor": cmd_doctor()
    elif action == "services": cmd_services()
    elif action in {"start", "stop", "restart"} and len(argv) == 2: cmd_service(action, argv[1])
    elif action == "event": cmd_event(" ".join(argv[1:]))
    elif action == "exec": cmd_exec(argv[1:])
    elif action == "snapshot": cmd_snapshot()
    elif action == "health": cmd_health()
    elif action == "json": main(argv[1:])
    else: raise SystemExit(f"unknown action: {action}")


if __name__ == "__main__":
    main(sys.argv[1:])

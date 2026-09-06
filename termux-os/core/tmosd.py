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
    rec = {"ts": time.time(), "kind": kind, **data}
    with EVENTS.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + "\n")


def run(cmd: list[str]) -> tuple[int, str, str]:
    try:
        p = subprocess.run(cmd, text=True, capture_output=True, timeout=20, check=False)
        return p.returncode, p.stdout.strip(), p.stderr.strip()
    except Exception as exc:
        return 127, "", str(exc)


def service_commands() -> dict[str, str]:
    result: dict[str, str] = {}
    if not CONF.exists():
        return result
    for line in CONF.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            name, sep, command = line.partition("=")
            if sep:
                result[name.strip()] = command.strip()
    return result


def service_state() -> dict[str, object]:
    result: dict[str, object] = {}
    for name, command in service_commands().items():
        pidfile = SERVICES / f"{name}.pid"
        pid = pidfile.read_text().strip() if pidfile.exists() else None
        result[name] = {"command": command, "pid": pid, "running": bool(pid and Path(f"/proc/{pid}").exists())}
    return result


def status() -> dict[str, object]:
    disk = shutil.disk_usage(Path.home())
    return {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": platform.python_version(),
        "prefix": os.environ.get("PREFIX"),
        "home": str(Path.home()),
        "tmos_home": str(HOME),
        "loadavg": run(["cat", "/proc/loadavg"])[1],
        "disk_free_gib": round(disk.free / 1024**3, 2),
        "disk_total_gib": round(disk.total / 1024**3, 2),
        "services": service_state(),
    }


def cmd_status() -> None:
    data = status()
    print(json.dumps(data, indent=2, ensure_ascii=False))
    emit("status", **data)


def cmd_doctor() -> None:
    required = ["bash", "python", "git", "curl", "jq", "tmux", "ssh"]
    checks = {name: shutil.which(name) for name in required}
    checks.update(
        termux=bool(os.environ.get("PREFIX", "").startswith("/data/data/com.termux")),
        proc=Path("/proc").exists(),
        storage=Path.home().exists(),
    )
    missing = [name for name, value in checks.items() if not value]
    result = {"ok": not missing, "checks": checks, "missing": missing}
    print(json.dumps(result, indent=2))
    emit("doctor", **result)
    if missing:
        raise SystemExit(1)


def cmd_event(message: str) -> None:
    emit("user", message=message)
    print("recorded")


def cmd_services() -> None:
    print(json.dumps(service_state(), indent=2, ensure_ascii=False))


def cmd_service(action: str, name: str) -> None:
    command = service_commands().get(name)
    if not command:
        raise SystemExit(f"unknown service: {name}")
    pidfile = SERVICES / f"{name}.pid"
    if action in {"stop", "restart"} and pidfile.exists():
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
    pidfile.write_text(str(proc.pid), encoding="utf-8")
    emit("service_start", name=name, pid=proc.pid, command=command)
    print(f"started {name} pid={proc.pid}")


def snapshot() -> Path:
    snap = STATE / f"snapshot-{int(time.time())}.json"
    snap.write_text(json.dumps({"created_at": time.time(), "status": status()}, indent=2), encoding="utf-8")
    emit("snapshot", path=str(snap))
    return snap


def loop(kind: str) -> None:
    interval = max(5, int(os.environ.get("TMOS_HEALTH_INTERVAL", "30")))
    while True:
        if kind == "snapshot":
            path = snapshot()
            emit("loop", service=kind, snapshot=str(path))
        else:
            emit(kind, **status())
        time.sleep(interval)


def cmd_health() -> None:
    print(json.dumps({"ok": True, "time": time.time(), "events": EVENTS.exists()}, indent=2))


def main(argv: list[str]) -> None:
    action = argv[0] if argv else "status"
    if action == "status": cmd_status()
    elif action == "doctor": cmd_doctor()
    elif action == "services": cmd_services()
    elif action in {"start", "stop", "restart"} and len(argv) == 2: cmd_service(action, argv[1])
    elif action == "event": cmd_event(" ".join(argv[1:]))
    elif action == "snapshot": print(snapshot())
    elif action == "snapshot-loop": loop("snapshot")
    elif action == "health-loop": loop("health")
    elif action == "health": cmd_health()
    elif action == "exec":
        if len(argv) < 2: raise SystemExit("exec requires command")
        emit("exec", argv=argv[1:])
        os.execvp(argv[1], argv[1:])
    elif action == "json": main(argv[1:])
    else: raise SystemExit(f"unknown action: {action}")


if __name__ == "__main__":
    main(sys.argv[1:])

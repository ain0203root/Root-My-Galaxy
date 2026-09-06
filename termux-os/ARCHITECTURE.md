# Architecture

## Layers

```text
Android kernel / Android framework
              |
            Termux
              |
     +--------+---------+
     |                  |
  TMOS CLI          TMOS daemon
     |                  |
     +--------+---------+
              |
     services / workspaces / logs
              |
       optional providers
```

### Control plane
`core/tmos` is the stable operator-facing entry point. It intentionally stays small and delegates stateful logic to `core/tmosd.py`.

### State
All mutable state is under `$TMOS_HOME` (default `$HOME/.tmos`). This keeps the system reversible and avoids polluting Android-owned paths.

### Services
Service definitions are declarative lines in `services.conf`. Each service gets a PID file and append-only log. Long-running services are designed for Termux:Boot integration.

### Observability
Every significant control-plane action emits JSONL records in `$TMOS_HOME/logs/events.jsonl`. Human-readable operational logs live beside it.

### AI gateway
`tmos-ai` speaks a Responses-compatible HTTP shape using an endpoint, model, and credential supplied through environment variables. No credential is stored in the repository.

## Non-goals

This project is not an Android kernel replacement, does not modify protected partitions, and does not attempt to defeat Android security boundaries. Root is an optional capability, not a requirement.

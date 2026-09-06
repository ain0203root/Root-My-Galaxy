# Termux OS

A serious Android userland platform layered on Termux. It does **not** replace Android's kernel or init system; instead it turns a normal Termux install into a reproducible, service-oriented workstation with a single control plane.

## Goals

- reproducible bootstrap from a clean Termux installation
- one `tmos` command for lifecycle, diagnostics, services, packages and recovery
- structured logging with persistent JSONL event streams
- optional root-aware acceleration without requiring root
- developer workspace with isolated project environments
- safe, idempotent configuration and rollback
- CI that tests shell and Python components

## Bootstrap

```sh
pkg update -y
pkg install -y git
mkdir -p "$HOME/src"
git clone https://github.com/ain0203root/Root-My-Galaxy.git "$HOME/src/root-my-galaxy"
cd "$HOME/src/root-my-galaxy/termux-os"
bash install/bootstrap.sh
```

Then:

```sh
tmos doctor
tmos status
tmos shell
```

## Design

`core/` is the control plane, `services/` contains long-running userland services, `profiles/` contains device-independent defaults, and `install/` performs idempotent deployment into `$PREFIX` and `$HOME`.

The system is intentionally userland-first: privileged capabilities are detected and used only when present. Nothing in the project disables Android security controls or bypasses device protections.

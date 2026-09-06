#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mapfile -t packages < <(sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$ROOT_DIR/packages/base.txt")

pkg update -y
pkg install -y "${packages[@]}"
printf '%s\n' 'base package set installed'

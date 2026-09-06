#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TMOS_HOME="${TMOS_HOME:-$HOME/.tmos}"
TMOS_BIN="$PREFIX/bin"

log(){ printf '[tmos-bootstrap] %s\n' "$*"; }
need_cmd(){ command -v "$1" >/dev/null 2>&1 || { log "missing command: $1"; exit 1; }; }

pkg_install(){
  local packages=("$@")
  pkg update -y >/dev/null
  pkg install -y "${packages[@]}" >/dev/null
}

main(){
  need_cmd pkg
  need_cmd bash
  mkdir -p "$TMOS_HOME" "$TMOS_HOME/state" "$TMOS_HOME/logs" "$TMOS_HOME/run" "$TMOS_HOME/workspaces" "$TMOS_HOME/services"

  pkg_install git python curl jq openssh tmux procps coreutils findutils grep sed gawk rsync zip unzip

  install -m 755 "$ROOT_DIR/core/tmos" "$TMOS_BIN/tmos"
  install -m 755 "$ROOT_DIR/core/tmosctl" "$TMOS_BIN/tmosctl"
  install -m 755 "$ROOT_DIR/core/tmos-log" "$TMOS_BIN/tmos-log"
  install -m 755 "$ROOT_DIR/core/tmos-ai" "$TMOS_BIN/tmos-ai"
  install -m 755 "$ROOT_DIR/core/tmos-workspace" "$TMOS_BIN/tmos-workspace"
  install -m 755 "$ROOT_DIR/core/tmosd.py" "$TMOS_HOME/tmosd.py"
  install -m 755 "$ROOT_DIR/install/termux-services.sh" "$TMOS_HOME/install-services.sh"
  install -m 755 "$ROOT_DIR/install/shell-profile.sh" "$TMOS_HOME/install-shell-profile.sh"
  cp "$ROOT_DIR/profiles/default.env" "$TMOS_HOME/default.env"
  cp "$ROOT_DIR/profiles/services.conf" "$TMOS_HOME/services.conf"

  mkdir -p "$HOME/.config/tmos"
  cp "$ROOT_DIR/profiles/tmos.conf" "$HOME/.config/tmos/config"

  cat > "$TMOS_HOME/env" <<'EOF'
export TMOS_HOME="${TMOS_HOME:-$HOME/.tmos}"
export TMOS_CONFIG="${TMOS_CONFIG:-$HOME/.config/tmos/config}"
export PATH="$HOME/.local/bin:${PATH}"
EOF

  "$TMOS_HOME/install-shell-profile.sh"
  "$TMOS_HOME/install-services.sh"
  "$TMOS_BIN/tmos" init
  "$TMOS_BIN/tmos" doctor || true
  log "installed successfully; run: tmos status"
}

main "$@"

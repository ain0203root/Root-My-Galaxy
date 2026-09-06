#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

TMOS_HOME="${TMOS_HOME:-$HOME/.tmos}"
fail=0

check(){
  local label="$1"; shift
  if "$@" >/dev/null 2>&1; then printf '[OK] %s\n' "$label"; else printf '[FAIL] %s\n' "$label"; fail=1; fi
}

check 'tmos executable' command -v tmos
check 'tmos control plane' tmos health
check 'Python engine compile' python -m py_compile "$TMOS_HOME/tmosd.py"
check 'workspace manager' command -v tmos-workspace
check 'AI gateway' command -v tmos-ai

printf 'verification complete\n'
exit "$fail"

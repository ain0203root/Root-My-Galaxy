#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

TMOS_HOME="${TMOS_HOME:-$HOME/.tmos}"
mkdir -p "$HOME/.local/bin" "$HOME/.config/tmos"

cat > "$HOME/.local/bin/tmos-welcome" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
printf '\nTermux OS ready.\n'
printf 'status: '; tmos status >/dev/null 2>&1 && printf 'ok\n' || printf 'degraded\n'
printf 'commands: tmos status | tmos doctor | tmos services | tmos logs\n\n'
EOF
chmod 755 "$HOME/.local/bin/tmos-welcome"

profile="$HOME/.bashrc"
entry='[ -f "$HOME/.tmos/env" ] && . "$HOME/.tmos/env"'
grep -Fqx "$entry" "$profile" 2>/dev/null || printf '\n%s\n' "$entry" >> "$profile"

grep -Fqx 'alias ts="tmos status"' "$profile" 2>/dev/null || cat >> "$profile" <<'EOF'
alias ts="tmos status"
alias td="tmos doctor"
alias tl="tmos logs"
alias tw="tmos-workspace"
EOF

printf '%s\n' 'shell integration installed'

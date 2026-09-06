#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

TMOS_HOME="${TMOS_HOME:-$HOME/.tmos}"
mkdir -p "$TMOS_HOME/services" "$TMOS_HOME/logs"

unit_dir="$HOME/.termux/boot"
mkdir -p "$unit_dir"

cat > "$unit_dir/tmos" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
[ -f "$HOME/.tmos/env" ] && . "$HOME/.tmos/env"
"${PREFIX}/bin/tmos" start logger || true
"${PREFIX}/bin/tmos" start snapshot || true
EOF
chmod 755 "$unit_dir/tmos"

printf '%s\n' 'Termux:Boot integration installed.'
printf '%s\n' 'Install the Termux:Boot app separately to enable automatic startup.'

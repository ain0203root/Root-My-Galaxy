#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

while IFS= read -r file; do
  bash -n "$file"
done < <(find "$ROOT_DIR" -type f -name '*.sh' -print)

printf '%s\n' 'shell syntax: OK'

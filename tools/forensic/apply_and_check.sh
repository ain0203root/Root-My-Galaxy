#!/usr/bin/env bash
set -euo pipefail
python3 tools/forensic/apply_forensic_trace.py tools/forensic/ForensicTrace.kt
git diff --check
git status --short

#!/bin/bash
# OWASP ZAP baseline runner for a single HTTP(S) target.
#
# Usage:
#   TARGET_URL=https://gateway.example.com tests/security/zap-baseline.sh [reports_dir]
#
# Defaults reports_dir to tests/security/reports/.
#
# Exit codes (forwarded from zap-baseline.py):
#   0  no warnings, no failures   -> pipeline passes
#   2  warnings only              -> pipeline passes with WARN
#   1  at least one FAIL rule     -> pipeline FAILS
#   3+ unexpected ZAP error       -> pipeline FAILS
#
# Reports written to ${REPORTS_DIR}:
#   zap-report.html      - human-readable report
#   zap-report.json      - machine-readable, archived as Jenkins artifact
#   zap-warnings.md      - markdown summary for posting to Slack on warn/fail

set -uo pipefail

TARGET_URL="${TARGET_URL:-}"
if [[ -z "$TARGET_URL" ]]; then
    echo "ERROR: TARGET_URL is required (e.g. http://localhost:30087)" >&2
    exit 1
fi

REPORTS_DIR="${1:-tests/security/reports}"
mkdir -p "$REPORTS_DIR"
REPORTS_ABS="$(cd "$REPORTS_DIR" && pwd)"

ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"

echo "[zap-baseline] target = ${TARGET_URL}"
echo "[zap-baseline] reports = ${REPORTS_ABS}"
echo "[zap-baseline] image  = ${ZAP_IMAGE}"

# zap-baseline.py reads the target with -t and writes the three reports below.
# -I makes ZAP exit 0 even on WARN; we still want CI to know about WARNs, so we
# DROP -I and translate the exit code ourselves further down.
docker run --rm \
    --network=host \
    -v "${REPORTS_ABS}:/zap/wrk/:rw" \
    -t "${ZAP_IMAGE}" \
    zap-baseline.py \
        -t "${TARGET_URL}" \
        -r zap-report.html \
        -J zap-report.json \
        -w zap-warnings.md \
        -m 1 \
    || ZAP_RC=$?

ZAP_RC="${ZAP_RC:-0}"
echo "[zap-baseline] zap-baseline.py exit code: ${ZAP_RC}"

case "$ZAP_RC" in
    0) echo "[zap-baseline] PASS: no warnings, no failures." ;;
    2) echo "[zap-baseline] WARN: warnings present. Build continues but please review."; ZAP_RC=0 ;;
    1) echo "[zap-baseline] FAIL: at least one rule failed. See ${REPORTS_ABS}/zap-report.html"; ;;
    *) echo "[zap-baseline] ERROR: unexpected ZAP exit code (${ZAP_RC}). See container logs above." ;;
esac

exit "$ZAP_RC"

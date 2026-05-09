#!/bin/bash
# Runs Locust against a target host with sane defaults for each profile.
#
# Profiles:
#   baseline  -> 10 users / 60s   (matches stage pipeline)
#   master    -> 50 users / 120s  (matches master pipeline)
#   stress    -> 200 users / 300s (manual run only)
#
# Override:
#   LOCUST_HOST     target form-service URL (default: http://localhost:8086)
#   PROFILE         baseline | master | stress (default: baseline)
#   REPORT_DIR      output directory, absolute or relative to repo root
#                   (default: tests/performance/reports)

set -euo pipefail

LOCUST_HOST="${LOCUST_HOST:-http://localhost:8086}"
PROFILE="${PROFILE:-baseline}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

REPORT_DIR="${REPORT_DIR:-${REPO_ROOT}/tests/performance/reports}"

case "$PROFILE" in
    baseline) USERS=10;  RATE=2;  DURATION=60s  ;;
    master)   USERS=50;  RATE=5;  DURATION=120s ;;
    stress)   USERS=200; RATE=20; DURATION=300s ;;
    *)
        echo "Unknown PROFILE: $PROFILE (use: baseline | master | stress)" >&2
        exit 2
        ;;
esac

mkdir -p "$REPORT_DIR"

locust -f "${REPO_ROOT}/tests/performance/locustfile.py" \
    --headless \
    -u "$USERS" -r "$RATE" -t "$DURATION" \
    --host "$LOCUST_HOST" \
    --html "${REPORT_DIR}/locust-report-${PROFILE}.html" \
    --csv  "${REPORT_DIR}/locust-${PROFILE}" \
    --exit-code-on-error 0

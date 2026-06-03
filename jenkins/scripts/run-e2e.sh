#!/bin/bash
# Runs the E2E test module against a target environment.
# Defaults to docker-compose.dev.yml ports on localhost; override via env vars.
#
#   BASE_URL          host scheme+name (default: http://localhost)
#   AUTH_PORT         auth-service port (default: 8180)
#   IDENTITY_PORT     identity-service port (default: 8083)
#   FORM_PORT         form-service port (default: 8086)
#   PROMOTION_PORT    promotion-service port (default: 8088)
#   NOTIFICATION_PORT notification-service port (default: 8082)
#   DASHBOARD_PORT    dashboard-service port (default: 8084)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
AUTH_PORT="${AUTH_PORT:-8180}"
IDENTITY_PORT="${IDENTITY_PORT:-8083}"
FORM_PORT="${FORM_PORT:-8086}"
PROMOTION_PORT="${PROMOTION_PORT:-8088}"
NOTIFICATION_PORT="${NOTIFICATION_PORT:-8082}"
DASHBOARD_PORT="${DASHBOARD_PORT:-8084}"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

cd "$REPO_ROOT"
# --rerun-tasks: the workspace is wiped between CI builds but the Gradle cache on the
# agent persists, so :tests:e2e:test resolves UP-TO-DATE and the suite never actually
# runs against the live environment. Force it to execute every time.
./gradlew :tests:e2e:test --rerun-tasks \
    -Dbase.url="$BASE_URL" \
    -Dauth.port="$AUTH_PORT" \
    -Didentity.port="$IDENTITY_PORT" \
    -Dform.port="$FORM_PORT" \
    -Dpromotion.port="$PROMOTION_PORT" \
    -Dnotification.port="$NOTIFICATION_PORT" \
    -Ddashboard.port="$DASHBOARD_PORT"

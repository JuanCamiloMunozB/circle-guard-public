#!/bin/bash
# Generates a Markdown release-notes document for the current pipeline build.
#
# Usage:
#   generate-release-notes.sh <image_tag> <build_number> <namespace> <output_file>
#
# Commits are grouped by Conventional Commits prefix:
#   feat -> Features
#   fix  -> Bug Fixes
# Anything else goes under "Other Changes".

set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: $0 <image_tag> <build_number> <namespace> <output_file>" >&2
    exit 2
fi

IMAGE_TAG="$1"
BUILD_NUMBER="$2"
NAMESPACE="$3"
OUTPUT_FILE="$4"

# --- Git context --------------------------------------------------------------
PREVIOUS_TAG="$(git describe --tags --abbrev=0 'HEAD^' 2>/dev/null || echo NONE)"
GIT_HASH="$(git rev-parse --short HEAD)"
GIT_AUTHOR="$(git log -1 --pretty='%an')"
DATE="$(date '+%Y-%m-%d')"

if [[ "$PREVIOUS_TAG" == "NONE" ]]; then
    RANGE_ARGS=(-20)
    RANGE_LABEL="last 20 commits"
else
    RANGE_ARGS=("${PREVIOUS_TAG}..HEAD")
    RANGE_LABEL="${PREVIOUS_TAG}..HEAD"
fi

# Pulls "<subject>|<author>" lines for the chosen range
ALL_COMMITS="$(git log "${RANGE_ARGS[@]}" --pretty='%s|%an' || true)"

# Classifies a single commit subject into one of the buckets above.
classify() {
    local subject="$1"
    case "$subject" in
        feat\(*\):*|feat:*) echo features ;;
        fix\(*\):*|fix:*)   echo fixes ;;
        *)                  echo other ;;
    esac
}

FEATURES=""
FIXES=""
OTHER=""

while IFS='|' read -r subject author; do
    [[ -z "$subject" ]] && continue
    line="- ${subject} (${author})"
    case "$(classify "$subject")" in
        features) FEATURES+="${line}"$'\n' ;;
        fixes)    FIXES+="${line}"$'\n' ;;
        *)        OTHER+="${line}"$'\n' ;;
    esac
done <<< "$ALL_COMMITS"

# Renders the bucketed commits into the CHANGES variable, keeping a blank line
# between sections (command substitution would strip the trailing \n).
CHANGES=""
[[ -n "$FEATURES" ]] && CHANGES+="### Features"$'\n\n'"$FEATURES"$'\n'
[[ -n "$FIXES" ]]    && CHANGES+="### Bug Fixes"$'\n\n'"$FIXES"$'\n'
[[ -n "$OTHER" ]]    && CHANGES+="### Other Changes"$'\n\n'"$OTHER"$'\n'

if [[ -z "$CHANGES" ]]; then
    CHANGES="_No commits in range ${RANGE_LABEL}._"
fi

# --- Render -------------------------------------------------------------------
mkdir -p "$(dirname "$OUTPUT_FILE")"
cat > "$OUTPUT_FILE" <<EOF
# CircleGuard Release Notes - ${IMAGE_TAG}

**Date:** ${DATE}
**Build:** ${BUILD_NUMBER}
**Commit:** ${GIT_HASH}
**Author:** ${GIT_AUTHOR}
**Environment:** master / production
**Range:** ${RANGE_LABEL}

---

## Services Released

| Service              | Port | Image Tag      |
|----------------------|------|----------------|
| auth-service         | 8180 | ${IMAGE_TAG}   |
| identity-service     | 8083 | ${IMAGE_TAG}   |
| form-service         | 8086 | ${IMAGE_TAG}   |
| promotion-service    | 8088 | ${IMAGE_TAG}   |
| notification-service | 8082 | ${IMAGE_TAG}   |
| dashboard-service    | 8084 | ${IMAGE_TAG}   |

---

## Changes

${CHANGES}

---

## Deployment

- Kubernetes namespace: ${NAMESPACE}
- Infrastructure: PostgreSQL 16, Neo4j 5.26, Kafka 7.6.0, Redis 7.2
- Rollout strategy: Rolling update (zero downtime)
EOF

echo "Release notes written to: ${OUTPUT_FILE}"

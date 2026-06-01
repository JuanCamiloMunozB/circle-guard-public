#!/bin/bash
# Semantic version bump + git tag based on Conventional Commits since the last tag.
#
# Usage:
#   bump-and-tag.sh [--dry-run] [--push]
#
# Behaviour:
#   1. Reads the most recent tag (`git describe --tags --abbrev=0`). Defaults to
#      v0.0.0 when no tag exists.
#   2. Inspects every commit subject since that tag and classifies it:
#        BREAKING CHANGE: / feat!:  -> major bump
#        feat:                      -> minor bump
#        fix: / perf: / refactor:   -> patch bump
#        anything else              -> ignored
#      Highest-rank bump wins (major > minor > patch).
#   3. Emits the new tag name to stdout.
#   4. Unless --dry-run is given, creates an annotated tag with `git tag -a`.
#   5. With --push, pushes the tag to origin (requires credentials in env).
#
# Exit codes:
#   0  bump computed and tag created
#   2  nothing to release (no relevant commits since last tag)
#   1  any unexpected error

set -euo pipefail

DRY_RUN=0
PUSH=0
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        --push)    PUSH=1 ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Usage: $0 [--dry-run] [--push]" >&2
            exit 1
            ;;
    esac
done

# --- Last tag ----------------------------------------------------------------
LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || echo v0.0.0)"
echo "[bump-and-tag] last tag: ${LAST_TAG}"

if ! [[ "$LAST_TAG" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "[bump-and-tag] ERROR: last tag '${LAST_TAG}' is not vMAJOR.MINOR.PATCH" >&2
    exit 1
fi
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

# --- Classify commits since LAST_TAG ----------------------------------------
if [[ "$LAST_TAG" == "v0.0.0" ]]; then
    # First release: everything in the history counts.
    SUBJECTS="$(git log --pretty='%s%n%b%n---' || true)"
else
    SUBJECTS="$(git log "${LAST_TAG}..HEAD" --pretty='%s%n%b%n---' || true)"
fi

BUMP="none"   # one of: none, patch, minor, major
rank() {
    case "$1" in
        major) echo 3 ;;
        minor) echo 2 ;;
        patch) echo 1 ;;
        *)     echo 0 ;;
    esac
}
upgrade_bump() {
    local candidate="$1"
    if [[ "$(rank "$candidate")" -gt "$(rank "$BUMP")" ]]; then
        BUMP="$candidate"
    fi
}

while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    [[ "$line" == "---" ]] && continue
    # Detect breaking changes first (highest rank).
    if [[ "$line" == "BREAKING CHANGE:"* ]] || [[ "$line" =~ ^[a-z]+\!\(.*\):|^[a-z]+\!: ]]; then
        upgrade_bump major
        continue
    fi
    case "$line" in
        feat\(*\):*|feat:*)
            upgrade_bump minor ;;
        fix\(*\):*|fix:*|perf\(*\):*|perf:*|refactor\(*\):*|refactor:*)
            upgrade_bump patch ;;
    esac
done <<< "$SUBJECTS"

echo "[bump-and-tag] computed bump: ${BUMP}"

if [[ "$BUMP" == "none" ]]; then
    echo "[bump-and-tag] No release-worthy commits since ${LAST_TAG}. Nothing to do."
    exit 2
fi

case "$BUMP" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
esac

NEW_TAG="v${MAJOR}.${MINOR}.${PATCH}"
echo "[bump-and-tag] new tag: ${NEW_TAG}"
echo "${NEW_TAG}"  # emit on stdout for callers that capture it

if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[bump-and-tag] --dry-run: NOT creating tag"
    exit 0
fi

git tag -a "${NEW_TAG}" -m "Release ${NEW_TAG} (auto from Conventional Commits since ${LAST_TAG})"
echo "[bump-and-tag] created annotated tag ${NEW_TAG}"

if [[ "$PUSH" -eq 1 ]]; then
    git push origin "${NEW_TAG}"
    echo "[bump-and-tag] pushed ${NEW_TAG} to origin"
fi

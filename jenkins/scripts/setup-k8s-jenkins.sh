#!/usr/bin/env bash
# Applies the Jenkins ServiceAccount + RBAC, extracts the SA token, and writes
# K8S_SA_TOKEN and K8S_API_SERVER into the project .env so JCasC can create the
# k8s-sa-token credential automatically when Jenkins starts.
#
# Run once before `docker compose ... up`, and again whenever the SA token is
# rotated or Docker Desktop restarts and the API port changes.
#
# Usage: bash jenkins/scripts/setup-k8s-jenkins.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env"
ACCOUNT_YAML="${REPO_ROOT}/jenkins/config/jenkins-account.yaml"

# ── 1. Apply ServiceAccount + RBAC ───────────────────────────────────────────
echo "==> Applying ServiceAccount and RBAC..."
kubectl apply -f "${ACCOUNT_YAML}"

# ── 2. Wait for the token secret to be populated ─────────────────────────────
echo "==> Waiting for jenkins-token secret to be populated..."
TOKEN=""
for i in $(seq 1 15); do
    TOKEN=$(kubectl get secret jenkins-token -n default \
        -o jsonpath='{.data.token}' 2>/dev/null \
        | base64 -d 2>/dev/null || true)
    if [ -n "$TOKEN" ]; then
        echo "    Token ready."
        break
    fi
    echo "    Attempt ${i}/15 — waiting 2 s..."
    sleep 2
done

if [ -z "$TOKEN" ]; then
    echo "ERROR: jenkins-token secret has no token after 30 s. Check the ServiceAccount." >&2
    exit 1
fi

# ── 3. Extract and rewrite the API server URL ────────────────────────────────
echo "==> Extracting API server URL..."
RAW_SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
API_SERVER=$(echo "$RAW_SERVER" \
    | sed 's|https://127\.0\.0\.1:|https://host.docker.internal:|' \
    | sed 's|https://kubernetes\.docker\.internal:|https://host.docker.internal:|')

echo "    API server: ${API_SERVER}"

# ── 4. Update .env ────────────────────────────────────────────────────────────
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: .env not found at ${ENV_FILE}. Copy .env.example first." >&2
    exit 1
fi

echo "==> Updating ${ENV_FILE}..."
TMPFILE=$(mktemp)
grep -v '^K8S_SA_TOKEN=' "${ENV_FILE}" \
    | grep -v '^K8S_API_SERVER=' > "$TMPFILE"
printf '\nK8S_SA_TOKEN=%s\nK8S_API_SERVER=%s\n' "$TOKEN" "$API_SERVER" >> "$TMPFILE"
mv "$TMPFILE" "${ENV_FILE}"

echo ""
echo "Done. .env updated with K8S_SA_TOKEN and K8S_API_SERVER."
echo "Start (or restart) Jenkins to apply:"
echo "  docker compose -f jenkins/config/docker-compose.jenkins.yml up -d --build"

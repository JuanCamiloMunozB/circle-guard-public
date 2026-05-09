#!/usr/bin/env bash
# Deploys the infrastructure layer (Postgres, Neo4j, Redis, Kafka, LDAP) into a
# given namespace and waits for each component to become ready.
#
# Run once per namespace before executing any CI pipeline. The pipeline itself
# only deploys application services — it assumes infrastructure is already up.
#
# Usage:
#   bash k8s/install-infra.sh circleguard-dev
#   bash k8s/install-infra.sh circleguard-stage
#   bash k8s/install-infra.sh circleguard-master

set -euo pipefail

NAMESPACE=${1:?"Usage: $0 <namespace>"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Namespace: ${NAMESPACE}"

# ── Pre-pull infra images into the Docker Desktop daemon ──────────────────────
# Kubernetes uses imagePullPolicy: IfNotPresent for named tags, so after this
# one-time pull it will never contact Docker Hub again for these images.
# Running docker pull on the host is sufficient because Docker Desktop K8s
# shares the same daemon.
INFRA_IMAGES=(
    "postgres:16"
    "neo4j:5.26"
    "redis:7.2"
    "confluentinc/cp-zookeeper:7.6.0"
    "confluentinc/cp-kafka:7.6.0"
    "osixia/openldap:1.5.0"
)
echo "==> Pulling infrastructure images (skipped if already cached)..."
for img in "${INFRA_IMAGES[@]}"; do
    if docker image inspect "${img}" &>/dev/null; then
        echo "    [cached] ${img}"
    else
        echo "    [pulling] ${img}"
        docker pull "${img}"
    fi
done

# ── Namespace ────────────────────────────────────────────────────────────────
kubectl apply -f "${SCRIPT_DIR}/namespaces.yaml"

# ── Shared config & secrets ───────────────────────────────────────────────────
kubectl apply -f "${SCRIPT_DIR}/configmap.yaml" -n "${NAMESPACE}"

# ── Infrastructure manifests ──────────────────────────────────────────────────
echo "==> Applying infrastructure manifests..."
kubectl apply -f "${SCRIPT_DIR}/infrastructure/" -n "${NAMESPACE}"

# ── Wait for each component ───────────────────────────────────────────────────
COMPONENTS=(postgres kafka zookeeper neo4j redis ldap)
for component in "${COMPONENTS[@]}"; do
    if kubectl get deployment "${component}" -n "${NAMESPACE}" &>/dev/null; then
        echo "==> Waiting for ${component}..."
        kubectl rollout status deployment/"${component}" \
            -n "${NAMESPACE}" --timeout=300s
    fi
done

echo ""
echo "Infrastructure ready in namespace '${NAMESPACE}'."
echo "You can now run the CI pipeline."

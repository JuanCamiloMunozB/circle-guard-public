#!/bin/bash
# Idempotent creation of CircleGuard Kubernetes namespaces.
# Safe to re-run: existing namespaces are left untouched.

set -euo pipefail

NAMESPACES=(circleguard-dev circleguard-stage circleguard-master)

for ns in "${NAMESPACES[@]}"; do
    kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
done

echo "Namespaces ready: ${NAMESPACES[*]}"

#!/usr/bin/env bash
# Bootstrap script: build the Jenkins controller image and push to Docker Hub.
# Run ONCE before deploying Jenkins to AKS.
# Uses `docker buildx --push` so no layers accumulate locally.
#
# Usage:
#   bash jenkins/scripts/build-push-controller.sh [tag]
#   tag defaults to 'lts'
#
# Prerequisites:
#   - docker buildx with a multi-arch builder active
#   - docker login -u <docker-hub-username> (run first)

set -euo pipefail

REGISTRY="docker.io/circleguard"
IMAGE="jenkins-controller"
TAG="${1:-lts}"
FULL_TAG="${REGISTRY}/${IMAGE}:${TAG}"

# Ensure a multi-arch builder exists (no-op if already created)
docker buildx inspect cg-builder >/dev/null 2>&1 \
  || docker buildx create --name cg-builder --use

echo "[build-push-controller] Building and pushing ${FULL_TAG} (linux/amd64 + linux/arm64)..."
docker buildx build \
    --builder cg-builder \
    --platform linux/amd64,linux/arm64 \
    --push \
    -t "${FULL_TAG}" \
    -f jenkins/config/Dockerfile.jenkins \
    jenkins/config

echo "[build-push-controller] Done. Image available at: ${FULL_TAG}"
echo "[build-push-controller] No local layers created (--push sends directly to Docker Hub)."

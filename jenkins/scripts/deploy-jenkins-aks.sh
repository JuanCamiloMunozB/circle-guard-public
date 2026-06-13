#!/usr/bin/env bash
# Deploy (or update) the Jenkins controller in AKS.
# Run after `az aks start` and `az aks get-credentials`.
#
# What it does:
#   1. Applies RBAC (jenkins-account.yaml) if not already present.
#   2. Creates / updates the jenkins-casc ConfigMap from jenkins/config/casc.yaml.
#   3. Applies the PVC, Deployment, and Service manifests.
#   4. Waits for the pod to become ready.
#
# Prerequisites:
#   - kubectl context pointing at aks-cg-dev  (az aks get-credentials ...)
#   - k8s/jenkins/jenkins-secret.yaml exists and is filled in (gitignored)
#   - Jenkins controller image already pushed (jenkins/scripts/build-push-controller.sh)
#
# Usage:
#   bash jenkins/scripts/deploy-jenkins-aks.sh

set -euo pipefail

KUBECTL="kubectl"
MANIFESTS_DIR="k8s/jenkins"
SECRET_FILE="${MANIFESTS_DIR}/jenkins-secret.yaml"

echo "[deploy-jenkins] Checking kubectl context..."
CONTEXT=$($KUBECTL config current-context)
echo "[deploy-jenkins] Active context: ${CONTEXT}"

echo "[deploy-jenkins] Applying RBAC (jenkins ServiceAccount + Roles)..."
$KUBECTL apply -f jenkins/config/jenkins-account.yaml

echo "[deploy-jenkins] Creating/updating jenkins-casc ConfigMap from jenkins/config/casc.yaml..."
$KUBECTL create configmap jenkins-casc \
    --from-file=casc.yaml=jenkins/config/casc.yaml \
    --namespace=default \
    --dry-run=client -o yaml \
  | $KUBECTL apply -f -

echo "[deploy-jenkins] Applying credentials Secret..."
if [[ ! -f "${SECRET_FILE}" ]]; then
  echo "ERROR: ${SECRET_FILE} not found."
  echo "Copy k8s/jenkins/jenkins-secret.example.yaml to jenkins-secret.yaml and fill in values."
  exit 1
fi
$KUBECTL apply -f "${SECRET_FILE}"

echo "[deploy-jenkins] Applying PVC, Deployment, Service..."
$KUBECTL apply -f "${MANIFESTS_DIR}/jenkins-pvc.yaml"
$KUBECTL apply -f "${MANIFESTS_DIR}/jenkins-deployment.yaml"
$KUBECTL apply -f "${MANIFESTS_DIR}/jenkins-service.yaml"

echo "[deploy-jenkins] Waiting for Jenkins pod to become ready (up to 5 min)..."
$KUBECTL rollout status deployment/jenkins --namespace=default --timeout=300s

echo "[deploy-jenkins] Fetching external IP..."
EXTERNAL_IP=""
for i in $(seq 1 30); do
  EXTERNAL_IP=$($KUBECTL get svc jenkins -n default -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  [[ -n "${EXTERNAL_IP}" ]] && break
  echo "[deploy-jenkins] Waiting for LoadBalancer IP... (${i}/30)"
  sleep 10
done

if [[ -n "${EXTERNAL_IP}" ]]; then
  echo ""
  echo "================================================"
  echo " Jenkins is ready at: http://${EXTERNAL_IP}:8080"
  echo " Share this URL with Jose Manuel."
  echo " Reminder: az aks stop when done to save credit."
  echo "================================================"
else
  echo "[deploy-jenkins] LoadBalancer IP not yet assigned. Run:"
  echo "  kubectl get svc jenkins -n default"
fi

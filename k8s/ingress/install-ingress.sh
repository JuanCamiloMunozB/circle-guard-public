#!/usr/bin/env bash
# Installs nginx-ingress-controller + cert-manager and deploys TLS Ingress
# resources for gateway-service and dashboard-service.
#
# Prerequisites:
#   - kubectl connected to AKS cluster
#   - helm >= 3.12
#
# Usage:
#   bash k8s/ingress/install-ingress.sh [--namespace circleguard-dev] [--uninstall]

set -euo pipefail

NAMESPACE="${1:-circleguard-dev}"
UNINSTALL="${2:-}"

if [[ "$UNINSTALL" == "--uninstall" ]]; then
  echo "==> Uninstalling ingress stack..."
  kubectl delete -f k8s/ingress/gateway-ingress.yaml -n "${NAMESPACE}" 2>/dev/null || true
  kubectl delete -f k8s/ingress/dashboard-ingress.yaml -n "${NAMESPACE}" 2>/dev/null || true
  kubectl delete -f k8s/ingress/cert-manager-issuer.yaml 2>/dev/null || true
  helm uninstall ingress-nginx -n ingress-nginx 2>/dev/null || true
  helm uninstall cert-manager -n cert-manager 2>/dev/null || true
  echo "Done."
  exit 0
fi

# ── Helm repos ────────────────────────────────────────────────────────────────
echo "==> Adding Helm repositories..."
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo update

# ── 1. nginx-ingress-controller ───────────────────────────────────────────────
echo "==> Installing nginx-ingress-controller..."
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --values k8s/ingress/values-nginx-ingress.yaml \
  --timeout 5m --wait

# ── 2. cert-manager ───────────────────────────────────────────────────────────
echo "==> Installing cert-manager..."
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.14.4 \
  --set installCRDs=true \
  --timeout 5m --wait

# ── 3. ClusterIssuer + CA ─────────────────────────────────────────────────────
echo "==> Applying cert-manager ClusterIssuer..."
sleep 10  # wait for cert-manager webhooks to be ready
kubectl apply -f k8s/ingress/cert-manager-issuer.yaml

# ── 4. Ingress resources ──────────────────────────────────────────────────────
echo "==> Applying TLS Ingress resources to namespace ${NAMESPACE}..."
kubectl apply -f k8s/ingress/gateway-ingress.yaml -n "${NAMESPACE}"
kubectl apply -f k8s/ingress/dashboard-ingress.yaml -n "${NAMESPACE}"

# ── Status ────────────────────────────────────────────────────────────────────
echo ""
INGRESS_IP=$(kubectl get svc ingress-nginx-controller -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")

echo "=========================================="
echo " Ingress stack installed!"
echo "=========================================="
echo ""
echo " LoadBalancer IP: ${INGRESS_IP}"
echo ""
echo " Add to /etc/hosts (or C:\\Windows\\System32\\drivers\\etc\\hosts):"
echo "   ${INGRESS_IP}  gateway.circleguard.local"
echo "   ${INGRESS_IP}  dashboard.circleguard.local"
echo ""
echo " HTTPS endpoints:"
echo "   https://gateway.circleguard.local"
echo "   https://dashboard.circleguard.local"
echo ""
echo " COST REMINDER: run 'az aks stop' when done!"
echo "=========================================="

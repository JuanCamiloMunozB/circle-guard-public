#!/usr/bin/env bash
# Installs the full CircleGuard observability stack:
#   Prometheus + Grafana + AlertManager (kube-prometheus-stack)
#   Loki + Promtail (log aggregation)
#   Jaeger all-in-one (distributed tracing)
#   PrometheusRule alerts
#   Grafana dashboard ConfigMap
#
# Prerequisites:
#   - kubectl connected to AKS cluster (az aks get-credentials ...)
#   - helm >= 3.12
#   - AKS cluster is RUNNING (az aks start ...)
#
# Usage:
#   bash monitoring/install-monitoring.sh [--namespace monitoring] [--uninstall]

set -euo pipefail

NAMESPACE="${MONITORING_NAMESPACE:-monitoring}"
UNINSTALL="${1:-}"

# ── Uninstall path ────────────────────────────────────────────────────────────
if [[ "$UNINSTALL" == "--uninstall" ]]; then
  echo "==> Uninstalling observability stack from namespace ${NAMESPACE}..."
  helm uninstall prometheus -n "${NAMESPACE}" 2>/dev/null || true
  helm uninstall loki -n "${NAMESPACE}" 2>/dev/null || true
  kubectl delete -f monitoring/jaeger/jaeger-allinone.yaml 2>/dev/null || true
  kubectl delete -f monitoring/alerts/circleguard-rules.yaml 2>/dev/null || true
  kubectl delete -f monitoring/dashboards/circleguard-overview-configmap.yaml 2>/dev/null || true
  echo "Done."
  exit 0
fi

# ── Namespace ─────────────────────────────────────────────────────────────────
echo "==> Creating namespace ${NAMESPACE}..."
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# ── Helm repos ────────────────────────────────────────────────────────────────
echo "==> Adding Helm repositories..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo add grafana https://grafana.github.io/helm-charts 2>/dev/null || true
helm repo update

# ── 1. kube-prometheus-stack (Prometheus + Grafana + AlertManager) ────────────
echo "==> Installing kube-prometheus-stack..."
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace "${NAMESPACE}" \
  --version "58.3.0" \
  --values monitoring/helm/values-kube-prometheus-stack.yaml \
  --timeout 10m \
  --wait

# ── 2. Loki + Promtail ────────────────────────────────────────────────────────
echo "==> Installing Loki stack..."
helm upgrade --install loki grafana/loki-stack \
  --namespace "${NAMESPACE}" \
  --version "2.10.2" \
  --values monitoring/helm/values-loki-stack.yaml \
  --timeout 5m \
  --wait

# ── 3. Jaeger all-in-one ─────────────────────────────────────────────────────
echo "==> Deploying Jaeger all-in-one..."
kubectl apply -f monitoring/jaeger/jaeger-allinone.yaml

# ── 4. PrometheusRule alerts ──────────────────────────────────────────────────
echo "==> Applying PrometheusRule alerts..."
kubectl apply -f monitoring/alerts/circleguard-rules.yaml

# ── 5. Grafana dashboard ──────────────────────────────────────────────────────
echo "==> Loading Grafana dashboard ConfigMap..."
kubectl apply -f monitoring/dashboards/circleguard-overview-configmap.yaml

# ── Wait & status ─────────────────────────────────────────────────────────────
echo ""
echo "==> Waiting for Jaeger to be ready..."
kubectl rollout status deployment/jaeger -n "${NAMESPACE}" --timeout=120s

echo ""
echo "=========================================="
echo " Observability stack installed!"
echo "=========================================="
echo ""
echo " Grafana:  http://<node-ip>:32000"
echo "           user: admin / password: circleguard-grafana-admin"
echo ""
echo " Jaeger:   http://<node-ip>:32686"
echo ""
echo " Port-forward alternatives (no NodePort needed):"
echo "   kubectl port-forward svc/prometheus-grafana 3000:80 -n ${NAMESPACE}"
echo "   kubectl port-forward svc/jaeger-ui 16686:16686 -n ${NAMESPACE}"
echo "   kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n ${NAMESPACE}"
echo ""
echo " COST REMINDER: run 'az aks stop' when done!"
echo "=========================================="

#!/bin/bash
# OWASP ZAP baseline scan executed as an in-cluster Pod against the gateway.
# Shared by Jenkinsfile.stage and Jenkinsfile.master so the scan logic lives in
# one place instead of being copy-pasted into both pipelines.
#
# Required env:
#   K8S_NAMESPACE     target namespace (e.g. circleguard-stage)
# Optional env:
#   ZAP_TARGET        URL to scan          (default: http://gateway-service:8087)
#   ZAP_POD           pod name             (default: zap-baseline)
#   ZAP_REPORT        report file (rel)    (default: tests/security/reports/zap-baseline.txt)
#   ZAP_WAIT_ITERS    poll iters, 10s each (default: 50)
#
# Assumes kubectl is already authenticated against the target cluster: the
# Jenkinsfile wraps the call in withKubeConfig + insecure-skip-tls-verify.
# -I keeps WARN-level findings from failing the build.

set -euo pipefail

NS="${K8S_NAMESPACE:?K8S_NAMESPACE is required}"
ZAP_TARGET="${ZAP_TARGET:-http://gateway-service:8087}"
ZAP_POD="${ZAP_POD:-zap-baseline}"
ZAP_REPORT="${ZAP_REPORT:-tests/security/reports/zap-baseline.txt}"
ZAP_WAIT_ITERS="${ZAP_WAIT_ITERS:-50}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT"

mkdir -p "$(dirname "$ZAP_REPORT")"

cat > zap-pod.yaml <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: ${ZAP_POD}
  namespace: ${NS}
spec:
  restartPolicy: Never
  containers:
    - name: zap
      image: ghcr.io/zaproxy/zaproxy:stable
      command: ["zap-baseline.py"]
      args: ["-t","${ZAP_TARGET}","-I"]
EOF

kubectl -n "$NS" delete pod "$ZAP_POD" --ignore-not-found
kubectl -n "$NS" apply -f zap-pod.yaml
for i in $(seq 1 "$ZAP_WAIT_ITERS"); do
    phase=$(kubectl -n "$NS" get pod "$ZAP_POD" -o jsonpath='{.status.phase}' 2>/dev/null || echo Pending)
    echo "[zap] pod phase: $phase"
    [ "$phase" = "Succeeded" ] && break
    [ "$phase" = "Failed" ] && break
    sleep 10
done
kubectl -n "$NS" logs "$ZAP_POD" | tee "$ZAP_REPORT" || true
kubectl -n "$NS" delete pod "$ZAP_POD" --ignore-not-found

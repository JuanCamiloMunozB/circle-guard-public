#!/bin/bash
# Locust load test executed as an in-cluster Pod, reaching the services over
# cluster DNS (form-service:8086, gateway-service:8087, ...). Shared by
# Jenkinsfile.stage and Jenkinsfile.master; the per-pipeline load profile is
# passed in via the LOCUST_* env vars below.
#
# Driving Locust from the agent over port-forward is unreliable because
# host.docker.internal resolves to the Windows host (whose 808x ports belong to
# the local dev stack) rather than the WSL agent, so traffic hits the wrong
# service. Running inside the cluster avoids that entirely.
#
# Required env:
#   K8S_NAMESPACE       target namespace
# Optional env (per-pipeline profile):
#   LOCUST_POD          pod name              (default: locust-baseline)
#   LOCUST_USERS        concurrent users      (default: 10)
#   LOCUST_SPAWN_RATE   users spawned/sec     (default: 2)
#   LOCUST_RUN_TIME     run duration          (default: 60s)
#   LOCUST_REPORT       report file (rel)     (default: tests/performance/reports/locust-baseline.txt)
#   LOCUST_WAIT_ITERS   poll iters, 10s each  (default: 40)
#   LOCUST_FILE         locustfile (rel)      (default: tests/performance/locustfile.py)
#
# Assumes kubectl is already authenticated against the target cluster: the
# Jenkinsfile wraps the call in withKubeConfig + insecure-skip-tls-verify.

set -euo pipefail

NS="${K8S_NAMESPACE:?K8S_NAMESPACE is required}"
LOCUST_POD="${LOCUST_POD:-locust-baseline}"
LOCUST_USERS="${LOCUST_USERS:-10}"
LOCUST_SPAWN_RATE="${LOCUST_SPAWN_RATE:-2}"
LOCUST_RUN_TIME="${LOCUST_RUN_TIME:-60s}"
LOCUST_REPORT="${LOCUST_REPORT:-tests/performance/reports/locust-baseline.txt}"
LOCUST_WAIT_ITERS="${LOCUST_WAIT_ITERS:-40}"
LOCUST_FILE="${LOCUST_FILE:-tests/performance/locustfile.py}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT"

mkdir -p "$(dirname "$LOCUST_REPORT")"

cat > locust-pod.yaml <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: ${LOCUST_POD}
  namespace: ${NS}
spec:
  restartPolicy: Never
  containers:
    - name: locust
      image: mirror.gcr.io/locustio/locust
      args: ["-f","/mnt/locustfile.py","--headless","-u","${LOCUST_USERS}","-r","${LOCUST_SPAWN_RATE}","-t","${LOCUST_RUN_TIME}","--only-summary","--exit-code-on-error","0"]
      env:
        - { name: LOCUST_HOST,    value: "http://form-service:8086" }
        - { name: GATEWAY_HOST,   value: "http://gateway-service:8087" }
        - { name: FILE_HOST,      value: "http://file-service:8085" }
        - { name: DASHBOARD_HOST, value: "http://dashboard-service:8084" }
      volumeMounts:
        - { name: script, mountPath: /mnt }
  volumes:
    - name: script
      configMap: { name: locust-script }
EOF

kubectl -n "$NS" delete pod "$LOCUST_POD" --ignore-not-found
kubectl -n "$NS" create configmap locust-script \
    --from-file=locustfile.py="$LOCUST_FILE" \
    --dry-run=client -o yaml | kubectl -n "$NS" apply -f -
kubectl -n "$NS" apply -f locust-pod.yaml
for i in $(seq 1 "$LOCUST_WAIT_ITERS"); do
    phase=$(kubectl -n "$NS" get pod "$LOCUST_POD" -o jsonpath='{.status.phase}' 2>/dev/null || echo Pending)
    echo "[locust] pod phase: $phase"
    [ "$phase" = "Succeeded" ] && break
    [ "$phase" = "Failed" ] && break
    sleep 10
done
kubectl -n "$NS" logs "$LOCUST_POD" | tee "$LOCUST_REPORT" || true
kubectl -n "$NS" delete pod "$LOCUST_POD" --ignore-not-found
kubectl -n "$NS" delete configmap locust-script --ignore-not-found

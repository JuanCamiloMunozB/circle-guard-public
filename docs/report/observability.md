# HU-07 — Stack de Observabilidad

**Responsable:** Jose Manuel Cardona (Ops) · **Puntos:** 8 · **Estado:** ✅ Implementado

---

## 1. Arquitectura

```
Servicios (8) ──── /actuator/prometheus ──→ Prometheus ──→ Grafana (métricas)
     │                                           │
     │          OTLP HTTP :4318                  └──→ AlertManager (alertas)
     └──────────────────────────────────→ Jaeger (trazas)

Pods (stdout/stderr) ──→ Promtail ──→ Loki ──→ Grafana (logs)
```

Stack elegido:
- **Prometheus + Grafana + AlertManager** — vía `kube-prometheus-stack` Helm (ARM64-compatible)
- **Loki + Promtail** — reemplaza ELK; 10× menos RAM, integrado en el mismo Grafana
- **Jaeger all-in-one** — tracing distribuido OTLP, sin dependencias externas para demo

---

## 2. Instrumentación de servicios

### 2.1 Dependencias añadidas (todos los servicios)

```kotlin
runtimeOnly("io.micrometer:micrometer-registry-prometheus")   // expone /actuator/prometheus
implementation("io.micrometer:micrometer-tracing-bridge-otel") // propaga trace IDs
runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")   // envía trazas a Jaeger
```

Gateway-service y file-service también recibieron `spring-boot-starter-actuator` (antes no lo tenían).

### 2.2 Configuración en application.yml (todos los servicios)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}   # etiqueta app= en todas las métricas
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}  # 100% en dev, ajustable en prod
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
```

### 2.3 Variables en ConfigMap

```yaml
OTEL_EXPORTER_OTLP_ENDPOINT: http://jaeger:4318
TRACING_SAMPLING_PROBABILITY: "1.0"
```

### 2.4 Anotaciones en Deployments K8s

Cada Deployment incluye en el pod template:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "<puerto-del-servicio>"
  prometheus.io/path: /actuator/prometheus
```

Prometheus usa `additionalScrapeConfigs` con `kubernetes_sd_configs` (role: pod) para descubrir automáticamente todos los pods anotados en los namespaces `circleguard-dev/stage/master`.

---

## 3. Métricas disponibles

| Métrica | Tipo | Descripción |
|---|---|---|
| `http_server_requests_seconds_*` | Histogram | Request count, latency (P50/P99) por servicio y status |
| `jvm_memory_used_bytes` | Gauge | Heap/non-heap usados |
| `jvm_memory_max_bytes` | Gauge | Heap/non-heap máximos |
| `process_cpu_usage` | Gauge | CPU del proceso JVM |
| `resilience4j_circuitbreaker_state` | Gauge | Estado del circuit breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `spring_kafka_*` | Counter/Gauge | Métricas del consumer/producer Kafka |
| `hikaricp_connections_*` | Gauge | Pool de conexiones DB |

---

## 4. Dashboard Grafana

**Archivo:** `monitoring/dashboards/circleguard-overview.json`  
**ConfigMap:** `monitoring/dashboards/circleguard-overview-configmap.yaml`

El dashboard `CircleGuard — Services Overview` incluye:
- **Request Rate** (req/s por servicio)
- **5xx Error Rate** (% por servicio)
- **HTTP Latency P50/P99** (segundos)
- **JVM Heap Memory** (usado vs máximo)
- **CPU Usage** (% por proceso)
- **Circuit Breaker State** (estado actual)

El Grafana sidecar (label `grafana_dashboard=1`) detecta el ConfigMap y lo carga automáticamente en la carpeta `CircleGuard`.

---

## 5. Alertas (PrometheusRule)

**Archivo:** `monitoring/alerts/circleguard-rules.yaml`

| Alerta | Severidad | Condición |
|---|---|---|
| `CircleGuardServiceDown` | 🔴 critical | Target ausente por >2 min |
| `CircleGuardHighErrorRate` | 🔴 critical | 5xx rate >10% por >2 min |
| `CircleGuardCircuitBreakerOpen` | 🟡 warning | CB en estado OPEN por >1 min |
| `CircleGuardHighLatency` | 🟡 warning | P99 >2s por >5 min |
| `CircleGuardJvmHeapHigh` | 🟡 warning | Heap >85% por >5 min |
| `CircleGuardPodRestartLoop` | 🟡 warning | >3 reinicios en 15 min |

---

## 6. Logs — Loki + Promtail

Promtail recoge automáticamente `stdout/stderr` de todos los pods en los namespaces CircleGuard. Pipeline de procesamiento:
1. Parser Docker (timestamp, stream, log)
2. Multiline join (stacktraces Java → un solo log entry)
3. Extracción de `level` (INFO/WARN/ERROR/DEBUG)
4. Labels: `app`, `namespace`, `pod`, `container`

**Consulta ejemplo en Grafana (LogQL):**
```logql
{app="auth-service"} |= "ERROR" | line_format "{{.level}} {{.log}}"
```

---

## 7. Trazas — Jaeger

**Archivo:** `monitoring/jaeger/jaeger-allinone.yaml`

- **Imagen:** `jaegertracing/all-in-one:1.57` (multi-arch, ARM64 ✓)
- **Backend:** in-memory (10 000 trazas, suficiente para demo)
- **Protocolo:** OTLP HTTP (puerto 4318) — misma lib que usa Micrometer Tracing
- **UI:** NodePort 32686

Cada request HTTP entrante genera un `traceId` que se propaga automáticamente entre servicios. Para ver un flujo completo:

1. Abrir Jaeger UI → buscar servicio `auth-service`
2. Seleccionar una traza → ver spans de `auth-service → identity-service`

---

## 8. Instalación

```bash
# Requisito: AKS encendido y kubectl conectado
az aks start --name aks-cg-dev --resource-group rg-cg-dev
az aks get-credentials --name aks-cg-dev --resource-group rg-cg-dev

# Instalar todo el stack
bash monitoring/install-monitoring.sh

# Acceso
kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring
# → http://localhost:3000  (admin / circleguard-grafana-admin)

kubectl port-forward svc/jaeger-ui 16686:16686 -n monitoring
# → http://localhost:16686

# Desinstalar (antes de apagar AKS)
bash monitoring/install-monitoring.sh --uninstall
az aks stop --name aks-cg-dev --resource-group rg-cg-dev
```

---

## 9. Criterios de aceptación — verificación

| Criterio | Estado | Evidencia |
|---|---|---|
| Servicios exponen `/actuator/prometheus` | ✅ | Dependencia + config en los 8 servicios |
| `/actuator/health` funcionando | ✅ | Ya existía; probes de K8s lo confirman |
| Prometheus + Grafana vía Helm | ✅ | `values-kube-prometheus-stack.yaml` |
| Dashboards versionados en Git | ✅ | `monitoring/dashboards/circleguard-overview.json` |
| Logs centralizados | ✅ | Loki + Promtail (`values-loki-stack.yaml`) |
| Trazas de un flujo completo | ✅ | Jaeger all-in-one + OTLP en los 8 servicios |
| Al menos una alerta crítica | ✅ | 2 críticas + 4 warnings en `circleguard-rules.yaml` |

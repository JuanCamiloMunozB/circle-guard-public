# Observabilidad y Monitoreo — CircleGuard (HU-07)

> Stack completo implementado y desplegado en AKS. Todos los manifiestos y valores Helm
> se encuentran en `monitoring/`. Script de instalación: `monitoring/setup/install-monitoring.sh`.

## Arquitectura de observabilidad

```
┌─────────────────────────────────────────────────────────────┐
│                    Namespace: monitoring                     │
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────────┐  │
│  │  Prometheus  │   │   Grafana    │   │  Alertmanager   │  │
│  │ (scrape cada │──►│  dashboards  │   │  (Slack/email)  │  │
│  │   15s)       │   │              │   │                 │  │
│  └──────┬───────┘   └──────────────┘   └─────────────────┘  │
│         │                                                    │
│  ┌──────┴───────┐   ┌──────────────┐   ┌─────────────────┐  │
│  │     Loki     │   │    Jaeger    │   │  Elasticsearch  │  │
│  │  (log aggr.) │   │  all-in-one  │   │  + Kibana       │  │
│  └──────┬───────┘   └──────┬───────┘   └─────────────────┘  │
│         │ Promtail          │ OTLP                           │
└─────────┼───────────────────┼────────────────────────────────┘
          │                   │
    ┌─────▼───────────────────▼──────────────────────────────┐
    │           Namespace: circleguard-master                 │
    │   auth · identity · form · promotion · notification    │
    │   dashboard · gateway · file                           │
    │   (Micrometer /actuator/prometheus + OTLP traces)      │
    └────────────────────────────────────────────────────────┘
```

## Componentes instalados

| Componente | Método de instalación | Namespace | Archivo de configuración |
|---|---|---|---|
| Prometheus | Helm `kube-prometheus-stack` | `monitoring` | `monitoring/helm/values-kube-prometheus-stack.yaml` |
| Grafana | Incluido en kube-prometheus-stack | `monitoring` | Dashboards en `monitoring/dashboards/` |
| Alertmanager | Incluido en kube-prometheus-stack | `monitoring` | `monitoring/alerts/circleguard-rules.yaml` |
| Loki | Helm `loki-stack` | `monitoring` | `monitoring/helm/values-loki-stack.yaml` |
| Promtail | Incluido en loki-stack (DaemonSet) | `monitoring` | — |
| Jaeger | Manifest YAML (all-in-one) | `monitoring` | `monitoring/jaeger/jaeger-allinone.yaml` |
| Elasticsearch | Manifest YAML | `monitoring` | `monitoring/elk/elasticsearch.yaml` |
| Kibana | Manifest YAML | `monitoring` | `monitoring/elk/kibana.yaml` |

## Instrumentación en los microservicios

### Dependencias (todas las dependencias añadidas en `v0.2.0`)

```kotlin
// build.gradle.kts (raíz)
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

### Configuración `application.yml` (común a todos los servicios)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

spring:
  application:
    name: ${SERVICE_NAME}

otel:
  exporter:
    otlp:
      endpoint: ${OTLP_ENDPOINT:http://jaeger-collector:4318}
  service:
    name: ${SERVICE_NAME}
```

### Annotations de scrape en Deployments K8s

```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/path: "/actuator/prometheus"
    prometheus.io/port: "8080"
```

## Métricas de negocio (Micrometer)

Implementadas con `MeterRegistry` en 4 servicios:

| Servicio | Métrica | Tipo | Descripción |
|---|---|---|---|
| auth-service | `circleguard.auth.login.total` | Counter | Intentos de login (labels: `result=success\|failure`) |
| auth-service | `circleguard.auth.register.total` | Counter | Registros de usuario (labels: `result=success\|failure`) |
| gateway-service | `circleguard.gateway.requests.total` | Counter | Peticiones enrutadas por el gateway (label: `route`) |
| form-service | `circleguard.form.submissions.total` | Counter | Formularios enviados (label: `type`) |
| promotion-service | `circleguard.promotion.created.total` | Counter | Promociones creadas |

## Dashboard Grafana — `circleguard-overview`

ConfigMap: `monitoring/dashboards/circleguard-overview-configmap.yaml`

**Panels incluidos:**

| Panel | Query PromQL | Propósito |
|---|---|---|
| HTTP Request Rate | `rate(http_server_requests_seconds_count[5m])` | Tráfico por servicio |
| HTTP Error Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | Tasa de errores 5xx |
| Latencia p50 | `histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))` | Latencia mediana |
| Latencia p95 | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` | Latencia percentil 95 |
| JVM Heap Used | `jvm_memory_used_bytes{area="heap"}` | Consumo de heap Java |
| Pod Restarts | `kube_pod_container_status_restarts_total` | Reinicios de pods |
| Login total | `circleguard_auth_login_total` | Métrica de negocio |
| Form submissions | `circleguard_form_submissions_total` | Métrica de negocio |

Variable de servicio configurada: el dropdown "Service" filtra todos los panels por `job` label.

## Alertas Prometheus

Archivo: `monitoring/alerts/circleguard-rules.yaml`

| Alerta | Condición | Severidad | Acción |
|---|---|---|---|
| `CircleGuardPodDown` | `kube_pod_status_ready == 0` por > 2 min | critical | Slack + email vía Alertmanager |
| `CircleGuardHighErrorRate` | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05` | warning | Slack |
| `CircleGuardHighLatency` | `histogram_quantile(0.95, ...) > 2` (segundos) | warning | Slack |

## Tracing distribuido (Jaeger)

- Jaeger all-in-one v1.57 en `monitoring/jaeger/jaeger-allinone.yaml`
- Collector expuesto en puerto 4318 (OTLP/HTTP) dentro del cluster
- Todos los servicios envían traces vía `opentelemetry-spring-boot-starter`
- Trace propagation: W3C TraceContext headers entre servicios
- Acceso UI: `kubectl port-forward svc/jaeger-query 16686:16686 -n monitoring`

## Gestión de logs (Loki + ELK)

### Loki + Promtail
- Promtail como DaemonSet recoge los logs de todos los pods (stdout/stderr)
- Etiquetas: `namespace`, `pod`, `container`, `app`
- Consulta desde Grafana: datasource Loki, query `{namespace="circleguard-master"}`

### ELK Stack
- Elasticsearch 8.x como backend de índices
- Kibana 8.x para análisis y visualización
- Logs Java estructurados (Logback JSON) indexados automáticamente
- Acceso Kibana: `kubectl port-forward svc/kibana-kibana 5601:5601 -n monitoring`

## Health checks en Kubernetes

Todos los Deployments incluyen probes configuradas sobre `/actuator/health`:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

Spring Boot Actuator expone `/actuator/health/liveness` y `/actuator/health/readiness` por defecto desde Spring Boot 2.3+.

## Instalación del stack

```bash
# Instalar todo el stack de monitoreo en el cluster actual
bash monitoring/setup/install-monitoring.sh

# Desinstalar
bash monitoring/setup/uninstall-monitoring.sh

# Accesos rápidos tras instalar
kubectl port-forward svc/prometheus-grafana         3000:80    -n monitoring &
kubectl port-forward svc/prometheus-kube-prometheus 9090:9090  -n monitoring &
kubectl port-forward svc/jaeger-query               16686:16686 -n monitoring &
kubectl port-forward svc/kibana-kibana              5601:5601  -n monitoring &
```

Credenciales Grafana por defecto: `admin` / `prom-operator` (override en `values-kube-prometheus-stack.yaml`).

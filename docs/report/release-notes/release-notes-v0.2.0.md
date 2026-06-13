# Release Notes — v0.2.0

**Fecha:** 2026-06-03  
**Tag:** `v0.2.0`  
**Generado por:** `jenkins/scripts/bump-and-tag.sh` al cierre del sprint 2  
**Docker Hub:** `josemanuelhernandez/circleguard-*:v0.2.0`

---

## Resumen

Segunda versión estable. Añade el stack completo de observabilidad (Prometheus + Grafana + Loki + Jaeger + ELK), cierra los quality gates del pipeline (SonarCloud GREEN), implementa RBAC + TLS en AKS y consolida el modelo híbrido de CI/CD. Incluye correcciones críticas de compatibilidad con Docker Engine 29.x, CVEs de Spring Boot y dependencias de seguridad.

---

## Nuevas funcionalidades

### HU-07 — Observabilidad completa
- **Prometheus + Grafana** (kube-prometheus-stack via Helm) con dashboard `circleguard-overview`
  - Panels: HTTP request rate, error rate, latency (p50/p95), JVM memory, pod restarts
  - Panels adicionales: métricas de negocio por servicio (login, registro, formularios, promociones)
- **Loki** (loki-stack Helm) para agregación de logs de todos los pods
- **Jaeger all-in-one** para tracing distribuido OTLP
- **ELK Stack** (Elasticsearch + Kibana) para análisis de logs (`monitoring/elk/`)
- **Micrometer** + `io.micrometer:micrometer-registry-prometheus` en los 8 servicios
- **OpenTelemetry tracing** (`io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`) en los 8 servicios
- `/actuator/prometheus` expuesto en todos los servicios; scrape annotations en Deployments K8s
- Métricas de negocio: contadores en auth, gateway, form y promotion services
- Alertas Prometheus: `circleguard-rules.yaml` (pod-down, alta tasa de errores HTTP, latencia elevada)
- Script de instalación/desinstalación del stack: `monitoring/setup/install-monitoring.sh`

### HU-12 — Seguridad (RBAC + TLS)
- ServiceAccount `jenkins` con Role acotado en cada namespace (`circleguard-dev|stage|master`)
- TLS Ingress para el gateway con cert-manager
- Secretos eliminados del repositorio; solo placeholders `${VAR}` en `casc.yaml`
- Metadatos de verificación Gradle (`gradle/verification-metadata.xml`) con hashes SHA-256

### HU-08 — Pipeline quality gates (cierre)
- Quality Gate SonarCloud: **GREEN** — 0 bugs, 0 vulnerabilidades, 100% hotspots revisados
- Corrección de todos los hallazgos Sonar: path traversal, mass assignment, inyección, ReDoS, CORS
- Gradle dependency verification metadata añadido como gate 0 del pipeline
- Notificaciones Slack + email implementadas y funcionando

### HU-05 — Pipeline híbrido (cierre)
- JCasC (`jenkins/casc.yaml`) con configuración completa del controlador Jenkins
- Imagen multi-arch del controlador Jenkins y manifiestos AKS para el controlador
- Alineación del stage SonarQube entre dev/stage/master (`b060a73`)

---

## Correcciones

| Commit | Descripción |
|---|---|
| `5b140a9` | Revert plugin org.sonarqube a 5.0.0.4638 (7.3.0 no resolvía el problema) |
| `f432307` | Fix(ci): upgrade org.sonarqube 5.0.0 → 7.3.0 para SonarCloud architecture extension |
| `8ae650b` | Fix(test): relaxar threshold PromotionPerformanceTest a 15s para Neo4j embebido en CI |
| `117434d` | Fix(ci): Kafka listener auto-startup, Docker Hub auth para Testcontainers, migración MockitoBean |
| `8076a33` | Fix(tests): inyectar SimpleMeterRegistry en tests rotos por métricas de negocio HU-07 |
| `3781087` | Fix: aumentar timeout Jenkinsfiles a 90 min; corregir commons-io a 2.14.0 |
| `ede880e` | Build: Netty 4.1.133.Final (seguridad) |
| `f9b1910` | Build: Spring Boot BOM 3.5.14 (remediar CVE-2026-40973) |
| `d2395a0` | Build: Spring Boot BOM 3.4.13 (CVEs transitivos) |
| `0c12cae` | Fix: tópicos EmbeddedKafka, env vars Docker socket, config Testcontainers para CI |
| `fd934d5` | Fix: sincronizar envíos Kafka y orden de tests en notificación |
| `2f1324d` | Fix: añadir destinatario email estático de fallback para fallos de pipeline |
| `011adab` | Fix: datasource duplicado Grafana; deshabilitar node-exporter en docker-desktop |
| `b7d64d5` | Fix: `@Autowired` en constructor primario de IdentityClient |

---

## Correcciones de seguridad (SonarCloud)

Resueltos antes del Quality Gate final:

| Categoría | Regla Sonar | Acción |
|---|---|---|
| Path traversal | S2083, S5443 | Validación de rutas en `StorageService` y `FileStorageService` |
| Mass assignment | S4684 (×3) | DTOs dedicados para request bodies |
| Inyección | S5145, S7044 | Validación de identificadores en `MacSessionRegistry` y `CircleService` |
| ReDoS | S5852 | Parseo CORS por coma literal sin regex |
| CORS | S5122 | Orígenes externalizados en ConfigMap |

---

## Componentes incluidos

| Servicio | Imagen Docker Hub |
|---|---|
| auth-service | `josemanuelhernandez/circleguard-auth:v0.2.0` |
| identity-service | `josemanuelhernandez/circleguard-identity:v0.2.0` |
| form-service | `josemanuelhernandez/circleguard-form:v0.2.0` |
| promotion-service | `josemanuelhernandez/circleguard-promotion:v0.2.0` |
| notification-service | `josemanuelhernandez/circleguard-notification:v0.2.0` |
| dashboard-service | `josemanuelhernandez/circleguard-dashboard:v0.2.0` |
| gateway-service | `josemanuelhernandez/circleguard-gateway:v0.2.0` |
| file-service | `josemanuelhernandez/circleguard-file:v0.2.0` |

**Stack de observabilidad (Helm charts):**
- `kube-prometheus-stack` (Prometheus + Grafana + Alertmanager)
- `loki-stack` (Loki + Promtail)
- Jaeger all-in-one `v1.57`
- Elasticsearch 8.x + Kibana 8.x

---

## Notas de operación

- Instalar stack de monitoreo: `bash monitoring/setup/install-monitoring.sh`
- Acceso Grafana: `kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring`
- Acceso Kibana: `kubectl port-forward svc/kibana-kibana 5601:5601 -n monitoring`
- Acceso Jaeger: `kubectl port-forward svc/jaeger-query 16686:16686 -n monitoring`
- Plan de rollback: `kubectl rollout undo deployment/<svc> -n circleguard-<env>`

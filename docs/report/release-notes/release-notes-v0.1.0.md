# Release Notes — v0.1.0

**Fecha:** 2026-05-25  
**Tag:** `v0.1.0`  
**Generado por:** `jenkins/scripts/bump-and-tag.sh` al cierre del sprint 1  
**Docker Hub:** `josemanuelhernandez/circleguard-*:v0.1.0`

---

## Resumen

Primera versión estable de CircleGuard. Cubre la infraestructura base en AKS con Terraform, los 8 microservicios desplegados vía Kubernetes, el pipeline CI/CD híbrido (build local + deploy AKS), los patrones de resiliencia y configuración externa, la suite completa de pruebas y los gates de calidad.

---

## Nuevas funcionalidades

### HU-03 — Infraestructura como Código (Terraform)
- Módulos reutilizables para red (VNet, subnets), AKS y almacenamiento
- Environments `dev`, `stage` y `prod` configurados en Azure
- Backend remoto para estado de Terraform (Azure Blob Storage)
- Diagrama de arquitectura Azure incluido en `docs/infra/`

### HU-04 — Manifiestos Kubernetes
- Deployments, Services y ConfigMaps para los 8 microservicios
- Gateway y file-service expuestos vía NodePort
- Dockerfiles con usuario no-root en todos los servicios (`018e6b5`)

### HU-02 — Configuración Externa
- Todos los `application.yml` externalizados con `${VAR:default}` (Spring relaxed binding)
- `VAULT_SECRET` / `VAULT_SALT` en Secret compartido de K8s
- Orígenes CORS externalizados (remedia Sonar S5122)

### HU-06 — Circuit Breaker (Resiliencia)
- Resilience4j Circuit Breaker envolviendo todas las llamadas REST inter-servicios
- Configuración en `application.yml` de cada servicio consumidor
- Tests unitarios de comportamiento con estado OPEN/CLOSED/HALF_OPEN

### HU-10 — Feature Toggle
- Toggle `sms.alerts.enabled` en `notification-service` para habilitar/deshabilitar alertas SMS sin redespliegue
- Documentado en `docs/report/feature-toggle.md`

### HU-09 — Suite de Pruebas
- Pruebas unitarias en los 8 servicios (JUnit5 + Mockito)
- Pruebas de integración con Testcontainers: EmbeddedKafka, Neo4j Harness, jedis-mock, H2
- Pruebas E2E: file-service, gateway, notification-service
- Pruebas de integración inter-servicio: gateway/dashboard/identity
- Pruebas de rendimiento con Locust: `GatewayUser` y `FileUser`
- JaCoCo configurado con umbral mínimo de 80%; form-service alcanza 92%

### HU-05 (parcial) — Pipeline CI/CD híbrido
- `Jenkinsfile.dev`: build local + tests + Sonar + Kaniko + deploy kind (opcional)
- `Jenkinsfile.stage`: build local + deploy AKS-stage + E2E + ZAP + Locust
- `Jenkinsfile.master`: build local + Trivy + semver tag + aprobación manual + deploy AKS-master
- Agente `local-kaniko` (WSL2 + Docker Desktop) para build en los 3 pipelines

### HU-08 (parcial) — Quality Gates
- SonarCloud análisis estático (`waitForQualityGate abortPipeline:true`)
- Trivy escaneo de imágenes (CRITICAL/HIGH bloqueante) en master
- OWASP ZAP baseline en stage
- Versionado semántico automático con `bump-and-tag.sh`
- Notificaciones de fallo: Slack `#ci-alerts` + email SMTP Gmail

---

## Correcciones y mejoras

- Diagramas de infraestructura actualizados con rutas correctas de Docker Hub (`d5b8d1d`)
- Dockerfiles endurecidos con usuario no-root (`018e6b5`)
- Gateway y file-service añadidos a los 3 Jenkinsfiles (`0cc104a`)
- Script de release notes mejorado con clasificación de tipos de commit adicionales (`1b78200`)
- Pub-sub: diagrama corregido; Kafka auto-startup deshabilitado en perfil de test (`c77a952`)

---

## Componentes incluidos

| Servicio | Imagen Docker Hub |
|---|---|
| auth-service | `josemanuelhernandez/circleguard-auth:v0.1.0` |
| identity-service | `josemanuelhernandez/circleguard-identity:v0.1.0` |
| form-service | `josemanuelhernandez/circleguard-form:v0.1.0` |
| promotion-service | `josemanuelhernandez/circleguard-promotion:v0.1.0` |
| notification-service | `josemanuelhernandez/circleguard-notification:v0.1.0` |
| dashboard-service | `josemanuelhernandez/circleguard-dashboard:v0.1.0` |
| gateway-service | `josemanuelhernandez/circleguard-gateway:v0.1.0` |
| file-service | `josemanuelhernandez/circleguard-file:v0.1.0` |

---

## Notas de operación

- Pre-condición para stage/master: AKS encendido manualmente antes del build.
- Variables de entorno: ver `.env.example` en la raíz.
- Plan de rollback: `kubectl rollout undo deployment/<svc> -n circleguard-<env>`.

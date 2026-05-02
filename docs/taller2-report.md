# Taller 2: Pruebas y Lanzamiento — CircleGuard

**Fecha:** 2026-05-01  
**Proyecto:** CircleGuard — Sistema de rastreo de contactos universitario  
**Repositorio:** circle-guard-public

---

## 1. Microservicios Seleccionados

Se seleccionaron 6 microservicios que forman una cadena de comunicación completa, tanto síncrona (HTTP/REST) como asíncrona (Apache Kafka):

| Servicio               | Puerto | Tecnología                          |
|------------------------|--------|-------------------------------------|
| circleguard-auth-service       | 8180   | Spring Boot 3.2.4, JWT, LDAP        |
| circleguard-identity-service   | 8083   | Spring Boot 3.2.4, PostgreSQL, Kafka |
| circleguard-form-service       | 8086   | Spring Boot 3.2.4, PostgreSQL, Kafka |
| circleguard-promotion-service  | 8088   | Spring Boot 3.2.4, Neo4j, Redis, Kafka |
| circleguard-notification-service | 8082 | Spring Boot 3.2.4, Kafka Consumer, Twilio |
| circleguard-dashboard-service  | 8084   | Spring Boot 3.2.4, PostgreSQL       |

### Diagrama de comunicación

```
[auth-service] ──HTTP──> [identity-service] ──Kafka (audit.identity.accessed)──> [notification-service]
                                                                                          ^
[dashboard-service] ──HTTP──> [promotion-service] <──Kafka (survey.submitted)── [form-service]
                                      |
                                      └──Kafka (promotion.status.changed, alert.priority)──> [notification-service]
```

---

## 2. Configuración: Jenkins, Docker y Kubernetes (Actividad 1 — 10%)

### 2.1 Infraestructura con Docker Compose (desarrollo local)

El archivo `docker-compose.dev.yml` provee toda la infraestructura base:
- **PostgreSQL 16**: bases de datos para auth, identity, form, promotion, dashboard
- **Neo4j 5.26**: grafo de contactos para promotion-service
- **Apache Kafka + Zookeeper**: mensajería asíncrona entre servicios
- **Redis 7.2**: caché para gateway y promotion-service
- **OpenLDAP**: directorio universitario para autenticación

### 2.2 Dockerfiles

Se crearon Dockerfiles para cada uno de los 6 servicios en `services/<nombre>/Dockerfile`, usando la imagen base `eclipse-temurin:21-jre-alpine` para minimizar el tamaño de imagen.

**Ejemplo (auth-service):**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8180
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.3 Kubernetes

Se crearon manifiestos en `k8s/`:

```
k8s/
├── namespaces.yaml           # circleguard-dev, circleguard-stage, circleguard-master
├── infrastructure/
│   ├── postgres.yaml         # Deployment + Service + PVC + ConfigMap init-db
│   ├── kafka.yaml            # Zookeeper + Kafka Deployment + Services
│   ├── redis.yaml            # Redis Deployment + Service
│   └── neo4j.yaml            # Neo4j Deployment + Service + PVC
└── services/
    ├── auth-service.yaml
    ├── identity-service.yaml
    ├── form-service.yaml
    ├── promotion-service.yaml
    ├── notification-service.yaml
    └── dashboard-service.yaml
```

Cada manifiesto de servicio incluye:
- **ConfigMap** con variables de entorno (URLs de DB, Kafka, Redis)
- **Deployment** con replicas=1, readinessProbe y livenessProbe sobre `/actuator/health`
- **Service** tipo ClusterIP para comunicación interna

---

## 3. Pipelines por Ambiente (Actividades 2, 4 y 5)

Se crearon tres Jenkinsfiles en `jenkins/`:

### 3.1 Pipeline Dev (`Jenkinsfile.dev`)

**Etapas:**
1. `Checkout` — clona el repositorio
2. `Build & Unit Tests` — ejecuta tests de los 6 servicios con `./gradlew test`
3. `Build JARs` — genera los fat-JARs en paralelo
4. `Build Docker Images` — construye y etiqueta imágenes como `dev-<BUILD_NUMBER>`
5. `Deploy to Dev` — aplica manifiestos K8s al namespace `circleguard-dev`
6. `Wait for Rollout` — espera que todos los deployments estén listos (timeout 120s)
7. `Smoke Tests` — valida `/actuator/health` en cada servicio via `kubectl exec`

**Configuración:**
```groovy
environment {
    REGISTRY      = 'circleguard'
    K8S_NAMESPACE = 'circleguard-dev'
}
```

### 3.2 Pipeline Stage (`Jenkinsfile.stage`)

Agrega sobre Dev:
- Etapa `Integration Tests` con perfil `integration`
- Etapa `System Tests` contra el ambiente desplegado
- Etapa `Performance Baseline` con Locust: 10 usuarios, 60 segundos, reporte HTML

### 3.3 Pipeline Master (`Jenkinsfile.master`)

Pipeline completo de producción:
1. Unit Tests + Integration Tests
2. Build JARs y Docker con tag `v<BUILD_NUMBER>`
3. Deploy a namespace `circleguard-master`
4. System Validation Tests
5. Performance Tests (50 usuarios, 120 segundos, CSV + HTML)
6. **Generate Release Notes** — genera `docs/release-notes-v<N>.md` automáticamente
7. **Tag Release** — crea tag git `v<N>`

---

## 4. Pruebas Implementadas (Actividad 3 — 30%)

### 4.1 Pruebas Unitarias (nuevas)

Todas las pruebas se basan en Mockito + JUnit 5 y prueban componentes individuales sin infraestructura externa.

| # | Clase | Servicio | Qué valida |
|---|-------|----------|------------|
| 1 | `HealthSurveyServiceTest` | form-service | Survey sin síntomas publica evento `survey.submitted` con `hasSymptoms=false` |
| 2 | `HealthSurveyServiceTest` | form-service | Survey con síntomas activa `hasFever=true` y `hasCough=true` |
| 3 | `HealthSurveyServiceTest` | form-service | Survey con `attachmentPath` asigna `ValidationStatus.PENDING` |
| 4 | `HealthSurveyServiceTest` | form-service | Validación APPROVED publica evento `certificate.validated` con `adminId` |
| 5 | `HealthSurveyServiceTest` | form-service | Validación REJECTED NO publica `certificate.validated` |
| 6 | `SymptomMapperEdgeCasesTest` | form-service | `null` responses retorna `false` |
| 7 | `SymptomMapperEdgeCasesTest` | form-service | Pregunta de dificultad respiratoria detecta síntoma |
| 8 | `SymptomMapperEdgeCasesTest` | form-service | Respuesta YES en pregunta irrelevante no activa síntoma |
| 9 | `SymptomMapperEdgeCasesTest` | form-service | Cuestionario vacío siempre retorna `false` |
| 10 | `SymptomMapperEdgeCasesTest` | form-service | Múltiples preguntas: detecta tos entre otras |
| 11 | `IdentityVaultServiceTest` | identity-service | Misma identidad siempre retorna el mismo UUID |
| 12 | `IdentityVaultServiceTest` | identity-service | Nueva identidad se persiste en repositorio |
| 13 | `IdentityVaultServiceTest` | identity-service | Identidades distintas producen UUIDs distintos |
| 14 | `IdentityVaultServiceTest` | identity-service | UUID desconocido lanza `ResponseStatusException` 404 |
| 15 | `IdentityVaultServiceTest` | identity-service | UUID conocido retorna la identidad real |
| 16 | `StatusLifecycleServiceTest` | promotion-service | Sin usuarios expirados NO publica eventos Kafka |
| 17 | `StatusLifecycleServiceTest` | promotion-service | Usuarios expirados publican `promotion.status.changed` |
| 18 | `StatusLifecycleServiceTest` | promotion-service | `SystemSettings` faltante lanza `IllegalStateException` |
| 19 | `AnalyticsServiceTest` | dashboard-service | `getCampusSummary` delega a `PromotionClient` |
| 20 | `AnalyticsServiceTest` | dashboard-service | Población pequeña activa K-Anonymity masking |
| 21 | `AnalyticsServiceTest` | dashboard-service | Población grande NO es enmascarada |
| 22 | `AnalyticsServiceTest` | dashboard-service | Tabla inexistente retorna datos mock (fallback) |
| 23 | `AnalyticsServiceTest` | dashboard-service | Período `daily` consulta con truncación por día |

### 4.2 Pruebas de Integración (nuevas)

Prueban la comunicación entre componentes dentro del contexto Spring completo.

| # | Clase | Servicios involucrados | Qué valida |
|---|-------|------------------------|------------|
| 1 | `SurveyKafkaPublishIntegrationTest` | form-service | Payload de `survey.submitted` contiene `anonymousId`, `hasSymptoms` y `timestamp` |
| 2 | `SurveyKafkaPublishIntegrationTest` | form-service | Payload de `certificate.validated` contiene `adminId` y `status=APPROVED` |
| 3 | `SurveyListenerToServiceIntegrationTest` | promotion-service | `SurveyListener` → `HealthStatusService.updateStatus("SUSPECT")` con síntomas |
| 4 | `SurveyListenerToServiceIntegrationTest` | promotion-service | Sin síntomas NO llama a `HealthStatusService` |
| 5 | `DashboardPromotionClientIntegrationTest` | dashboard-service → promotion-service | `getCampusSummary` retorna datos del `PromotionClient` en contexto Spring |
| 6 | `DashboardPromotionClientIntegrationTest` | dashboard-service → promotion-service | K-Anonymity aplicada en departamento con población < 5 |
| 7 | `DashboardPromotionClientIntegrationTest` | dashboard-service → promotion-service | Fallo del `PromotionClient` retorna mapa de error sin lanzar excepción |
| 8 | `StatusChangeNotificationIntegrationTest` | notification-service | Estado SUSPECT dispara `dispatcher.dispatch` y `lmsService.syncRemoteAttendance` |
| 9 | `StatusChangeNotificationIntegrationTest` | notification-service | Estado ACTIVE NO dispara notificaciones |
| 10 | `StatusChangeNotificationIntegrationTest` | notification-service | JSON malformado no lanza excepción y no despacha notificación |
| 11 | `IdentityMappingIntegrationTest` | identity-service | POST `/identities/map` retorna `anonymousId` válido |
| 12 | `IdentityMappingIntegrationTest` | identity-service | POST `/identities/visitor` crea mapping compuesto con prefijo `VISITOR|` |

### 4.3 Pruebas E2E (nuevas)

Prueban flujos completos de usuario contra los servicios desplegados, usando RestAssured. Ubicadas en `tests/e2e/`.

| # | Clase | Flujo | Qué valida |
|---|-------|-------|------------|
| 1 | `HealthSurveyFlowE2ETest` | Health check | identity-service `/actuator/health` retorna UP |
| 2 | `HealthSurveyFlowE2ETest` | Health check | form-service `/actuator/health` retorna UP |
| 3 | `HealthSurveyFlowE2ETest` | Submit survey | POST `/surveys` retorna ID persistido |
| 4 | `HealthSurveyFlowE2ETest` | Health check | promotion-service `/actuator/health` retorna UP |
| 5 | `HealthSurveyFlowE2ETest` | Health check | dashboard-service `/actuator/health` retorna UP |
| 6 | `IdentityMappingFlowE2ETest` | Identity mapping | Nueva identidad retorna UUID válido |
| 7 | `IdentityMappingFlowE2ETest` | Identity idempotency | Misma identidad dos veces → mismo UUID |
| 8 | `IdentityMappingFlowE2ETest` | Visitor flow | Visitante obtiene UUID sin exponer identidad real |
| 9 | `DashboardStatsFlowE2ETest` | Campus summary | Endpoint `/analytics/summary` responde correctamente |
| 10 | `DashboardStatsFlowE2ETest` | Department stats | Departamento grande no aplica masking |
| 11 | `DashboardStatsFlowE2ETest` | Time series | `/analytics/time-series` retorna lista de puntos |
| 12 | `CertificateValidationFlowE2ETest` | Certificate flow | Survey con attachment inicia en estado PENDING |
| 13 | `CertificateValidationFlowE2ETest` | Certificate flow | GET `/surveys/pending` requiere autenticación o retorna lista |
| 14 | `CertificateValidationFlowE2ETest` | Auth health | auth-service `/actuator/health` retorna UP |
| 15 | `CertificateValidationFlowE2ETest` | Questionnaire | GET `/questionnaires/active` responde correctamente |

### 4.4 Pruebas de Rendimiento con Locust

Archivo: `tests/performance/locustfile.py`

#### Usuarios simulados

| Clase | Peso | Operaciones principales |
|-------|------|------------------------|
| `StudentUser` | 10 (mayoría) | Submit survey sano (task weight=10), submit con síntomas (2), fetch questionnaire (3), ver historial (1) |
| `AdminUser` | 2 | Listar encuestas pendientes (5), validar certificado (3) |
| `DashboardUser` | 1 | Campus summary (5), dept stats (3), time series (2) |

#### Escenarios de ejecución

**Dev baseline (stage pipeline):**
```bash
locust -f tests/performance/locustfile.py --headless -u 10 -r 2 -t 60s \
    --host http://<form-service>:8086
```

**Master (producción):**
```bash
locust -f tests/performance/locustfile.py --headless -u 50 -r 5 -t 120s \
    --host http://<form-service>:8086 --csv locust-master
```

**Stress test:**
```bash
locust -f tests/performance/locustfile.py --headless -u 200 -r 20 -t 300s \
    --host http://<form-service>:8086
```

#### Métricas objetivo (NFR-1 del sistema)

| Métrica | Objetivo | Umbral de fallo |
|---------|----------|-----------------|
| P50 response time | < 200ms | > 500ms |
| P95 response time | < 1000ms | > 2000ms |
| Throughput | > 50 req/s con 50 usuarios | < 20 req/s |
| Error rate | < 1% | > 5% |

---

## 5. Generación Automática de Release Notes (Actividad 5)

El `Jenkinsfile.master` genera automáticamente un archivo `docs/release-notes-v<N>.md` en la etapa `Generate Release Notes`, incluyendo:

- Fecha, número de build, hash de commit y autor
- Tabla de servicios desplegados con su imagen tag
- Lista de commits desde el tag anterior (`git describe --tags`)
- Resumen de tests: total ejecutados y fallos
- Referencia al namespace Kubernetes destino

Los release notes se archivan como artefacto de Jenkins y se almacenan en el repositorio bajo `docs/`.

---

## 6. Estructura de Archivos Creados

```
circle-guard-public/
├── k8s/
│   ├── namespaces.yaml
│   ├── infrastructure/
│   │   ├── postgres.yaml
│   │   ├── kafka.yaml
│   │   ├── redis.yaml
│   │   └── neo4j.yaml
│   └── services/
│       ├── auth-service.yaml
│       ├── identity-service.yaml
│       ├── form-service.yaml
│       ├── promotion-service.yaml
│       ├── notification-service.yaml
│       └── dashboard-service.yaml
├── jenkins/
│   ├── Jenkinsfile.dev
│   ├── Jenkinsfile.stage
│   └── Jenkinsfile.master
├── services/
│   ├── circleguard-auth-service/Dockerfile
│   ├── circleguard-identity-service/Dockerfile
│   ├── circleguard-form-service/
│   │   └── src/test/java/com/circleguard/form/
│   │       ├── service/HealthSurveyServiceTest.java       (5 pruebas unitarias)
│   │       ├── service/SymptomMapperEdgeCasesTest.java    (5 pruebas unitarias)
│   │       └── integration/SurveyKafkaPublishIntegrationTest.java (2 pruebas integración)
│   ├── circleguard-identity-service/
│   │   └── src/test/java/com/circleguard/identity/
│   │       ├── service/IdentityVaultServiceTest.java      (5 pruebas unitarias)
│   │       └── integration/IdentityMappingIntegrationTest.java (2 pruebas integración)
│   ├── circleguard-promotion-service/
│   │   └── src/test/java/com/circleguard/promotion/
│   │       ├── service/StatusLifecycleServiceTest.java    (3 pruebas unitarias)
│   │       └── integration/SurveyListenerToServiceIntegrationTest.java (2 integración)
│   ├── circleguard-notification-service/
│   │   └── src/test/java/com/circleguard/notification/
│   │       └── integration/StatusChangeNotificationIntegrationTest.java (3 integración)
│   └── circleguard-dashboard-service/
│       └── src/test/java/com/circleguard/dashboard/
│           ├── service/AnalyticsServiceTest.java          (5 pruebas unitarias)
│           └── integration/DashboardPromotionClientIntegrationTest.java (3 integración)
├── tests/
│   ├── e2e/
│   │   ├── build.gradle.kts
│   │   └── src/test/java/com/circleguard/e2e/
│   │       ├── BaseE2ETest.java
│   │       ├── HealthSurveyFlowE2ETest.java     (5 pruebas E2E)
│   │       ├── IdentityMappingFlowE2ETest.java  (3 pruebas E2E)
│   │       ├── DashboardStatsFlowE2ETest.java   (3 pruebas E2E)
│   │       └── CertificateValidationFlowE2ETest.java (4 pruebas E2E)
│   └── performance/
│       └── locustfile.py                        (3 user classes, múltiples escenarios)
└── docs/
    └── taller2-report.md                        (este documento)
```

---

*Documento generado como parte del Taller 2 de Ingeniería de Software V — 2026-05-01*

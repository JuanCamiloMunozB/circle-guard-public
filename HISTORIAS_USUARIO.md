# HISTORIAS DE USUARIO Y GESTIÓN ÁGIL — CircleGuard

## 1. Marco metodológico

**Metodología:** Kanban gestionado en **GitHub Projects** (tablero del repositorio).
**Equipo:**
- **Juan Camilo Muñoz — Dev:** código de servicios, patrones de diseño, pruebas de aplicación.
- **Jose Manuel Cardona — Ops:** Terraform, Kubernetes, Jenkins, observabilidad, seguridad de infraestructura.

**Ventana:** 15 días, del **22 de mayo al 8 de junio de 2026**, dividida en dos iteraciones.

**Entorno y restricciones:** máquina local **Windows** (comandos bash vía Git Bash o WSL Ubuntu);
**100 USD de crédito Azure** (AKS solo encendido para stage/prod/observabilidad/Locust); registro
**Docker Hub público**; build con **Kaniko**.

### Columnas del tablero Kanban (GitHub Projects)
`Backlog` → `Ready` (refinada, lista para tomar) → `In Progress` → `In Review` (PR abierto)
→ `Done` (mergeada a `develop` y DoD cumplida).

**Límite WIP (Work In Progress):** máximo **2 tarjetas** simultáneas por persona. Kanban
limita el trabajo en curso para forzar que se termine antes de empezar lo siguiente; con un
equipo de 2 y plazo corto, un WIP de 2 evita dispersión.

### Estimación
Puntos de historia en escala Fibonacci (1, 2, 3, 5, 8). 1 punto ≈ media jornada de trabajo
sin bloqueos. La capacidad estimada del equipo es ~40 puntos en 15 días.

---

## 2. Iteración 1 — Fundaciones (22 may – 30 may)

Objetivo: dejar los 8 servicios desplegables en la nube con infraestructura como código,
configuración externalizada y un pipeline mínimo extremo a extremo. Sin esto, nada más se
puede demostrar.

### HU-01 — Completar microservicios huérfanos
- **Como** equipo de arquitectura, **quiero** que gateway-service y file-service estén
  completos y dockerizados, **para** cumplir el alcance de 8 microservicios.
- **Criterios de aceptación:**
  - file-service y gateway-service tienen Dockerfile multistage con usuario no-root.
  - Ambos compilan vía `./gradlew build` y aparecen en el pipeline de build.
  - Existe al menos un flujo donde gateway enruta a un servicio downstream.
- **Puntos:** 5 · **Prioridad:** Alta · **Responsable:** Juan Camilo (Dev)

### HU-02 — Externalizar configuración (env vars + ConfigMap/Secret)
- **Como** operador, **quiero** que toda la configuración sensible y de entorno se inyecte
  por variables, **para** alternar entre local y nube sin tocar el código.
- **Criterios de aceptación:**
  - Ningún `application.yml` contiene contraseñas, secretos JWT ni URLs fijas; usan
    placeholders `${VAR:default}`.
  - Cada servicio toma su configuración de un ConfigMap (no sensible) y un Secret (sensible).
  - El sistema arranca en local con los defaults y en AKS con los valores inyectados.
- **Puntos:** 5 · **Prioridad:** Alta · **Responsable:** Juan Camilo (Dev) + Jose Manuel (Ops)

### HU-03 — Infraestructura base en Azure con Terraform
- **Como** operador, **quiero** provisionar AKS y la red con Terraform modular y estado
  remoto, **para** tener infraestructura reproducible y versionada.
- **Criterios de aceptación:**
  - `infra/terraform/` con módulos reutilizables y carpetas por ambiente (dev/stage/prod).
  - Estado en backend remoto (Azure Storage) con bloqueo.
  - `terraform plan` válido en los 3 ambientes; `apply` en dev levanta AKS.
  - Recursos apagados tras validar (evidencia del apagado).
- **Puntos:** 8 · **Prioridad:** Alta · **Responsable:** Jose Manuel (Ops)

### HU-04 — Manifiestos Kubernetes parametrizados para los 8 servicios
- **Como** operador, **quiero** Deployments y Services con probes y referencias a
  ConfigMap/Secret, **para** desplegar el sistema completo en AKS.
- **Criterios de aceptación:**
  - Los 8 servicios tienen Deployment + Service + liveness/readiness probes.
  - Las variables provienen de ConfigMap/Secret (no valores en el YAML).
  - `kubectl apply` despliega el stack y los pods quedan en estado Ready.
- **Puntos:** 5 · **Prioridad:** Alta · **Responsable:** Jose Manuel (Ops)

### HU-05 — Pipeline Jenkins híbrido (agente local dev + agentes AKS stage/prod) con Kaniko
- **Como** equipo, **quiero** que dev construya en un agente local con Kaniko y stage/prod en
  pods AKS, **para** proteger el crédito de Azure y no consumir disco local.
- **Criterios de aceptación:**
  - dev usa agente local + Kaniko y publica en Docker Hub sin encender AKS.
  - stage/prod usan pods efímeros en AKS que se destruyen al terminar.
  - Las imágenes se construyen con Kaniko (no acumulan capas en disco local).
  - El pipeline dev ejecuta: checkout → build → unit tests → build imagen → push.
- **Puntos:** 8 · **Prioridad:** Alta · **Responsable:** Jose Manuel (Ops)

**Total Iteración 1: 31 puntos.**

---

## 3. Iteración 2 — Calidad, observabilidad y cierre (31 may – 8 jun)

Objetivo: añadir las capas de calidad, seguridad, observabilidad y patrones sobre la base
desplegable, y consolidar la documentación y la evidencia para la presentación.

### HU-06 — Patrón de resiliencia: Circuit Breaker
- **Como** arquitecto, **quiero** circuit breakers en las llamadas REST inter-servicio,
  **para** que un fallo aislado no derribe el flujo completo.
- **Criterios de aceptación:**
  - Resilience4j aplicado en al menos auth→identity y dashboard→promotion.
  - Existe un fallback verificable cuando el servicio downstream está caído.
  - Documentado en `docs/report/patterns.md` (propósito + beneficio).
- **Puntos:** 5 · **Prioridad:** Alta · **Responsable:** Juan Camilo (Dev)

### HU-07 — Stack de observabilidad (Prometheus, Grafana, ELK, tracing)
- **Como** operador, **quiero** métricas, logs centralizados y trazas, **para** monitorear
  el sistema y demostrar observabilidad.
- **Criterios de aceptación:**
  - Servicios exponen `/actuator/prometheus` y health endpoints.
  - Prometheus + Grafana desplegados vía Helm; dashboards versionados.
  - ELK recoge logs; Jaeger muestra trazas de un flujo completo.
  - Al menos una alerta crítica configurada.
- **Puntos:** 8 · **Prioridad:** Alta · **Responsable:** Jose Manuel (Ops)

### HU-08 — Calidad y seguridad en el pipeline (SonarQube, Trivy, ZAP, semver)
- **Como** equipo, **quiero** análisis estático, escaneo de imágenes y de seguridad más
  versionado automático, **para** cumplir el rubro de CI/CD avanzado y seguridad.
- **Criterios de aceptación:**
  - SonarQube con quality gate bloqueante; Trivy falla ante vulnerabilidades críticas.
  - OWASP ZAP baseline contra servicios expuestos en stage.
  - Versionado semántico derivado de Conventional Commits; aprobación manual a prod.
- **Puntos:** 8 · **Prioridad:** Media · **Responsable:** Jose Manuel (Ops) + Juan Camilo (Dev)

### HU-09 — Suite de pruebas completa y de estrés en Azure
- **Como** equipo, **quiero** unitarias, integración, E2E y Locust ejecutándose en la nube,
  **para** evidenciar calidad sin cargar las máquinas locales.
- **Criterios de aceptación:**
  - Cobertura unitaria ≥70% en los servicios de menor cobertura (JaCoCo).
  - Integración con Testcontainers y E2E verdes en pipeline.
  - Locust ejecutado contra el despliegue de stage en AKS; reportes archivados.
- **Puntos:** 5 · **Prioridad:** Media · **Responsable:** Juan Camilo (Dev)

### HU-10 — Feature Toggle, Change Management y Release Notes
- **Como** equipo, **quiero** el patrón Feature Toggle, el proceso de cambios y release notes
  automáticas, **para** cerrar los rubros de patrones y release management.
- **Criterios de aceptación:**
  - Feature Toggle implementado: una flag parametrizada activa/desactiva una función sin redeploy.
  - Publisher-Subscriber (Kafka) documentado como patrón existente (solo documentación).
  - Release Notes generadas automáticamente; plan de rollback documentado; tags de release.
- **Puntos:** 3 · **Prioridad:** Media · **Responsable:** Juan Camilo (Dev)

### HU-11 — Consolidación documental y material de presentación
- **Como** equipo, **quiero** la documentación modular consolidada y los diagramas PlantUML,
  **para** entregar el informe y la presentación.
- **Criterios de aceptación:**
  - Diagramas de arquitectura en PlantUML (`docs/diagrams/*.puml`) renderizados.
  - Documentación por componentes consolidada en `docs/report/taller2-report.md`.
  - Costos de infraestructura documentados; manual de operaciones básico; guion de video.
- **Puntos:** 5 · **Prioridad:** Media · **Responsable:** Jose Manuel (Ops) + Juan Camilo (Dev)

**Total Iteración 2: 34 puntos.**

---

## 4. Resumen de asignación

| Responsable | Historias | Puntos |
|---|---|---|
| Juan Camilo Muñoz (Dev) | HU-01, HU-06, HU-09, HU-10 + mitad de HU-02/HU-08/HU-11 | ~28 |
| Jose Manuel Cardona (Ops) | HU-03, HU-04, HU-05, HU-07 + mitad de HU-02/HU-08/HU-11 | ~37 |

La carga de Ops es mayor en la Iteración 1 (infraestructura intensiva); la de Dev se
concentra en patrones y pruebas en la Iteración 2. Las historias compartidas (HU-02, HU-08,
HU-11) son los puntos de sincronización entre ambos roles.

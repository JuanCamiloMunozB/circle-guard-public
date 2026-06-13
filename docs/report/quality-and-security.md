# Calidad y seguridad de pipeline — CircleGuard

> Política de quality gates y escaneo de seguridad en los pipelines Jenkins.
> Estado actual: todas las fases implementadas y verificadas con pipelines reales corriendo
> contra AKS (stage y master) y localmente (dev).

## Capas de gate (todas bloqueantes)

| # | Gate | Herramienta | Ubicación en el pipeline | Criterio de fallo |
|---|---|---|---|---|
| 0 | Dependency verification | Gradle dependency-verification (`gradle/verification-metadata.xml`) | Resolución de dependencias, antes de compilar | SHA-256 de cualquier artefacto descargado no coincide con el hash fijado → Gradle aborta |
| 1 | Unit + Integration tests | JUnit5 + Testcontainers (EmbeddedKafka, Neo4j Harness, jedis-mock, H2) | Antes de Sonar | Cualquier test falla |
| 2 | Code coverage | JaCoCo 0.8.11 | Reporte agregado por `jacocoTestReport` | < 80% líneas (`jacocoTestCoverageVerification`) |
| 3 | Static analysis + coverage | SonarCloud (`org.sonarqube` plugin + `withSonarQubeEnv` + `waitForQualityGate`) | Después de Integration, antes de Build JARs | Quality gate falla. `abortPipeline: true` corta el build |
| 4 | Container vulnerabilities | Trivy (`aquasec/trivy:latest`) | Solo `Jenkinsfile.master`, después de Kaniko, antes de Deploy | Cualquier CVE `CRITICAL` o `HIGH` sin parche. `.trivyignore` documenta las supresiones |
| 5 | Dynamic security scan | OWASP ZAP baseline (`ghcr.io/zaproxy/zaproxy:stable`) | Solo `Jenkinsfile.stage`, después de E2E, antes de Performance | Cualquier rule `FAIL` (exit code 1). `WARN` no bloquea pero queda en el reporte archivado |
| 6 | Manual approval | Jenkins `input` | Solo `Jenkinsfile.master`, después de Trivy + semver tag, antes de Deploy a master | Operador rechaza o timeout de 30 min |

Cualquier fallo dispara `post.failure`: `slackSend` a `#ci-alerts` + `emailext` a developers y culprits.

## Semantic versioning automático

| Aspecto | Detalle |
|---|---|
| Script | `jenkins/scripts/bump-and-tag.sh` |
| Entrada | Commits desde el último tag (`git describe --tags --abbrev=0`, fallback `v0.0.0`) |
| Reglas | `BREAKING CHANGE:` / `feat!:` → major. `feat:` → minor. `fix:` / `perf:` / `refactor:` → patch |
| Salida | Tag anotado `vMAJOR.MINOR.PATCH` creado con `git tag -a -m`, pusheado a origin |
| Pipeline | Stage `Semantic Version Tag` en `Jenkinsfile.master`, entre Trivy y la aprobación manual |
| Re-tagging de imágenes | `crane copy` etiqueta las 8 imágenes en Docker Hub con `vN.M.P` además de `v${BUILD_NUMBER}` |
| Tags generados | `v0.1.0` (2026-05-25) y `v0.2.0` (2026-06-03) |

## Notificaciones de fallo

| Canal | Plugin Jenkins | Credencial | Trigger |
|---|---|---|---|
| Slack | `slack` (Incoming Webhook), `slackNotifier.baseUrl` apunta al prefijo de hooks.slack.com | `slack-webhook` (string credential con `${SLACK_WEBHOOK_TOKEN}`) | `post.failure` + `post.success` en los 3 Jenkinsfiles |
| Email | `email-ext` + `mailer`, SMTP Gmail | `smtp-credentials` (`${SMTP_USERNAME}` / `${SMTP_PASSWORD}` = App Password) | `post.failure` con `developers() + culprits()` |

El mensaje incluye job, número de build, rama y enlace a la consola (`${env.BUILD_URL}console`).

## Remediación del Quality Gate (SonarCloud)

El gate de SonarCloud (proyecto `JuanCamiloMunozB_circle-guard-public`, evaluado sobre **código nuevo**) pasó a **GREEN** tras corregir las causas raíz sin bajar umbrales ni añadir exclusiones:

| Categoría | Reglas Sonar | Acción aplicada |
|---|---|---|
| Bugs de fiabilidad | S2229, S2119 | Corrección directa en el servicio afectado |
| Path traversal | S2083, S5443 | Saneo y validación de rutas en `StorageService` y `FileStorageService` |
| Mass assignment | S4684 (×3) | Request bodies vinculados a DTOs dedicados (`HealthSurveyRequest`, `QuestionnaireRequest`, `SystemSettingsRequest`) |
| Inyección | S5145, S7044 | Validación/escape de identificadores en `MacSessionRegistry` y `CircleService` |
| ReDoS / CORS | S5852, S5122 | Parseo de orígenes CORS por coma literal sin regex; orígenes externalizados |
| CSRF (hotspots) | S4502 | Marcados SAFE: API REST stateless (`SessionCreationPolicy.STATELESS` + JWT bearer, sin cookie) |
| Manejo de errores | — | `LoginController` devuelve 401/500 correctos ante fallo de autenticación / error inesperado |

**Resultado:** Quality Gate **GREEN** — cobertura de código nuevo > 80% (~92% medido en form-service), 0 bugs, 0 vulnerabilidades en código nuevo, 100% de security hotspots revisados, ratings A en fiabilidad/seguridad/mantenibilidad.

## Evidencia de seguridad

| Entregable | Archivo | Resultado |
|---|---|---|
| Trivy: imagen base JRE | `docs/report/security/trivy-baseline-jre.txt` | Reporte real sobre `eclipse-temurin:21-jre-alpine` |
| ZAP: baseline (target `form-service:8086`) | `docs/report/security/zap-report.html` + `.json` + `-warnings.md` | High: 0, Medium: 0, Low: 0, Informational: 1 — 66 reglas pasivas PASS, 0 FAIL |
| Semver tags | `v0.1.0`, `v0.2.0` | Tags anotados generados automáticamente por `bump-and-tag.sh` |
| SonarCloud | Proyecto `JuanCamiloMunozB_circle-guard-public` | Quality Gate GREEN |
| Cobertura JaCoCo | `services/*/build/reports/jacoco/test/html/index.html` | form-service: 92% instrucciones |

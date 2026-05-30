# Calidad y seguridad de pipeline — CircleGuard

> Política de quality gates y escaneo de seguridad en los pipelines Jenkins.
> Estado tras HU-08 Fase 8a: configuración completa, evidencia local; verificación
> end-to-end con Jenkins corriendo + SonarQube/Slack reales se cierra en Fase 8b.

## Capas de gate (todas bloqueantes)

| # | Gate | Herramienta | Ubicación en el pipeline | Criterio de fallo |
|---|---|---|---|---|
| 1 | Unit + Integration tests | JUnit5 + Testcontainers | Antes de Sonar | Cualquier test falla |
| 2 | Code coverage | JaCoCo 0.8.11 | Reporte agregado por `jacocoTestReport` | <80% línea (regla `jacocoTestCoverageVerification`, ya configurada pero NO atada a `check` aún) |
| 3 | Static analysis + coverage | SonarQube (`org.sonarqube` plugin + `withSonarQubeEnv` + `waitForQualityGate`) | Después de Integration, antes de Build JARs | Quality gate del server falla. `abortPipeline: true` corta el build |
| 4 | Container vulnerabilities | Trivy (`aquasec/trivy:latest`) | Inmediatamente después de Kaniko, antes de Deploy | Cualquier CVE `CRITICAL` o `HIGH` no suprimido. `--ignore-unfixed` excluye CVEs sin parche. `.trivyignore` lista las supresiones documentadas |
| 5 | Dynamic security scan | OWASP ZAP baseline (`ghcr.io/zaproxy/zaproxy:stable`) | Solo `Jenkinsfile.stage`, después de E2E, antes de Performance | Cualquier rule `FAIL` (exit code 1 de `zap-baseline.py`). `WARN` no bloquea pero queda en el reporte archivado |
| 6 | Manual approval | Jenkins `input` | Solo `Jenkinsfile.master`, después de Trivy + semver tag, antes de Deploy a master | Operador rechaza o timeout de 30 min |

Cualquier fallo dispara el `post { failure { ... } }` que envía a `#ci-alerts` por Slack
y un email a developers + culprits.

## Semantic versioning automático

| Aspecto | Detalle |
|---|---|
| Script | `jenkins/scripts/bump-and-tag.sh` |
| Entrada | Commits desde el último tag (`git describe --tags --abbrev=0`, fallback `v0.0.0`) |
| Reglas | `BREAKING CHANGE:` o `feat!:` → major. `feat:` → minor. `fix:` / `perf:` / `refactor:` → patch. Cualquier otro tipo → ignorado |
| Salida | Tag anotado `vMAJOR.MINOR.PATCH` creado con `git tag -a -m`, pusheado a origin con `--push` |
| Pipeline | Stage `Semantic Version Tag` en `Jenkinsfile.master`. Se ejecuta entre Trivy y la aprobación manual, de modo que el aprobador ve la versión exacta que se promueve |
| Re-tagging de imágenes | Tras crear el tag git, `crane copy` (incluido en `kaniko:debug`) etiqueta las 8 imágenes en Docker Hub con el `vN.M.P` además del `v${BUILD_NUMBER}` |
| Bootstrap | Sin tags previos. Primer `feat:` produce `v0.1.0` (no `v1.0.0`) porque el sistema aún no está en producción |

## Notificaciones de fallo

| Canal | Plugin Jenkins | Credencial | Trigger |
|---|---|---|---|
| Slack | `slack` (Slack Notification), modo Incoming Webhook | `slack-webhook` (string credential con `${SLACK_WEBHOOK_TOKEN}`, el tramo tras `hooks.slack.com/services/`; `slackNotifier.baseUrl` aporta el prefijo) | `post.failure` + `post.success` en los 3 Jenkinsfiles |
| Email | `email-ext` (`unclassified.email-ext.mailAccount`) + `mailer` | SMTP Gmail vía credencial `smtp-credentials` (`${SMTP_USERNAME}`/`${SMTP_PASSWORD}` = dirección Gmail + App Password) | `post.failure` recipientProviders: `developers() + culprits()` |

El mensaje incluye job, número de build, rama y enlace a la consola
(`${env.BUILD_URL}console`) — cumple el requisito de PLAN §8.6.

## Configuración requerida (variables de entorno del controlador)

```bash
export SONAR_HOST_URL=http://sonarqube:9000   # o la URL pública del server
export SONAR_TOKEN=<token-de-usuario-sonarqube>
export SLACK_WEBHOOK_TOKEN=T.../B.../...       # SOLO el tramo tras hooks.slack.com/services/
export SMTP_HOST=smtp.gmail.com                # Gmail SMTP
export SMTP_USERNAME=<dirección-gmail>
export SMTP_PASSWORD=<google-app-password>     # App Password, no la contraseña normal
export GITHUB_PAT=<personal-access-token>     # solo necesario para bump-and-tag --push
```

`casc.yaml` referencia estas variables. Sin ellas, las credenciales quedan vacías y los
stages que las usan fallan con un mensaje claro.

## Evidencia local (Fase 8a)

Generada en esta sesión sin Jenkins corriendo, con las herramientas reales corriendo
contra targets reales (no mocks):

| Entregable | Archivo | Tamaño/Resultado | Notas |
|---|---|---|---|
| Trivy: imagen base JRE | `docs/report/security/trivy-baseline-jre.txt` | Reporte real con CVEs reales | Imagen `eclipse-temurin:21-jre-alpine` (base de los 8 Dockerfiles); severidades HIGH/CRITICAL no parcheadas |
| ZAP: baseline smoke | `tests/security/reports/zap-report.html` + `.json` + `-warnings.md` | Reporte real generado por ZAP | Target `https://httpbin.org` como demo del flujo. Target real (stage en AKS) se valida en 8b |
| Semver tag | `git tag` muestra `v0.1.0` | Tag anotado real con mensaje | Computado por `bump-and-tag.sh` desde Conventional Commits del repo |
| bump-and-tag dry-run | log de ejecución | `last tag: v0.0.0 -> bump: minor -> new tag: v0.1.0` | Demuestra que la lógica funciona contra los commits reales del repo |

## Lo que queda para Fase 8b (Jenkins + AKS + webhooks)

| # | Pendiente | Recurso requerido |
|---|---|---|
| 3 | Log de pipeline detenido por SonarQube quality gate fallido | Jenkins corriendo + SonarQube alcanzable + un cambio que viole el gate |
| 5 | ZAP baseline real contra `circleguard-stage` en AKS | AKS-stage encendido + 8 servicios desplegados (depende a su vez de imágenes ARM64 publicadas) |
| 7 | Mensaje Slack recibido en `#ci-alerts` ante fallo provocado | `SLACK_WEBHOOK_TOKEN` real + Jenkins corriendo + un `error('forced')` temporal en una stage |

Estas tres evidencias se entregan en una sesión separada cuando los recursos estén
disponibles, exactamente igual que los split 5b y los entregables `Ready` de 4b.

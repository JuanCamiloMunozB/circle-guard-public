# Modelo de CI/CD híbrido — CircleGuard

> Diseñado para el presupuesto de 100 USD de Azure for Students: el build (compilación,
> tests, Kaniko) corre siempre en el agente local WSL2 + Docker Desktop. Solo los
> despliegues de stage y master van a AKS, encendido manualmente y apagado tras el build.

## Resumen ejecutivo

| Ambiente | Agente de build | Tooling de build | Destino de deploy | Costo Azure por build |
|---|---|---|---|---|
| dev    | Local (WSL2 + Docker Desktop), label `local-kaniko` | Kaniko en contenedor Docker efímero (`docker run --rm`) | kind local (opcional, `SKIP_DEPLOY=true` por defecto) | 0 USD |
| stage  | Local (mismo agente `local-kaniko`) | Kaniko local | AKS `circleguard-stage` vía `aks-sa-token` + `AKS_API_SERVER` | ~0.06 USD/h × duración del build |
| master | Local (mismo agente `local-kaniko`) | Kaniko local | AKS `circleguard-master` + aprobación manual + Release Notes | ~0.06 USD/h × duración del build |

**Por qué el build no corre dentro de AKS:** la suscripción Azure for Students limita cada familia de VM amd64 a 4 vCPU. AKS necesita ese cupo para alojar la aplicación (8 servicios + infra). Correr el build en pods efímeros dentro del mismo cluster requeriría además que el controlador Jenkins sea alcanzable desde el cluster, lo que no encaja con el entorno local. El modelo elegido (build local + deploy remoto) mantiene el mismo tooling probado en dev y demuestra un despliegue real en la nube.

## Por qué Kaniko (no `docker build`)

1. **Sin demonio Docker** — funciona dentro de WSL2 sin requerir `dockerd` con socket expuesto.
2. **Sin capas locales acumuladas** — no satura el disco de la workstation.
3. **Reproducible** — mismo binario en dev y en stage/master; la única diferencia es el wrapper (`docker run` vs invocación directa).
4. **Single-platform** — `--customPlatform=linux/amd64` para que las imágenes corran en los nodos B4ms (amd64) de AKS.

## Flujo por pipeline

### `Jenkinsfile.dev` (agente local, deploy opcional a kind)
```
Checkout
  └─> Unit Tests          (gradle)
  └─> Integration Tests   (gradle)
  └─> SonarQube Analysis  (gradle sonar + waitForQualityGate)
  └─> Build JARs          (gradle bootJar)
  └─> Build & Push Images (Kaniko docker run) ──► Docker Hub :<svc>:dev-N
  └─> Deploy to Dev       (skip si SKIP_DEPLOY=true) ──► kind local
  └─> Wait for Rollout    (skip si SKIP_DEPLOY=true)
  └─> Smoke Tests         (skip si SKIP_DEPLOY=true)
post.always: cleanWs + docker system prune
```

### `Jenkinsfile.stage` (agente local, deploy a AKS)
```
Checkout
  └─> Unit Tests           (gradle)
  └─> Integration Tests    (gradle)
  └─> SonarQube Analysis   (gradle sonar + waitForQualityGate)
  └─> Build JARs           (gradle bootJar)
  └─> Build & Push Images  (Kaniko docker run) ──► Docker Hub :<svc>:stage-N
  └─> Deploy to Stage      (kubectl apply → AKS circleguard-stage)
  └─> Wait for Rollout     (kubectl rollout status)
  └─> E2E Tests            (port-forward + run-e2e.sh)
  └─> OWASP ZAP Baseline   (zap-baseline.py contra stage)
  └─> Performance Baseline (pip install locust + run-locust.sh)
post.always: cleanWs
```

### `Jenkinsfile.master` (agente local, deploy a AKS + aprobación)
```
Checkout
  └─> Unit Tests           (gradle)
  └─> Integration Tests    (gradle)
  └─> SonarQube Analysis   (gradle sonar + waitForQualityGate)
  └─> Build JARs           (gradle bootJar)
  └─> Trivy Scan           (aquasec/trivy — falla en CRITICAL/HIGH sin parche)
  └─> Build & Push Images  (Kaniko) ──► Docker Hub :<svc>:v${BUILD_NUMBER} + latest
  └─> Semantic Version Tag (bump-and-tag.sh → git tag vMAJOR.MINOR.PATCH + push)
  └─> Approval             (input; timeout 30 min)
  └─> Deploy to Master     (kubectl apply → AKS circleguard-master)
  └─> Wait for Rollout     (kubectl rollout status)
  └─> E2E Tests            (port-forward + run-e2e.sh)
  └─> Performance Tests    (locust)
  └─> Generate Release Notes (script → archiveArtifacts)
post.always: cleanWs
post.failure: slackSend #ci-alerts + emailext a developers/culprits
post.success: slackSend #ci-alerts
```

## Credenciales y secretos

| Credencial Jenkins ID | Tipo | Fuente del valor | Consumida por |
|---|---|---|---|
| `aks-sa-token` | StringCredential | `${K8S_SA_TOKEN}` (env del controlador) | `withKubeConfig(...)` en stage/master para autenticarse al API server de AKS |
| `dockerhub-creds` | UsernamePassword | `${DOCKERHUB_USERNAME}` / `${DOCKERHUB_TOKEN}` | Login a Docker Hub en el agente local |
| `dockerhub-config-json` | FileCredential | archivo apuntado por `${DOCKERHUB_CONFIG_JSON_PATH}` | Montado en `/kaniko/.docker/config.json` dentro del contenedor Kaniko local |
| `slack-webhook` | StringCredential | `${SLACK_WEBHOOK_TOKEN}` | `slackSend` en post.failure / post.success |
| `smtp-credentials` | UsernamePassword | `${SMTP_USERNAME}` / `${SMTP_PASSWORD}` | `emailext` en post.failure |

Ningún valor real se versiona. `casc.yaml` solo referencia placeholders `${VAR}`; las variables se inyectan al controlador vía `.env` (gitignored).

## RBAC en AKS

ServiceAccount `jenkins` en cada namespace con Role acotado:

- Namespaces `circleguard-dev|stage|master`: pods, services, configmaps, secrets, deployments, exec, log, port-forward.
- Cluster-level: namespaces, persistent volumes.

Suficiente para deploy/rollout/E2E sin elevar privilegios.

## Notificaciones

| Canal | Plugin Jenkins | Trigger |
|---|---|---|
| Slack (`#ci-alerts`) | `slack` (Incoming Webhook), credencial `slack-webhook` | `post.failure` + `post.success` en los 3 Jenkinsfiles |
| Email | `email-ext` + Gmail SMTP, credencial `smtp-credentials` | `post.failure` con `developers() + culprits()` |

El mensaje incluye job, número de build, rama y enlace a la consola (`${env.BUILD_URL}console`).

## Costos previstos

| Evento | Duración típica | Crédito consumido |
|---|---|---|
| Build dev (8 servicios, Kaniko local) | ~8–12 min | 0 USD (workstation) |
| Build stage (tests + Sonar + Kaniko + deploy AKS + E2E + ZAP + Locust) | ~40–60 min | ~0.04–0.06 USD (AKS-stage encendido durante el build) |
| Build master (igual + aprobación + Release Notes) | ~45–70 min + tiempo de aprobación | ~0.045–0.07 USD si la aprobación es rápida |

Pre-condición: AKS-stage/master encendido **solo durante el build**. El pipeline no lo arranca: el operador lo enciende manualmente antes del build y ejecuta `az aks stop` después.

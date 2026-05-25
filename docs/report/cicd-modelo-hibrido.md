# Modelo de CI/CD híbrido — CircleGuard

> Diseñado para el presupuesto de 100 USD de Azure for Students: el desarrollo diario
> NO enciende AKS; sólo stage y prod consumen crédito y solo mientras un build corre.

## Resumen ejecutivo

| Ambiente | Dónde corre el agente | Tooling de build | Tooling de deploy | Costo Azure por build |
|---|---|---|---|---|
| dev    | Local (workstation, Git Bash/WSL + Docker Desktop) | Kaniko en contenedor Docker efímero (`docker run --rm gcr.io/kaniko-project/executor`) | `kubectl` local apuntando a kind o AKS-dev (sólo si ya está encendido) | 0 USD |
| stage  | Pod efímero en AKS (kubernetes plugin de Jenkins) | Kaniko en contenedor `kaniko` del pod | `kubectl` en contenedor `kubectl` del mismo pod (RBAC: ServiceAccount `jenkins`) | ~0.06 USD/h × duración del build |
| master | Pod efímero en AKS (idem stage) | idem stage | idem stage + aprobación manual + Release Notes | ~0.06 USD/h × duración del build |

## Por qué Kaniko (no `docker build`)

1. **Sin demonio Docker** ⇒ funciona dentro de un pod no privilegiado en AKS (no requiere `dockerd` corriendo).
2. **Sin capas locales acumuladas** ⇒ cumple CLAUDE.md §3 (no saturar disco del workstation).
3. **Reproducible** ⇒ mismo binario corre en agente local y en pod AKS; la única diferencia es el wrapper (`docker run` vs invocación directa `/kaniko/executor`).
4. **Single-platform por invocación**: aceptamos arm64-only (CLAUDE.md §4 lo permite) en lugar de multi-arch porque los nodos AKS son ARM64; un solo `--customPlatform=linux/arm64` basta.

## Anatomía del Pod Template

Definido en `jenkins/podtemplates/kaniko-build-agent.yaml`. El kubernetes plugin lo lee vía `yamlFile` desde el repo checkeado.

| Contenedor | Imagen | Para qué |
|---|---|---|
| `jnlp` | `jenkins/inbound-agent:latest` | Conexión inbound con el controlador. Asignado a `defaultContainer`. |
| `gradle` | `arm64v8/gradle:8.14-jdk21-alpine` | Compilar JARs, ejecutar tests JUnit/Testcontainers. |
| `kaniko` | `gcr.io/kaniko-project/executor:debug` | Build + push de las 8 imágenes a Docker Hub. Monta `/kaniko/.docker` desde el Secret `dockerhub-config-json`. |
| `kubectl` | `bitnami/kubectl:latest` | `apply`, `set image`, `rollout status`, `port-forward`. Usa el token del SA `jenkins`. |
| `python` | `arm64v8/python:3.12-alpine` | `pip install locust` y disparar el escenario de performance. |

`nodeSelector: kubernetes.io/arch=arm64` evita scheduling accidental en un eventual nodepool x86.

## Flujo por pipeline

### `Jenkinsfile.dev` (agente local)
```
Checkout
  └─> Unit Tests (gradle)
  └─> Integration Tests (gradle)
  └─> Build JARs (gradle)
  └─> Build & Push Images (Kaniko en `docker run --rm`) ──► Docker Hub
  └─> Deploy to Dev      (skip si SKIP_DEPLOY=true)  ──► kind / AKS-dev (opcional)
  └─> Wait for Rollout   (skip si SKIP_DEPLOY=true)
  └─> Smoke Tests        (skip si SKIP_DEPLOY=true)
post.always: cleanWs + docker system prune
```

Cumple CLAUDE.md §5.1 / §5.4: dev NUNCA enciende AKS. Las stages Deploy/Rollout/Smoke
existen para validar el ciclo completo cuando hay un cluster disponible (kind local,
o AKS-dev en una sesión deliberada), pero no son justificación para encender AKS.

### `Jenkinsfile.stage` (pod efímero en AKS)
```
[pod nace en circleguard-stage; serviceAccountName=jenkins]
Checkout                                  (container default: jnlp)
  └─> Unit Tests             container('gradle')
  └─> Integration Tests      container('gradle')
  └─> Build JARs             container('gradle')
  └─> Build & Push Images    container('kaniko')   ──► Docker Hub
  └─> Deploy to Stage        container('kubectl')
  └─> Wait for Rollout       container('kubectl')
  └─> E2E Tests              container('kubectl')  (port-forward + run-e2e.sh)
  └─> Performance Baseline   container('python')   (pip install locust + run-locust.sh)
post.always: cleanWs
[pod muere; kubectl get pods -n circleguard-stage queda vacío]
```

### `Jenkinsfile.master` (pod efímero en AKS)
Idéntico a stage hasta `Build & Push Images`, pero etiquetando con `v${BUILD_NUMBER}` y `latest`. Luego:
```
  └─> Approval: promote to production   (input; timeout 30 min)
  └─> Deploy to Master                  container('kubectl')
  └─> Wait for Rollout                  container('kubectl')
  └─> E2E Tests                         container('kubectl')
  └─> Performance Tests                 container('python')
  └─> Generate Release Notes            container('gradle') (archiveArtifacts)
```

## Credenciales y secretos

| Credencial Jenkins ID | Tipo | Fuente del valor | Consumida por |
|---|---|---|---|
| `k8s-sa-token` | StringCredential | `${K8S_SA_TOKEN}` (env del controlador) | `withKubeConfig(...)` en dev, y la cloud `aks-cg` para autenticarse al API server de AKS |
| `dockerhub-creds` | UsernamePassword | `${DOCKERHUB_USERNAME}` / `${DOCKERHUB_TOKEN}` (env del controlador) | Reserva por si un script futuro necesita login interactivo |
| `dockerhub-config-json` | FileCredential | archivo apuntado por `${DOCKERHUB_CONFIG_JSON_PATH}` | dev pipeline: montado en `/kaniko/.docker/config.json` dentro del contenedor Kaniko |
| Secret K8s `dockerhub-config-json` | `kubernetes.io/dockerconfigjson` | Generado fuera del repo (ver `jenkins/README.md` §1–§2) | stage/master pipelines: montado por el pod template en `/kaniko/.docker` |

Ningún valor real se versiona. `casc.yaml` solo referencia placeholders `${VAR}`; las
variables se inyectan al controlador vía `.env` (gitignored). El Secret K8s real está
también gitignored.

## RBAC

Reusa lo existente en `jenkins/config/jenkins-account.yaml` (ServiceAccount `jenkins` +
Role por namespace + ClusterRole acotado, ver CLAUDE.md §9.7). El pod efímero corre
como `jenkins`, así que su Role determina qué puede tocar `kubectl`:

- Namespaces `circleguard-dev|stage|master`: pods, services, configmaps, secrets, deployments, exec, log, port-forward.
- Cluster-level: namespaces, persistent volumes.

Esto es suficiente para deploy/rollout/E2E sin elevar privilegios.

## Costos previstos

| Evento | Duración típica | Crédito consumido |
|---|---|---|
| Build dev (8 servicios, Kaniko local) | ~8–12 min | 0 USD (workstation) |
| Build stage (8 servicios, Kaniko + deploy + E2E + Locust) | ~25–35 min | ~0.026–0.036 USD (1 pod ARM64 en AKS-stage encendido) |
| Build master (igual + aprobación + Release Notes) | ~30–40 min + tiempo de aprobación | ~0.030–0.040 USD si la aprobación es rápida |

Pre-condición: AKS-stage/master encendido **sólo durante el build**. El pipeline NO lo
arranca: el operador (Ops) lo enciende manualmente antes del build, dispara el job, y
ejecuta `az aks stop` después.

## Notificaciones (estado actual: marcador)

Los tres Jenkinsfiles tienen `post { failure { echo ... } }` con un comentario `// TODO
Phase 8 (sec.8.6): replace with slackSend / emailext`. La conexión real (webhook Slack
o `emailext`) se hace en HU-08 con la credencial almacenada en Jenkins; este PR sólo
deja el punto de extensión preparado.

## Verificación pendiente (Fase 5b)

Esta fase deja el código listo. La verificación end-to-end con recursos reales se
hará en sesión separada cuando estén disponibles:

1. Cuenta Docker Hub `circleguard` con access token Read/Write.
2. Crédito AKS para una sesión de ~1 h (encender stage, desplegar Jenkins en AKS o
   conectar el controlador local con la cloud `aks-cg`, ejecutar 1 build de dev + 1
   de stage, apagar).

Los entregables de verificación de la HU son:

- Build dev: log de Kaniko subiendo a `docker.io/circleguard/auth-service:dev-N` y
  `docker images | grep circleguard` vacío tras el build.
- Build stage: log del pod `jnlp-*` apareciendo durante el build y `kubectl get pods -n
  circleguard-stage` vacío tras terminar.
- `az aks show ... --query "powerState.code"` devolviendo `Stopped` al cerrar.

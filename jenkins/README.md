# Jenkins — CircleGuard CI/CD (modelo híbrido)

Este directorio contiene la infraestructura de pipeline de CircleGuard:

| Ruta | Propósito |
|---|---|
| `Jenkinsfile.dev` | Pipeline dev, corre en **agente local** (Git Bash/WSL + Docker Desktop). Construye con Kaniko en un contenedor efímero. **No enciende AKS.** |
| `Jenkinsfile.stage` | Pipeline stage, corre en un **pod efímero dentro de AKS** vía el cloud `aks-cg`. Construye con Kaniko, despliega a `circleguard-stage`, ejecuta E2E + Locust baseline. |
| `Jenkinsfile.master` | Pipeline prod, mismo modelo que stage + **aprobación manual** (`input`) + Release Notes. |
| `config/casc.yaml` | Configuration as Code: SecurityRealm, Credentials (k8s SA token, Docker Hub user/token, Docker Hub config.json) y el cloud Kubernetes `aks-cg`. |
| `config/plugins.txt` | Plugins preinstalados (incluye `kubernetes` para pod templates y `kubernetes-cli` para `withKubeConfig`). |
| `config/jenkins-account.yaml` | RBAC del ServiceAccount `jenkins` en AKS (Role por namespace + ClusterRole acotado). |
| `podtemplates/kaniko-build-agent.yaml` | Definición del pod efímero que stage/master usan: contenedores `gradle`, `kaniko`, `kubectl`, `python`. Imágenes ARM64 (nodos AKS son ARM64). |

## Modelo híbrido en una imagen mental

```
                  ┌─────────────────────────────┐
                  │  Jenkins controller         │
                  │  (CASC: casc.yaml)          │
                  └─────────────┬───────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
   dev pipeline           stage pipeline           master pipeline
   agent { label          agent { kubernetes       agent { kubernetes
   'local-kaniko' }       cloud 'aks-cg' }         cloud 'aks-cg' }
        │                       │                       │
   ┌────▼─────┐         ┌───────▼────────┐      ┌───────▼────────┐
   │ Local    │         │ Ephemeral pod  │      │ Ephemeral pod  │
   │ Docker   │         │ inside AKS:    │      │ inside AKS:    │
   │ Desktop  │         │  gradle        │      │  gradle        │
   │          │         │  kaniko        │      │  kaniko        │
   │ Kaniko   │         │  kubectl       │      │  kubectl       │
   │ container│         │  python        │      │  python        │
   └────┬─────┘         └───────┬────────┘      └───────┬────────┘
        │                       │                       │
        └────────────► Docker Hub (public) ◄────────────┘
                       docker.io/circleguard/*
```

dev pipeline NUNCA enciende AKS. Si `K8S_API_SERVER` no apunta a un cluster encendido,
las stages Deploy/Rollout/Smoke deben saltarse con `SKIP_DEPLOY=true` o el pipeline
debe apuntarse a un kind local de smoke-test.

## Bootstrap (una sola vez por workstation/operator)

### 1. Credenciales Docker Hub

Crea un access token con scope **Read/Write** en
https://hub.docker.com/settings/security y compón el `config.json`:

```bash
echo -n "<DOCKERHUB_USER>:<DOCKERHUB_TOKEN>" | base64 -w0
# pega el resultado dentro de:
# {"auths":{"https://index.docker.io/v1/":{"auth":"<base64>"}}}
```

Guarda el JSON resultante en una ruta local, por ejemplo
`~/.circleguard/dockerhub-config.json`, y exporta su path:

```bash
export DOCKERHUB_USERNAME=<user>
export DOCKERHUB_TOKEN=<token>
export DOCKERHUB_CONFIG_JSON_PATH=~/.circleguard/dockerhub-config.json
```

Estas variables son leídas por `casc.yaml` al iniciar Jenkins.

### 2. Secret K8s para Kaniko (stage/master)

```bash
cp k8s/jenkins/dockerhub-secret.yaml.example k8s/jenkins/dockerhub-secret.yaml
# sustituye REPLACE_ME_WITH_BASE64_OF_DOCKERCONFIGJSON con
#   cat ~/.circleguard/dockerhub-config.json | base64 -w0

# (Necesita AKS encendido. Ver advertencia de costo más abajo.)
kubectl apply -f k8s/jenkins/dockerhub-secret.yaml -n circleguard-stage
kubectl apply -f k8s/jenkins/dockerhub-secret.yaml -n circleguard-master
```

El `.yaml` real está en `.gitignore`. Verifica con `git status` antes de cada commit.

### 3. Agente local de dev (Jenkins → label `local-kaniko`)

Opciones equivalentes:

- **Controlador local en Docker Desktop:** levanta Jenkins con `docker compose
  --file jenkins/config/docker-compose.jenkins.yml up -d`, abre
  http://localhost:8080, ve a *Manage Jenkins → Nodes → New Node*, crea un nodo
  permanente con label `local-kaniko`, comando `docker.sock` montado.

- **Agente standalone JNLP:** desde Git Bash/WSL Ubuntu, descarga
  `agent.jar` desde http://localhost:8080/jnlpJars/agent.jar y lánzalo con la
  secret del nodo creado.

El requisito mínimo: que el agente tenga `docker` accesible (Docker Desktop con
WSL2 backend basta) y permisos para hacer `docker run --rm gcr.io/kaniko-project/executor:debug ...`.

### 4. Cloud Kubernetes (stage/master)

`casc.yaml` lee `${K8S_API_SERVER}` y `${K8S_API_CA_CERT}` del entorno:

```bash
# Tras `az aks start` y `az aks get-credentials`:
export K8S_API_SERVER=$(az aks show -g rg-cg-stage -n aks-cg-stage --query fqdn -o tsv | sed 's|^|https://|')
export K8S_API_CA_CERT=$(kubectl config view --raw -o jsonpath='{.clusters[0].cluster.certificate-authority-data}' | base64 -d)
```

(El SA token sigue gestionado por `setup-k8s-jenkins.sh`.)

## Reglas duras (CLAUDE.md)

1. **dev no enciende AKS** (sec.5.1, sec.5.4). Si lo necesitas para depurar Deploy/Rollout,
   pásale `SKIP_DEPLOY=true` o apunta `K8S_API_SERVER` a un kind.
2. **Sin imágenes locales acumuladas** (sec.3): Kaniko corre en contenedor efímero;
   tras cada build de dev, `docker system prune -af` se ejecuta en el stage post.
3. **Pods de agente en AKS son efímeros**: el plugin `kubernetes` los destruye al
   terminar el build. Verifica con `kubectl get pods -n circleguard-stage` que no
   queda ningún `jnlp-*`.
4. **Aprobación manual en prod** (sec.9.3): el stage `Approval: promote to production`
   pausa el pipeline con timeout de 30 min.
5. **Sin secretos en texto plano** (sec.9.6): `${K8S_SA_TOKEN}`,
   `${DOCKERHUB_TOKEN}`, `${DOCKERHUB_CONFIG_JSON_PATH}` se inyectan vía env, NUNCA
   versionados.
6. **Notificaciones reales**: hoy los bloques `post { failure }` solo hacen `echo`
   (TODO marcado en cada Jenkinsfile). La integración Slack/email se conecta en la
   Fase 8 (HU-08, sec.8.6 del plan).

## Verificación end-to-end (a ejecutar en sesión separada con credenciales reales)

Estos son los entregables que la fase de configuración deja preparados pero requieren
recursos reales para validarse:

```bash
# (3) dev: Kaniko construye y publica
docker images   # debe estar vacío de capas circleguard/* tras el build dev
# El log Jenkins del job dev muestra:
#   "INFO[0000] Resolved base name eclipse-temurin:21-jre-alpine"
#   "INFO[0010] Pushed docker.io/circleguard/auth-service:dev-NN"

# (4) stage: pod efímero en AKS se crea y se destruye
kubectl get pods -n circleguard-stage -w   # durante el build aparece un pod jnlp-XXXX
kubectl get pods -n circleguard-stage      # tras el build: vacío (sin pods jenkins-agent)

# (5) AKS apagado al cerrar la sesión
az aks stop --name aks-cg-stage --resource-group rg-cg-stage
az aks show --name aks-cg-stage --resource-group rg-cg-stage --query "powerState.code" -o tsv
# debe responder: "Stopped"
```

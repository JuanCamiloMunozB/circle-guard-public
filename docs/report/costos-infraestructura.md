# Costos de Infraestructura — CircleGuard (HU-03)

> Presupuesto Azure: **100 USD** (Azure for Students). Region: **southcentralus**.
> Cualquier encendido de AKS debe registrarse aqui. Precios USD, retail PAYG, sin descuentos
> de Spot ni Reservations.

## Restricciones reales detectadas en la suscripcion (2026-05-24)

- Policy `Allowed resource deployment regions` restringe regiones a:
  `mexicocentral`, `brazilsouth`, `chilecentral`, `southcentralus`, `westus3`.
- En southcentralus, Azure for Students **NO permite x86 B-series** (`Standard_B2s`,
  `Standard_B2ms`, etc.). Solo se pueden desplegar B-series ARM64
  (`Standard_B*pls_v2`, `Standard_B*ps_v2`).
- Cuotas en southcentralus:

  | Familia | Limite |
  |---|---|
  | Total Regional vCPUs | 6 |
  | Standard PBS Family (B-series ARM v2) | 6 |
  | Standard BS Family (B-series x86) | 4 *(no usable: SKUs bloqueados por policy)* |

- AKS rechaza versiones `<= 1.32` por estar en **LTS-only** (requiere Premium tier).
  Versiones soportadas en plan `KubernetesOfficial`: **1.33, 1.34, 1.35**.

## Decisiones de sizing (cumplen restricciones + minimizan costo)

| Ambiente | VM size | Arquitectura | vCPU | RAM | Precio retail South Central US |
|---|---|---|---|---|---|
| dev   | `Standard_B2pls_v2` | ARM64 | 2 | 4 GB  | **~0.0332 USD/h** |
| stage | `Standard_B2pls_v2` | ARM64 | 2 | 4 GB  | ~0.0332 USD/h |
| prod  | `Standard_B2ps_v2`  | ARM64 | 2 | 8 GB  | ~0.0498 USD/h (x2 nodos) |

> **Implicacion del cambio a ARM64:** todas las imagenes Docker del proyecto
> (`docker.io/circleguard/<servicio>:<tag>`) deben publicarse como **multi-arch
> (`linux/amd64,linux/arm64`)** o **arm64-only**. Las imagenes base oficiales para Java
> (`eclipse-temurin:21-jre-jammy`) ya son multi-arch. El build con Kaniko debe usar
> `--customPlatform=linux/arm64` o build matrix.

## Tabla de costo estimado por ambiente

| Ambiente | Compute | OS Disk (32 GiB P4) | Load Balancer Standard | Storage Account (artefactos, vacio) | **Encendido / hora** | **Encendido / dia** |
|---|---|---|---|---|---|---|
| dev   | 1x B2pls_v2 = 0.0332/h | ~0.0024/h | 0.025/h | LRS ~0.0208/GB-mes | **~0.061 USD/h** | **~1.46 USD/dia** |
| stage | 1x B2pls_v2 = 0.0332/h | ~0.0024/h | 0.025/h | LRS | **~0.061 USD/h** | **~1.46 USD/dia** |
| prod  | 2x B2ps_v2 = 0.0996/h  | ~0.0048/h | 0.025/h | GRS ~0.0416/GB-mes | **~0.130 USD/h** | **~3.12 USD/dia** |

> El control plane es gratis con `sku_tier = "Free"` (lo usamos en los 3 ambientes).
> El SLA solo aplica en `Standard` (~0.10 USD/h adicional).

## Costos siempre encendidos (no se apagan con `az aks stop`)

| Recurso | Costo mensual estimado |
|---|---|
| Storage Account `stcgtfstatesesqet` (backend remoto, <1 MB blobs) | ~0.05 USD/mes |
| Storage Account `stcgdev4113` (artefactos dev, vacio) | ~0.05 USD/mes |
| Discos managed P4 (32 GiB) por nodo (mientras NO se haga `terraform destroy`) | ~1.70 USD/disco/mes |
| Load Balancer Standard + IP publica (mientras AKS exista, aunque este Stopped) | ~3.65 USD/mes |

`az aks stop` apaga compute (sin costo de VM) pero NO libera discos, LB ni IP. Para
liberar todo: `terraform destroy`.

## Estimacion para HU-03 (lo que costo esta fase)

| Item | Costo estimado |
|---|---|
| Bootstrap (RG + Storage Account tfstate) | <0.01 USD one-time + ~0.05 USD/mes |
| `terraform apply` dev: AKS encendido ~10 min (creacion + verificacion + apagado) | ~0.011 USD |
| Discos+LB residuales hasta proximo apply/destroy | ~0.18 USD/dia (mientras dev exista) |

**Total estimado HU-03 (solo encendido + bootstrap): < 0.10 USD.**
**Costo residual diario mientras dev exista apagado: ~0.18 USD/dia.**

## Registro de gasto real

| Fecha | Accion | Ambiente | Duracion encendido | Costo estimado (USD) | Credito restante (USD) | Operador |
|-------|--------|----------|--------------------|----------------------|------------------------|----------|
| 2026-05-24 | Bootstrap backend remoto (RG + Storage Account `stcgtfstatesesqet`) | tfstate | n/a | <0.01 | ~100.00 | Jose M. |
| 2026-05-24 | RG huerfano `rg-cg-tfstate` (region bloqueada por policy) creado y destruido | tfstate | <2 min | 0.00 | ~100.00 | Jose M. |
| 2026-05-24 | `terraform apply` dev (`aks-cg-dev`, 1x B2pls_v2, K8s 1.33.11) | dev | ~6 min (provisioning) + ~2 min (kubectl) = ~8 min | ~0.008 | ~99.99 | Jose M. |
| 2026-05-24 | `az aks stop aks-cg-dev` (powerState=Stopped verificado) | dev | n/a | 0.00 | ~99.99 | Jose M. |
| 2026-05-24 | Discos+LB residuales dev (apagado) | dev | continuo | ~0.18/dia | (se acumula) | Jose M. |
| 2026-05-24 | HU-04: validacion de manifiestos K8s (8 servicios + infra deps) con `kind` local + `kubectl apply --dry-run=client/server`. AKS NO encendido: las imagenes ARM64 multi-arch aun no existen en Docker Hub, asi que `Ready` se cerrara en Fase 5/HU-05 cuando Kaniko publique. | dev (no tocado) | 0 min en AKS | 0.00 | ~99.99 | Jose M. |
| 2026-05-25 | HU-05 (Fase 5a): cambios estructurales de Jenkins hibrido. Refactor de 3 Jenkinsfiles (`agent any` -> agente local Kaniko / pod efimero AKS), Pod Template ARM64, cloud Kubernetes en CASC, credenciales Docker Hub como Jenkins Credentials + Secret K8s. Sin ejecutar pipelines (sin token Docker Hub real); verificacion end-to-end queda para Fase 5b. AKS NO encendido. | n/a (config + docs) | 0 min en AKS | 0.00 | ~99.99 | Jose M. |

> Actualizar esta tabla **cada** vez que se ejecute `az aks start/stop` o un `terraform
> apply/destroy` en cualquier ambiente.

## Reglas duras (CLAUDE.md §6)

1. AKS solo encendido cuando se esta usando activamente; apagar **inmediatamente** despues.
2. Validar `powerState.code == "Stopped"` tras cada apagado.
3. Cuota PBS Family = 6 vCPU. Con dev encendido (2 vCPU) cabe stage (2 vCPU). Prod (4 vCPU)
   requiere dev+stage apagados. NUNCA encender los 3 a la vez.
4. Si se cierra el proyecto: `terraform destroy` en cada ambiente para no acumular costos
   de discos/IP publica/LB persistentes (~5.35 USD/mes por ambiente apagado).
5. NUNCA crear recursos manualmente desde el portal; toda la infra pasa por Terraform.
6. Imagenes Docker del proyecto: **multi-arch o linux/arm64** (los nodos son ARM64 v2).

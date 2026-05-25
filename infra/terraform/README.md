# Terraform — Infraestructura Azure de CircleGuard

Este directorio contiene la IaC de CircleGuard. Es **modular** y **parametrizada**: ninguna
referencia a subscription, region, nombres ni sizes va hardcodeada en el codigo.

## Estructura

```
infra/terraform/
├── bootstrap/        # RG + Storage Account + Container para el backend remoto (UNA vez, estado local)
├── modules/
│   ├── network/      # VNet + Subnet AKS
│   ├── aks/          # AKS cluster + system node pool
│   └── storage/      # Storage Account por ambiente (artefactos)
└── environments/
    ├── dev/          # node_count=1, Standard_B2pls_v2 (ARM64), sku_tier=Free
    ├── stage/        # node_count=1, Standard_B2pls_v2 (ARM64), sku_tier=Free (plan only)
    └── prod/         # node_count=2, Standard_B2ps_v2 (ARM64), replicacion GRS (plan only)
```

## Restricciones de suscripción (Azure for Students en southcentralus)

- **Región obligatoria:** `southcentralus` (Policy `Allowed resource deployment regions` restringe a: mexicocentral, brazilsouth, chilecentral, southcentralus, westus3).
- **VM size obligatoria:** `Standard_B*pls_v2` o `Standard_B*ps_v2` (ARM64 solo). **NO se permiten x86 B-series** en Azure for Students.
- **Cuota PBS Family ARM64:** 6 vCPU total en la región. dev (2 vCPU) + stage (2 vCPU) caben juntos; prod (4 vCPU) requiere dev+stage apagados.
- **Kubernetes:** versiones `1.33, 1.34, 1.35` (LTS-only < 1.33 requiere Premium tier, no viable en Free).

## Mandato de costo (CLAUDE.md §6)

- Crédito Azure: **100 USD** (Azure for Students).
- AKS solo encendido cuando se necesite y apagado inmediatamente después (comando: `az aks stop`).
- Estimar costo antes de cada `terraform apply` y registrar en `docs/report/costos-infraestructura.md`.
- **Implicación:** todas las imágenes Docker publicadas en Docker Hub DEBEN ser **multi-arch (`linux/amd64,linux/arm64`)** o **arm64-only** (los nodos son ARM64 v2).

## Orden de operación

### Prerequisitos
- **Azure CLI:** `az version` debe devolver 2.60+.
- **Terraform:** `terraform version` debe devolver 1.5+.
- **Kubectl:** `kubectl version --client` debe devolver 1.28+.
- **Acceso:** debes estar logueado en la suscripción correcta con rol **Contributor** (mínimo):
  ```bash
  az login
  az account set --subscription "2f53e0c9-4466-4393-b59f-127947832d2b"
  az account show  # verifica que sea la suscripción correcta
  ```

### 1. Bootstrap (una sola vez, solo si no existe aún)
```bash
cd infra/terraform/bootstrap
terraform init
terraform validate
terraform plan -out=bootstrap.plan
terraform apply bootstrap.plan
# Anota el output storage_account_name y reemplaza REPLACE_BY_BOOTSTRAP_OUTPUT en cada backend.tf
```

### 2. Por ambiente (dev en esta fase)
```bash
cd infra/terraform/environments/dev

# Inicializar backend remoto (necesita credenciales Azure CLI)
terraform init

terraform validate
terraform plan -out=dev.plan

# SOLO aplicar tras estimar costo y confirmar:
echo "Costo estimado: ~0.061 USD/hora. ¿Continuar? (y/n)"
# Si continúas:
terraform apply dev.plan

# Verificar nodos y credenciales:
az aks get-credentials --resource-group rg-cg-dev --name aks-cg-dev --overwrite-existing
kubectl get nodes

# Tras validaciones, apagar INMEDIATAMENTE:
az aks stop --resource-group rg-cg-dev --name aks-cg-dev
az aks show --resource-group rg-cg-dev --name aks-cg-dev --query "powerState.code" -o tsv
# Debe devolver: "Stopped"
```

### 3. Destruir (al cerrar proyecto o liberar crédito)
```bash
cd infra/terraform/environments/dev
terraform destroy  # Libera discos, IP pública, Load Balancer (~5.35 USD/mes ahorrados)
```

## Politicas (no negociables)

- Nada de `terraform.tfstate` local en repo (`.gitignore` ya lo cubre).
- Backend remoto con bloqueo (azurerm + Azure AD auth).
- `terraform.tfvars` versionado SIN secretos; valores sensibles via env vars `TF_VAR_*` o
  `terraform.tfvars.local` (ignorado por git).
- Antes de cada `apply` o `az aks start`, estimar el costo y registrar en
  `docs/report/costos-infraestructura.md`.

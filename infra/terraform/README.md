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
    ├── dev/          # node_count=1, Standard_B2s, sku_tier=Free
    ├── stage/        # node_count=1, Standard_B2s, sku_tier=Free
    └── prod/         # node_count=2, Standard_B2ms, replicacion GRS
```

## Mandato de costo (CLAUDE.md §6)

- Credito Azure: **100 USD** (Azure for Students).
- AKS solo encendido cuando se necesite y apagado inmediatamente despues.
- Cuota tipica de Students: **4 vCPU por region**. Standard_B2s = 2 vCPU; Standard_B2ms = 2 vCPU.
- NO encender stage y dev simultaneamente sin verificar cuota.
- prod en esta fase **solo se planifica**, NO se aplica.

## Orden de operacion

### 1. Bootstrap (una sola vez)
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
terraform init           # configura backend remoto
terraform validate
terraform plan -out=dev.plan
terraform apply dev.plan # SOLO con confirmacion explicita
az aks get-credentials --resource-group rg-cg-dev --name aks-cg-dev --overwrite-existing
kubectl get nodes
# Apagar inmediatamente:
az aks stop --resource-group rg-cg-dev --name aks-cg-dev
az aks show --resource-group rg-cg-dev --name aks-cg-dev --query "powerState.code" -o tsv
```

### 3. Destruir (al cerrar el proyecto o liberar credito)
```bash
cd infra/terraform/environments/dev
terraform destroy
```

## Politicas (no negociables)

- Nada de `terraform.tfstate` local en repo (`.gitignore` ya lo cubre).
- Backend remoto con bloqueo (azurerm + Azure AD auth).
- `terraform.tfvars` versionado SIN secretos; valores sensibles via env vars `TF_VAR_*` o
  `terraform.tfvars.local` (ignorado por git).
- Antes de cada `apply` o `az aks start`, estimar el costo y registrar en
  `docs/report/costos-infraestructura.md`.

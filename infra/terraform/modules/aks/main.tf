resource "azurerm_kubernetes_cluster" "this" {
  name                = "aks-${var.prefix}-${var.environment}"
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = "aks-${var.prefix}-${var.environment}"
  kubernetes_version  = var.kubernetes_version
  sku_tier            = var.sku_tier
  node_resource_group = "rg-${var.prefix}-${var.environment}-aks-nodes"

  default_node_pool {
    name                 = "system"
    vm_size              = var.node_vm_size
    node_count           = var.node_count
    vnet_subnet_id       = var.subnet_id
    os_disk_size_gb      = var.os_disk_size_gb
    os_disk_type         = "Managed"
    type                 = "VirtualMachineScaleSets"
    orchestrator_version = var.kubernetes_version
    only_critical_addons_enabled = false
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "azure"
    load_balancer_sku = "standard"
    service_cidr      = var.service_cidr
    dns_service_ip    = var.dns_service_ip
  }

  role_based_access_control_enabled = true

  tags = var.tags
}

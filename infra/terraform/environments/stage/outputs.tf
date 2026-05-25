output "resource_group_name" {
  description = "Resource Group del ambiente."
  value       = azurerm_resource_group.this.name
}

output "aks_cluster_name" {
  description = "Nombre del cluster AKS."
  value       = module.aks.cluster_name
}

output "aks_get_credentials_command" {
  description = "Comando az para obtener kubeconfig."
  value       = module.aks.kube_config_command
}

output "vnet_name" {
  description = "Nombre de la VNet."
  value       = module.network.vnet_name
}

output "artifacts_storage_account" {
  description = "Storage Account del ambiente."
  value       = module.storage.storage_account_name
}

output "cluster_id" {
  description = "Resource ID del cluster AKS."
  value       = azurerm_kubernetes_cluster.this.id
}

output "cluster_name" {
  description = "Nombre del cluster AKS."
  value       = azurerm_kubernetes_cluster.this.name
}

output "node_resource_group" {
  description = "Resource Group que Azure crea automaticamente para los nodos AKS."
  value       = azurerm_kubernetes_cluster.this.node_resource_group
}

output "kube_config_command" {
  description = "Comando az para obtener kubeconfig del cluster."
  value       = "az aks get-credentials --resource-group ${var.resource_group_name} --name ${azurerm_kubernetes_cluster.this.name} --overwrite-existing"
}

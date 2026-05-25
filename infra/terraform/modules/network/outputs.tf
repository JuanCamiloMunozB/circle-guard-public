output "vnet_id" {
  description = "ID de la VNet creada."
  value       = azurerm_virtual_network.this.id
}

output "vnet_name" {
  description = "Nombre de la VNet."
  value       = azurerm_virtual_network.this.name
}

output "aks_subnet_id" {
  description = "ID de la subnet dedicada al node pool de AKS."
  value       = azurerm_subnet.aks.id
}

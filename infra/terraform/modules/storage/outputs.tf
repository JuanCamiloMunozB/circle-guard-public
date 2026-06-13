output "storage_account_name" {
  description = "Nombre del Storage Account."
  value       = azurerm_storage_account.this.name
}

output "storage_account_id" {
  description = "Resource ID del Storage Account."
  value       = azurerm_storage_account.this.id
}

output "artifacts_container_name" {
  description = "Container privado para artefactos del ambiente."
  value       = azurerm_storage_container.artifacts.name
}

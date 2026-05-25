output "resource_group_name" {
  description = "Resource Group que contiene la Storage Account del estado."
  value       = azurerm_resource_group.tfstate.name
}

output "storage_account_name" {
  description = "Storage Account donde viven los blobs del estado de Terraform. Se usa en backend.tf de cada ambiente."
  value       = azurerm_storage_account.tfstate.name
}

output "container_name" {
  description = "Container del Storage Account donde se almacenan los estados."
  value       = azurerm_storage_container.tfstate.name
}

output "backend_config_snippet" {
  description = "Snippet listo para pegar en backend.tf de cada ambiente."
  value = <<EOT
terraform {
  backend "azurerm" {
    resource_group_name  = "${azurerm_resource_group.tfstate.name}"
    storage_account_name = "${azurerm_storage_account.tfstate.name}"
    container_name       = "${azurerm_storage_container.tfstate.name}"
    key                  = "<env>.tfstate"
  }
}
EOT
}

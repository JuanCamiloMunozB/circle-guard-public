terraform {
  backend "azurerm" {
    resource_group_name  = "rg-cg-tfstate"
    storage_account_name = "stcgtfstatesesqet"
    container_name       = "tfstate"
    key                  = "prod.tfstate"
  }
}

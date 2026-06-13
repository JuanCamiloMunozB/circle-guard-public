terraform {
  required_version = ">= 1.6"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.116"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {
  features {}
}

locals {
  common_tags = merge({
    project     = "circleguard"
    environment = var.environment
    managed_by  = "terraform"
  }, var.extra_tags)
}

resource "azurerm_resource_group" "this" {
  name     = "rg-${var.prefix}-${var.environment}"
  location = var.location
  tags     = local.common_tags
}

resource "random_string" "storage_suffix" {
  length  = 4
  upper   = false
  numeric = true
  special = false
}

module "network" {
  source = "../../modules/network"

  prefix                      = var.prefix
  environment                 = var.environment
  location                    = var.location
  resource_group_name         = azurerm_resource_group.this.name
  vnet_address_space          = var.vnet_address_space
  aks_subnet_address_prefixes = var.aks_subnet_address_prefixes
  tags                        = local.common_tags
}

module "aks" {
  source = "../../modules/aks"

  prefix              = var.prefix
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.this.name
  subnet_id           = module.network.aks_subnet_id
  kubernetes_version  = var.kubernetes_version
  node_count          = var.node_count
  node_vm_size        = var.node_vm_size
  os_disk_size_gb     = var.os_disk_size_gb
  sku_tier            = var.aks_sku_tier
  tags                = local.common_tags
}

module "storage" {
  source = "../../modules/storage"

  prefix              = var.prefix
  environment         = var.environment
  suffix              = random_string.storage_suffix.result
  location            = var.location
  resource_group_name = azurerm_resource_group.this.name
  replication         = var.storage_replication
  tags                = local.common_tags
}

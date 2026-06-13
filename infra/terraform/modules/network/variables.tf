variable "prefix" {
  description = "Prefijo corto de nombres."
  type        = string
}

variable "environment" {
  description = "Nombre del ambiente (dev/stage/prod)."
  type        = string
}

variable "location" {
  description = "Region de Azure."
  type        = string
}

variable "resource_group_name" {
  description = "Resource Group donde se crea la VNet."
  type        = string
}

variable "vnet_address_space" {
  description = "Address space CIDR de la VNet."
  type        = list(string)
  default     = ["10.10.0.0/16"]
}

variable "aks_subnet_address_prefixes" {
  description = "Prefijos CIDR de la subnet para nodos AKS."
  type        = list(string)
  default     = ["10.10.1.0/24"]
}

variable "tags" {
  description = "Tags comunes."
  type        = map(string)
  default     = {}
}

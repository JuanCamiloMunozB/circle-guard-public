variable "prefix" {
  description = "Prefijo corto de nombres."
  type        = string
}

variable "environment" {
  description = "Nombre del ambiente."
  type        = string
}

variable "location" {
  description = "Region de Azure."
  type        = string
}

variable "vnet_address_space" {
  description = "Address space CIDR de la VNet."
  type        = list(string)
}

variable "aks_subnet_address_prefixes" {
  description = "Prefijos CIDR para la subnet de AKS."
  type        = list(string)
}

variable "kubernetes_version" {
  description = "Version de Kubernetes para el cluster."
  type        = string
}

variable "node_count" {
  description = "Cantidad de nodos del system node pool."
  type        = number
}

variable "node_vm_size" {
  description = "SKU de VM para los nodos de AKS."
  type        = string
}

variable "os_disk_size_gb" {
  description = "Tamano del OS disk por nodo."
  type        = number
  default     = 32
}

variable "aks_sku_tier" {
  description = "SKU tier de AKS (Free o Standard)."
  type        = string
  default     = "Free"
}

variable "storage_replication" {
  description = "Tipo de replicacion para el Storage de artefactos."
  type        = string
  default     = "LRS"
}

variable "extra_tags" {
  description = "Tags adicionales especificos del ambiente."
  type        = map(string)
  default     = {}
}

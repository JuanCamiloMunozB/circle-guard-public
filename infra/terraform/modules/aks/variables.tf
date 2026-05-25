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
  description = "Resource Group donde se crea el cluster AKS."
  type        = string
}

variable "subnet_id" {
  description = "ID de la subnet donde se alojan los nodos del cluster."
  type        = string
}

variable "kubernetes_version" {
  description = "Version de Kubernetes para el cluster y el node pool."
  type        = string
}

variable "node_count" {
  description = "Cantidad de nodos en el system node pool."
  type        = number
}

variable "node_vm_size" {
  description = "SKU de VM para los nodos. Usar B-series (Standard_B2s) en dev/stage para minimizar costo."
  type        = string
}

variable "os_disk_size_gb" {
  description = "Tamano del OS disk en cada nodo."
  type        = number
  default     = 32
}

variable "sku_tier" {
  description = "SKU tier del control plane. Free no cobra control plane; Standard cobra ~0.10 USD/h."
  type        = string
  default     = "Free"

  validation {
    condition     = contains(["Free", "Standard"], var.sku_tier)
    error_message = "sku_tier debe ser Free o Standard."
  }
}

variable "service_cidr" {
  description = "CIDR para Service IPs del cluster."
  type        = string
  default     = "10.20.0.0/16"
}

variable "dns_service_ip" {
  description = "IP del servicio DNS dentro de service_cidr."
  type        = string
  default     = "10.20.0.10"
}

variable "tags" {
  description = "Tags comunes."
  type        = map(string)
  default     = {}
}

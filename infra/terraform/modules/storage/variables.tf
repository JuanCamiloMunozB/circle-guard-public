variable "prefix" {
  description = "Prefijo corto."
  type        = string
}

variable "environment" {
  description = "Nombre del ambiente."
  type        = string
}

variable "suffix" {
  description = "Sufijo aleatorio o numerico para garantizar unicidad global del Storage Account."
  type        = string
}

variable "location" {
  description = "Region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource Group destino."
  type        = string
}

variable "replication" {
  description = "Tipo de replicacion (LRS, ZRS, GRS). Dev/Stage: LRS; Prod: GRS."
  type        = string
  default     = "LRS"
}

variable "tags" {
  description = "Tags comunes."
  type        = map(string)
  default     = {}
}

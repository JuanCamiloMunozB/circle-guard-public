variable "prefix" {
  description = "Prefijo corto para los nombres de recursos (ej. cg). Maximo 8 chars para respetar el limite de 24 de Storage Account."
  type        = string

  validation {
    condition     = length(var.prefix) > 0 && length(var.prefix) <= 8
    error_message = "El prefix debe tener entre 1 y 8 caracteres."
  }
}

variable "location" {
  description = "Region de Azure para el Resource Group y la Storage Account del estado de Terraform."
  type        = string
}

variable "tags" {
  description = "Tags comunes aplicados a todos los recursos del bootstrap."
  type        = map(string)
  default = {
    project    = "circleguard"
    component  = "tfstate-backend"
    managed_by = "terraform"
  }
}

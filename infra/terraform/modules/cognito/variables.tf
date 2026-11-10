variable "name_prefix" {
  description = "Prefixo do user pool, do client e da Lambda."
  type        = string
}

variable "deletion_protection" {
  description = "ACTIVE em prod, INACTIVE em dev."
  type        = string
  default     = "INACTIVE"

  validation {
    condition     = contains(["ACTIVE", "INACTIVE"], var.deletion_protection)
    error_message = "deletion_protection deve ser ACTIVE ou INACTIVE."
  }
}

variable "log_retention_days" {
  description = "Retencao do log da Lambda de pre-token."
  type        = number
}

variable "logs_kms_key_arn" {
  description = "CMK do log group da Lambda."
  type        = string
}

variable "callback_urls" {
  description = "Callbacks do app client. O fluxo principal dos servicos e o password grant, mas o client deixa o authorization code preparado."
  type        = list(string)
  default     = ["http://localhost:8080/callback"]
}

variable "logout_urls" {
  description = "URLs de logout do app client."
  type        = list(string)
  default     = ["http://localhost:8080/"]
}

variable "aws_region" {
  description = "Regiao do bucket de state. O state de dev e prod fica nesta mesma regiao."
  type        = string
  default     = "sa-east-1"
}

variable "bucket_name" {
  description = "Nome globalmente unico do bucket de state. Sugestao: fluxo-caixa-tfstate-<account_id>."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "bucket_name deve ser um nome valido de bucket S3."
  }
}

variable "state_key_deletion_window_days" {
  description = "Janela de exclusao da CMK do bucket de state."
  type        = number
  default     = 30
}

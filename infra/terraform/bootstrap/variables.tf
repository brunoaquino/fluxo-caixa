variable "aws_region" {
  description = "Regiao do bucket de state. Um bucket por conta; a key separa dev, hml e prod."
  type        = string
  default     = "us-east-1"
}

variable "assume_role_arn" {
  description = "Role opcional assumida pelo provider. Use quando o bootstrap roda a partir de outra conta."
  type        = string
  default     = null
}

variable "owner" {
  description = "Tag Owner."
  type        = string
  default     = "plataforma"
}

variable "cost_center" {
  description = "Tag CostCenter."
  type        = string
  default     = "fluxo-caixa"
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

variable "noncurrent_version_expiration_days" {
  description = "Dias ate expirar versoes antigas do state. A versao corrente permanece."
  type        = number
  default     = 90

  validation {
    condition     = var.noncurrent_version_expiration_days >= 7
    error_message = "noncurrent_version_expiration_days deve ser pelo menos 7."
  }
}

variable "enable_object_lock" {
  description = "Object Lock GOVERNANCE no bucket. So pode ser ligado na criacao do bucket. Padrao false."
  type        = bool
  default     = false
}

variable "object_lock_retention_days" {
  description = "Retencao GOVERNANCE quando enable_object_lock e true. GOVERNANCE ainda permite apagar com permissao explicita, entao um apply quebrado nao prende o state para sempre."
  type        = number
  default     = 7
}

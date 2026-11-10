variable "name_prefix" {
  description = "Prefixo dos aliases. Ex.: fluxo-caixa-dev."
  type        = string
}

variable "region" {
  description = "Regiao usada nas conditions kms:ViaService e logs."
  type        = string
}

variable "deletion_window_days" {
  description = "Janela de exclusao das CMKs. 7 em dev, 30 em prod."
  type        = number
  default     = 30
}

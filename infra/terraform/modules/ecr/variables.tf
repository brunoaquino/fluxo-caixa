variable "name_prefix" {
  description = "Prefixo dos repositorios. Ex.: fluxo-caixa-dev."
  type        = string
}

variable "repository_names" {
  description = "Nomes logicos dos repositorios, sem o prefixo."
  type        = list(string)
}

variable "kms_key_arn" {
  description = "CMK das imagens."
  type        = string
}

variable "image_count_to_keep" {
  description = "Quantidade de imagens mantidas pela lifecycle policy."
  type        = number
  default     = 20
}

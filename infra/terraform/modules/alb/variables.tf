variable "name_prefix" {
  description = "Prefixo do ALB, dos target groups e do bucket de logs."
  type        = string
}

variable "vpc_id" {
  description = "VPC do ALB."
  type        = string
}

variable "public_subnet_ids" {
  description = "Subnets publicas, uma por AZ."
  type        = list(string)
}

variable "certificate_arn" {
  description = "ARN do certificado ACM na mesma regiao do ALB. O certificado nao e criado aqui."
  type        = string

  validation {
    condition     = can(regex("^arn:aws:acm:", var.certificate_arn))
    error_message = "certificate_arn deve ser um ARN de certificado ACM."
  }
}

variable "deletion_protection" {
  description = "Protege o ALB contra delete. true em prod."
  type        = bool
}

variable "access_logs_bucket_name" {
  description = "Nome globalmente unico do bucket de access logs."
  type        = string
}

variable "logs_kms_key_arn" {
  description = "CMK dos access logs. A key policy precisa autorizar logdelivery.elasticloadbalancing.amazonaws.com."
  type        = string
}

variable "access_log_expiration_days" {
  description = "Dias ate expirar o access log."
  type        = number
  default     = 90
}

variable "waf_rate_limit" {
  description = "Requisicoes por IP em 5 minutos. 50 req/s sustentados geram 15000, entao o teto fica acima disso."
  type        = number
  default     = 30000

  validation {
    condition     = var.waf_rate_limit >= 100 && var.waf_rate_limit <= 2000000000
    error_message = "waf_rate_limit deve respeitar o minimo de 100 da AWS."
  }
}

variable "services" {
  description = "Target groups e regras de caminho."
  type = map(object({
    port          = number
    path_patterns = list(string)
    health_path   = string
    priority      = number
  }))
}

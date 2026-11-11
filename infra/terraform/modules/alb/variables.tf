variable "name_prefix" {
  description = "Prefixo do ALB, dos target groups e do bucket de logs."
  type        = string
}

variable "vpc_id" {
  description = "VPC do ALB."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets privadas do ALB interno, uma por AZ. O CloudFront chega por VPC Origin."
  type        = list(string)
}

variable "enable_waf" {
  description = "WAF regional no ALB. Padrao false: o ALB e interno e o WAF de borda (escopo CLOUDFRONT) ja inspeciona o cliente. Um WAF regional veria so o IP do CloudFront e cobraria de novo."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Protege o ALB contra delete. true em prod."
  type        = bool
}

variable "access_logs_bucket_name" {
  description = "Nome globalmente unico do bucket de access logs."
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

variable "stage_path_prefix" {
  description = "Nome do stage do API Gateway, sem barra. Quando preenchido, a regra tambem aceita /<stage><path> e reescreve tirando esse prefixo. Vazio nao altera o path."
  type        = string
  default     = ""

  validation {
    condition     = var.stage_path_prefix == "" || can(regex("^[a-zA-Z0-9]+$", var.stage_path_prefix))
    error_message = "stage_path_prefix e o nome do stage, sem barra."
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

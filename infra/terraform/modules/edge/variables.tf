variable "name_prefix" {
  description = "Prefixo dos recursos de borda. Ex.: fluxo-caixa-dev."
  type        = string
}

variable "alb_arn" {
  description = "ARN do ALB interno usado como VPC Origin."
  type        = string
}

variable "alb_dns_name" {
  description = "DNS do ALB interno. O CloudFront usa este hostname na origem."
  type        = string
}

variable "acm_certificate_arn" {
  description = "Certificado ACM em us-east-1. Nulo usa o certificado padrao *.cloudfront.net."
  type        = string
  default     = null

  validation {
    condition     = var.acm_certificate_arn == null || can(regex("^arn:aws:acm:us-east-1:", var.acm_certificate_arn))
    error_message = "acm_certificate_arn tem de ser um certificado ACM em us-east-1."
  }
}

variable "domain_names" {
  description = "Aliases da distribuicao. Vazio quando o certificado padrao do CloudFront e usado."
  type        = list(string)
  default     = []

  validation {
    condition     = (var.acm_certificate_arn == null) == (length(var.domain_names) == 0)
    error_message = "Dominio customizado exige aliases e ACM em us-east-1. Sem dominio, deixe os dois vazios."
  }
}

variable "logs_kms_key_arn" {
  description = "CMK do log group do WAF. A chave precisa estar em us-east-1, mesma regiao do escopo CLOUDFRONT."
  type        = string
}

variable "log_retention_days" {
  description = "Retencao dos logs do WAF."
  type        = number
}

variable "waf_rate_limit" {
  description = "Requisicoes por IP em 5 minutos no WAF de borda."
  type        = number
  default     = 30000

  validation {
    condition     = var.waf_rate_limit >= 100 && var.waf_rate_limit <= 2000000000
    error_message = "waf_rate_limit deve respeitar o minimo de 100 da AWS."
  }
}

variable "enable_api_gateway" {
  description = "true envia /api/v1/* ao API Gateway regional. false mantem VPC Origin para o ALB."
  type        = bool
  default     = false
}

variable "api_gateway_domain_name" {
  description = "Host execute-api da REST API regional. Obrigatorio com enable_api_gateway."
  type        = string
  default     = null
}

variable "api_gateway_origin_path" {
  description = "origin_path do stage, com barra. Ex.: /v1."
  type        = string
  default     = null
}

variable "origin_verify_header_value" {
  description = "Valor do header X-Origin-Verify. Sensivel. O CloudFront sobrescreve o valor enviado pelo cliente."
  type        = string
  default     = null
  sensitive   = true

  validation {
    condition = var.enable_api_gateway == false || (
      var.api_gateway_domain_name != null && var.api_gateway_domain_name != "" &&
      var.api_gateway_origin_path != null && startswith(var.api_gateway_origin_path, "/") &&
      var.origin_verify_header_value != null && var.origin_verify_header_value != ""
    )
    error_message = "Com enable_api_gateway, informe o host execute-api, o origin_path do stage e o header X-Origin-Verify."
  }
}

variable "anonymous_ip_count_only" {
  description = "AnonymousIpList em count (dev) em vez de block. Count deixa o trafego passar e so mede."
  type        = bool
  default     = false
}

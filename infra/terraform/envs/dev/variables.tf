variable "environment" {
  description = "Tem de ser dev neste diretorio. O state e a nomenclatura dependem disso."
  type        = string
  default     = "dev"

  validation {
    condition     = var.environment == "dev"
    error_message = "envs/dev so aplica environment = dev."
  }
}

variable "aws_region" {
  description = "Regiao da carga. Padrao us-east-1."
  type        = string
  default     = "us-east-1"
}

variable "assume_role_arn" {
  description = "Role da conta deste ambiente. Nulo usa as credenciais correntes. Preencha quando cada ambiente tem a propria conta."
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

variable "alert_email" {
  description = "E-mail dos alarmes."
  type        = string
  default     = ""
}

variable "image_tag" {
  description = "Tag imutavel no ECR."
  type        = string
  default     = "bootstrap"
}

variable "acm_certificate_arn" {
  description = "ACM us-east-1 do CloudFront. Nulo usa *.cloudfront.net."
  type        = string
  default     = null
}

variable "domain_names" {
  description = "Aliases do CloudFront."
  type        = list(string)
  default     = []
}

variable "enable_api_gateway" {
  description = "true coloca o API Gateway entre o CloudFront e o ALB. false volta a VPC Origin."
  type        = bool
  default     = true
}

variable "api_cache_enabled" {
  description = "Cache do stage do API Gateway. Padrao desligado."
  type        = bool
  default     = false
}

variable "api_key_required" {
  description = "Rotas que exigem API key alem do JWT."
  type        = map(bool)
  default     = {}
}

variable "api_consumers" {
  description = "Consumidores de integracao. Vazio nao cria API key."
  type = map(object({
    description    = optional(string, "")
    quota_limit    = number
    quota_period   = string
    throttle_rate  = number
    throttle_burst = number
  }))
  default = {}
}

variable "api_canary" {
  description = "Canary do stage. Nulo nao cria."
  type = object({
    percent_traffic          = number
    use_stage_cache          = optional(bool, false)
    stage_variable_overrides = optional(map(string), {})
  })
  default = null
}

variable "sizing" {
  description = "Sobrescritas do dimensionamento. O exemplo repete os defaults da secao 15."
  type        = any
  default     = {}
}

variable "name_prefix" {
  description = "Prefixo dos recursos. Ex.: fluxo-caixa-dev."
  type        = string
}

variable "vpc_id" {
  description = "VPC onde o VPC Link v2 cria as ENIs."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets privadas de aplicacao, as mesmas do ALB, uma por AZ."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "O VPC Link v2 precisa de subnets em pelo menos duas AZs."
  }
}

variable "alb_arn" {
  description = "ARN do ALB interno. integration_target do VPC Link v2."
  type        = string
}

variable "alb_dns_name" {
  description = "DNS do ALB. Vira o Host da URI de integracao. O path vai no restante da URI."
  type        = string
}

variable "alb_security_group_id" {
  description = "Security group do ALB. Recebe ingresso na porta 80 vindo do VPC Link."
  type        = string
}

variable "cognito_user_pool_arn" {
  description = "ARN do user pool usado pelo authorizer COGNITO_USER_POOLS."
  type        = string
}

variable "scope_prefix" {
  description = "Prefixo do resource server. Na AWS e fluxo-caixa/."
  type        = string
  default     = "fluxo-caixa/"

  validation {
    condition     = endswith(var.scope_prefix, "/")
    error_message = "scope_prefix termina com barra, como o resource server do Cognito."
  }
}

variable "logs_kms_key_arn" {
  description = "CMK dos log groups de access log, WAF e Lambda."
  type        = string
}

variable "secret_kms_key_arn" {
  description = "CMK propria do segredo X-Origin-Verify. A key policy precisa autorizar o Secrets Manager."
  type        = string
}

variable "log_retention_days" {
  description = "Retencao dos access logs, do WAF regional e da Lambda de rotacao."
  type        = number
}

variable "stage_name" {
  description = "Stage publicado. O CloudFront usa este nome como origin_path."
  type        = string
  default     = "v1"

  validation {
    condition     = can(regex("^[a-zA-Z0-9]+$", var.stage_name))
    error_message = "stage_name so aceita letras e numeros. O valor do blueprint e v1."
  }
}

variable "stage_throttling" {
  description = "Throttle padrao do stage, em req/s e burst. O method setting especifico sobrescreve."
  type = object({
    rate_limit  = number
    burst_limit = number
  })

  validation {
    condition     = var.stage_throttling.rate_limit >= 1 && var.stage_throttling.burst_limit >= var.stage_throttling.rate_limit
    error_message = "stage_throttling exige rate >= 1 e burst >= rate."
  }
}

variable "route_throttling" {
  description = "Throttle por classe de rota. consolidado_read fica acima de 50 req/s fora de dev."
  type = object({
    consolidado_read = object({
      rate_limit  = number
      burst_limit = number
    })
    lancamentos_write = object({
      rate_limit  = number
      burst_limit = number
    })
    lancamentos_read = object({
      rate_limit  = number
      burst_limit = number
    })
  })

  validation {
    condition = alltrue([
      for bucket in [var.route_throttling.consolidado_read, var.route_throttling.lancamentos_write, var.route_throttling.lancamentos_read] :
      bucket.rate_limit >= 1 && bucket.burst_limit >= bucket.rate_limit
    ])
    error_message = "Cada classe de rota exige rate >= 1 e burst >= rate."
  }
}

variable "require_consolidado_above_peak" {
  description = "Ligado em hml e prod. O GET do consolidado precisa passar de 50 req/s."
  type        = bool
  default     = false

  validation {
    condition     = !var.require_consolidado_above_peak || var.route_throttling.consolidado_read.rate_limit > 50
    error_message = "hml e prod exigem throttle do consolidado acima do pico de 50 req/s."
  }
}

variable "cache_enabled" {
  description = "Cache de stage. Padrao false: a resposta e por comerciante e leva Authorization."
  type        = bool
  default     = false
}

variable "cache_ttl_seconds" {
  description = "TTL do cache quando cache_enabled e true. Ignorado com o cache desligado."
  type        = number
  default     = 0

  validation {
    condition     = var.cache_ttl_seconds >= 0 && var.cache_ttl_seconds <= 3600
    error_message = "cache_ttl_seconds fica entre 0 e 3600."
  }
}

variable "canary" {
  description = "Canary do stage. Nulo nao cria canary. percent_traffic de 0 a 100."
  type = object({
    percent_traffic          = number
    use_stage_cache          = optional(bool, false)
    stage_variable_overrides = optional(map(string), {})
  })
  default = null

  validation {
    condition     = var.canary == null || (var.canary.percent_traffic >= 0 && var.canary.percent_traffic <= 100)
    error_message = "percent_traffic do canary fica entre 0 e 100."
  }
}

variable "api_key_required" {
  description = "Exige API key alem do JWT. Chave no formato METHOD /path. Ausente significa false."
  type        = map(bool)
  default     = {}

  validation {
    condition = alltrue([
      for key in keys(var.api_key_required) : contains([
        "POST /api/v1/lancamentos",
        "GET /api/v1/lancamentos",
        "GET /api/v1/lancamentos/{id}",
        "POST /api/v1/lancamentos/{id}/estornos",
        "GET /api/v1/consolidados/diario",
        "GET /api/v1/consolidados/diario/{data}",
      ], key)
    ])
    error_message = "api_key_required so aceita as rotas de negocio da secao 7."
  }
}

variable "api_consumers" {
  description = "Consumidores de integracao. Vazio nao cria usage plan nem API key. Cliente final usa so JWT."
  type = map(object({
    description    = optional(string, "")
    quota_limit    = number
    quota_period   = string
    throttle_rate  = number
    throttle_burst = number
  }))
  default = {}

  validation {
    condition = alltrue([
      for consumer in values(var.api_consumers) :
      contains(["DAY", "WEEK", "MONTH"], consumer.quota_period) &&
      consumer.quota_limit > 0 &&
      consumer.throttle_rate >= 1 &&
      consumer.throttle_burst >= consumer.throttle_rate
    ])
    error_message = "Consumidor exige cota positiva, periodo DAY WEEK ou MONTH, e burst >= rate."
  }
}

variable "rotation_days" {
  description = "Dias entre rotacoes do X-Origin-Verify. O valor anterior continua valido ate a proxima rotacao."
  type        = number
  default     = 30

  validation {
    condition     = var.rotation_days >= 1 && var.rotation_days <= 365
    error_message = "rotation_days fica entre 1 e 365."
  }
}

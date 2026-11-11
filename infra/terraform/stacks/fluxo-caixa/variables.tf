variable "environment" {
  description = "Ambiente. Entra no prefixo fluxo-caixa-<env>-<recurso>."
  type        = string

  validation {
    condition     = contains(["dev", "hml", "prod"], var.environment)
    error_message = "environment deve ser dev, hml ou prod."
  }
}

variable "aws_region" {
  description = "Regiao da carga. A borda CloudFront e WAF continua em us-east-1."
  type        = string
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-[0-9]$", var.aws_region))
    error_message = "aws_region deve ser uma regiao AWS, por exemplo us-east-1."
  }
}

variable "alert_email" {
  description = "E-mail dos alarmes. Vazio deixa o topico sem inscricao."
  type        = string
  default     = ""
}

variable "image_tag" {
  description = "Tag imutavel publicada no ECR."
  type        = string
  default     = "bootstrap"

  validation {
    condition     = var.image_tag != "latest"
    error_message = "Nao use a tag latest. O ECR esta imutavel."
  }
}

variable "acm_certificate_arn" {
  description = "Certificado ACM us-east-1 do CloudFront. Nulo usa *.cloudfront.net."
  type        = string
  default     = null
}

variable "domain_names" {
  description = "Aliases do CloudFront. Vazio junto com acm_certificate_arn nulo."
  type        = list(string)
  default     = []
}

variable "adot_image" {
  description = "Imagem do ADOT collector, com tag fixa e suporte a ARM64."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.51.0"
}

variable "rds_engine_version" {
  description = "Versao menor do PostgreSQL 17."
  type        = string
  default     = "17.11"
}

variable "redis_engine_version" {
  description = "Versao do Valkey."
  type        = string
  default     = "8.2"
}

variable "lancamentos_request_count_target" {
  description = "Requests por minuto por task de lancamentos."
  type        = number
  default     = 600
}

variable "consolidado_request_count_target" {
  description = "Requests por minuto por task do consolidado. 1500 cobre cerca de 25 req/s."
  type        = number
  default     = 1500
}

variable "consolidado_queue_backlog_per_task" {
  description = "Mensagens visiveis por task antes de subir mais tasks."
  type        = number
  default     = 20
}

variable "sizing" {
  description = "Sobrescreve o dimensionamento da secao 15. Atributo nulo herda o default do ambiente."
  type = object({
    az_count                          = optional(number)
    nat_gateway_per_az                = optional(bool)
    interface_endpoint_az_count       = optional(number)
    vpc_cidr                          = optional(string)
    flow_log_traffic_type             = optional(string)
    log_retention_days                = optional(number)
    kms_deletion_window_days          = optional(number)
    alb_deletion_protection           = optional(bool)
    cognito_deletion_protection       = optional(string)
    enable_execute_command            = optional(bool)
    container_insights                = optional(string)
    capacity_provider                 = optional(string)
    rds_instance_class                = optional(string)
    rds_allocated_storage             = optional(number)
    rds_max_allocated_storage         = optional(number)
    rds_multi_az                      = optional(bool)
    rds_deletion_protection           = optional(bool)
    rds_skip_final_snapshot           = optional(bool)
    rds_backup_retention_days         = optional(number)
    rds_apply_immediately             = optional(bool)
    rds_monitoring_interval           = optional(number)
    rds_rotation_days                 = optional(number)
    read_replica_count                = optional(number)
    enable_rds_proxy                  = optional(bool)
    rds_connection_threshold          = optional(number)
    rds_free_storage_threshold        = optional(number)
    redis_node_type                   = optional(string)
    redis_num_cache_clusters          = optional(number)
    redis_automatic_failover          = optional(bool)
    redis_multi_az                    = optional(bool)
    redis_snapshot_retention          = optional(number)
    lancamentos_cpu                   = optional(number)
    lancamentos_memory                = optional(number)
    lancamentos_desired_count         = optional(number)
    lancamentos_min_capacity          = optional(number)
    lancamentos_max_capacity          = optional(number)
    consolidado_cpu                   = optional(number)
    consolidado_memory                = optional(number)
    consolidado_desired_count         = optional(number)
    consolidado_min_capacity          = optional(number)
    consolidado_max_capacity          = optional(number)
    anonymous_ip_count_only           = optional(bool)
    waf_rate_limit                    = optional(number)
    api_stage_rate_limit              = optional(number)
    api_stage_burst_limit             = optional(number)
    api_consolidado_rate_limit        = optional(number)
    api_consolidado_burst_limit       = optional(number)
    api_lancamentos_write_rate_limit  = optional(number)
    api_lancamentos_write_burst_limit = optional(number)
    api_lancamentos_read_rate_limit   = optional(number)
    api_lancamentos_read_burst_limit  = optional(number)
    origin_verify_rotation_days       = optional(number)
  })
  default = {}
}

variable "enable_api_gateway" {
  description = "true coloca o API Gateway REST regional entre o CloudFront e o ALB. false mantem VPC Origin direto no ALB."
  type        = bool
  default     = true
}

variable "api_cache_enabled" {
  description = "Cache do stage v1. Padrao false porque a resposta e por comerciante."
  type        = bool
  default     = false
}

variable "api_key_required" {
  description = "Rotas que exigem API key alem do JWT. Chave METHOD /path. Padrao nenhuma."
  type        = map(bool)
  default     = {}
}

variable "api_consumers" {
  description = "Consumidores de integracao com usage plan e API key. Vazio nao cria chave."
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
  description = "Canary do stage. Nulo nao cria. O deployment do canary e o mesmo do stage ate existir uma segunda definicao."
  type = object({
    percent_traffic          = number
    use_stage_cache          = optional(bool, false)
    stage_variable_overrides = optional(map(string), {})
  })
  default = null
}

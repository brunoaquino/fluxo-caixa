variable "name_prefix" {
  description = "Prefixo dos log groups, do dashboard e dos alarmes."
  type        = string
}

variable "region" {
  description = "Regiao do dashboard."
  type        = string
}

variable "log_retention_days" {
  description = "Retencao dos logs de aplicacao e de metricas EMF."
  type        = number
}

variable "logs_kms_key_arn" {
  description = "CMK dos log groups."
  type        = string
}

variable "alerts_kms_key_arn" {
  description = "CMK do topico de alertas. A key policy precisa autorizar o CloudWatch."
  type        = string
}

variable "alert_email" {
  description = "E-mail inscrito no topico. Vazio nao cria inscricao. A AWS envia um e-mail de confirmacao."
  type        = string
  default     = ""
}

variable "cluster_name" {
  description = "Cluster ECS das metricas de CPU e memoria."
  type        = string
}

variable "alb_arn_suffix" {
  description = "Sufixo do ALB."
  type        = string
}

variable "services" {
  description = "Servicos monitorados. O nome da task ECS precisa bater com ecs_service_name."
  type = map(object({
    ecs_service_name        = string
    target_group_arn_suffix = string
    latency_p99_seconds     = number
  }))
}

variable "queue_name" {
  description = "Fila principal do consolidado."
  type        = string
}

variable "dlq_name" {
  description = "DLQ do consolidado."
  type        = string
}

variable "queue_visible_threshold" {
  description = "Alarme de profundidade da fila."
  type        = number
  default     = 100
}

variable "queue_age_threshold_seconds" {
  description = "Alarme da idade da mensagem mais antiga. Acima do SLO de 5s para nao disparar em atraso curto."
  type        = number
  default     = 120
}

variable "rotation_secret_arns" {
  description = "Secrets de banco cuja falha de rotacao deve ir para o topico de alertas. O mapa entra na descricao da regra para o apply esperar o secret existir."
  type        = map(string)
  default     = {}
}

variable "api_gateway" {
  description = "API Gateway monitorada. Nulo nao cria alarme nem widget."
  type = object({
    api_name = string
    stage    = string
  })
  default = null
}

variable "rds_instances" {
  description = "Instancias RDS monitoradas."
  type = map(object({
    identifier             = string
    connection_threshold   = number
    free_storage_threshold = number
  }))
}

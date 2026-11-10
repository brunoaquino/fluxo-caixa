variable "aws_region" {
  description = "Regiao de todos os recursos."
  type        = string
  default     = "sa-east-1"
}

variable "environment" {
  description = "Nome do ambiente. Entra no prefixo dos recursos."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment deve ser dev ou prod."
  }
}

variable "certificate_arn" {
  description = "ARN do certificado ACM do listener HTTPS. Nao e criado por este stack."
  type        = string
}

variable "access_logs_bucket_name" {
  description = "Bucket globalmente unico dos access logs do ALB."
  type        = string
}

variable "alert_email" {
  description = "E-mail dos alarmes. Vazio deixa o topico sem inscricao."
  type        = string
  default     = ""
}

variable "vpc_cidr" {
  description = "CIDR da VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "nat_gateway_per_az" {
  description = "Um NAT por AZ em prod. Um NAT compartilhado em dev."
  type        = bool
}

variable "interface_endpoint_az_count" {
  description = "AZs com interface endpoint. 1 em dev, 3 em prod."
  type        = number
}

variable "flow_log_traffic_type" {
  description = "ACCEPT, REJECT ou ALL."
  type        = string
}

variable "log_retention_days" {
  description = "Retencao de logs."
  type        = number
}

variable "kms_deletion_window_days" {
  description = "Janela de exclusao das CMKs."
  type        = number
}

variable "alb_deletion_protection" {
  description = "Protecao contra delete do ALB."
  type        = bool
}

variable "cognito_deletion_protection" {
  description = "ACTIVE ou INACTIVE."
  type        = string
}

variable "waf_rate_limit" {
  description = "Limite do WAF por IP a cada 5 minutos."
  type        = number
  default     = 30000
}

variable "enable_execute_command" {
  description = "ECS Exec nas tasks."
  type        = bool
}

variable "topic_name" {
  description = "Topico SNS. Prefixo de ambiente para dev e prod coexistirem na mesma conta."
  type        = string
}

variable "queue_name" {
  description = "Fila do consolidado."
  type        = string
}

variable "dlq_name" {
  description = "DLQ do consolidado."
  type        = string
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

variable "adot_image" {
  description = "Imagem do ADOT collector."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.51.0"
}

variable "rds_engine_version" {
  description = "Versao menor do PostgreSQL 17."
  type        = string
  default     = "17.11"
}

variable "rds_instance_class" {
  description = "Classe das duas instancias RDS."
  type        = string
}

variable "rds_allocated_storage" {
  description = "Storage inicial em GiB."
  type        = number
}

variable "rds_max_allocated_storage" {
  description = "Teto do autoscaling de storage."
  type        = number
}

variable "rds_multi_az" {
  description = "Multi-AZ."
  type        = bool
}

variable "rds_deletion_protection" {
  description = "Protecao contra delete do RDS."
  type        = bool
}

variable "rds_skip_final_snapshot" {
  description = "Pula o snapshot final. Somente dev."
  type        = bool
}

variable "rds_backup_retention_days" {
  description = "Retencao que habilita o PITR."
  type        = number
}

variable "rds_apply_immediately" {
  description = "Aplica mudancas de RDS e Redis na hora."
  type        = bool
}

variable "rds_connection_threshold" {
  description = "Alarme de conexoes. Abaixo do max_connections da classe."
  type        = number
}

variable "rds_free_storage_threshold" {
  description = "Alarme de espaco livre, em bytes."
  type        = number
}

variable "redis_engine_version" {
  description = "Versao do Valkey."
  type        = string
  default     = "8.2"
}

variable "redis_node_type" {
  description = "Tipo do no."
  type        = string
}

variable "redis_num_cache_clusters" {
  description = "Primario mais replicas."
  type        = number
}

variable "redis_automatic_failover" {
  description = "Failover automatico."
  type        = bool
}

variable "redis_multi_az" {
  description = "Replica em outra AZ."
  type        = bool
}

variable "redis_snapshot_retention" {
  description = "Dias de snapshot do Redis."
  type        = number
}

variable "lancamentos_cpu" {
  description = "CPU Fargate de lancamentos."
  type        = number
}

variable "lancamentos_memory" {
  description = "Memoria de lancamentos."
  type        = number
}

variable "lancamentos_desired_count" {
  description = "Tasks iniciais de lancamentos."
  type        = number
}

variable "lancamentos_min_capacity" {
  description = "Minimo do scaling de lancamentos."
  type        = number
}

variable "lancamentos_max_capacity" {
  description = "Maximo do scaling de lancamentos."
  type        = number
}

variable "lancamentos_request_count_target" {
  description = "Requests por minuto por task de lancamentos."
  type        = number
}

variable "consolidado_cpu" {
  description = "CPU Fargate do consolidado."
  type        = number
}

variable "consolidado_memory" {
  description = "Memoria do consolidado."
  type        = number
}

variable "consolidado_desired_count" {
  description = "Tasks iniciais do consolidado."
  type        = number
}

variable "consolidado_min_capacity" {
  description = "Minimo do scaling do consolidado. Prod fica em 2."
  type        = number
}

variable "consolidado_max_capacity" {
  description = "Maximo do scaling do consolidado."
  type        = number
}

variable "consolidado_request_count_target" {
  description = "Requests por minuto por task. 1500 cobre cerca de 25 req/s; duas tasks cobrem o pico de 50."
  type        = number
}

variable "consolidado_queue_backlog_per_task" {
  description = "Mensagens visiveis por task antes de subir mais tasks."
  type        = number
}

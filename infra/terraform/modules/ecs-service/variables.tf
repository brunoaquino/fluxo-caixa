variable "name" {
  description = "Nome do servico ECS e prefixo das roles. Ex.: fluxo-caixa-dev-lancamentos."
  type        = string
}

variable "container_name" {
  description = "Nome do container da aplicacao. O CD usa este nome no render da task definition."
  type        = string
}

variable "cluster_arn" {
  description = "ARN do cluster."
  type        = string
}

variable "cluster_name" {
  description = "Nome do cluster, usado no scaling da fila."
  type        = string
}

variable "vpc_id" {
  description = "VPC das tasks."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR da VPC, liberado apenas para DNS."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets de aplicacao. Uma task por AZ quando desired_count acompanha a quantidade de AZs."
  type        = list(string)
}

variable "container_image" {
  description = "Imagem no ECR, com tag imutavel."
  type        = string
}

variable "container_port" {
  description = "Porta HTTP do Spring Boot."
  type        = number
}

variable "cpu" {
  description = "CPU da task Fargate. 256, 512, 1024..."
  type        = number
}

variable "memory" {
  description = "Memoria da task em MiB, compativel com a CPU."
  type        = number
}

variable "desired_count" {
  description = "Tasks iniciais. O autoscaling assume o valor depois; o Terraform ignora mudancas posteriores."
  type        = number
}

variable "min_capacity" {
  description = "Minimo do autoscaling. Em prod, 2 para espalhar entre AZs."
  type        = number
}

variable "max_capacity" {
  description = "Maximo do autoscaling."
  type        = number
}

variable "environment" {
  description = "Variaveis de ambiente do contrato. Sem segredo."
  type        = map(string)
}

variable "secrets" {
  description = "Segredos injetados pelo ECS a partir do Secrets Manager. valueFrom pode apontar para uma chave JSON."
  type = list(object({
    name      = string
    valueFrom = string
  }))
  default = []
}

variable "secret_arns" {
  description = "ARNs puros dos secrets, para o IAM da execution role."
  type        = list(string)
  default     = []
}

variable "execution_kms_key_arns" {
  description = "CMKs que a execution role precisa decifrar (secret do RDS e logs)."
  type        = list(string)
  default     = []
}

variable "task_policy_statements" {
  description = "Permissoes extras da task role. Lancamentos publica no SNS; consolidado consome a fila."
  type = list(object({
    sid       = string
    actions   = list(string)
    resources = list(string)
  }))
  default = []
}

variable "log_group_name" {
  description = "Log group da aplicacao e do sidecar."
  type        = string
}

variable "log_group_arn" {
  description = "ARN do log group da aplicacao."
  type        = string
}

variable "metrics_log_group_name" {
  description = "Log group das metricas EMF."
  type        = string
}

variable "metrics_log_group_arn" {
  description = "ARN do log group de metricas."
  type        = string
}

variable "aws_region" {
  description = "Regiao usada pelo awslogs e pelo ADOT."
  type        = string
}

variable "target_group_arn" {
  description = "Target group do ALB."
  type        = string
}

variable "alb_security_group_id" {
  description = "Security group do ALB."
  type        = string
}

variable "vpc_endpoint_security_group_id" {
  description = "Security group dos interface endpoints. Nulo abre HTTPS 443 para a internet (NAT), usado em dev."
  type        = string
  default     = null
}

variable "capacity_provider_strategy" {
  description = "Capacity providers da task. Dev usa FARGATE_SPOT. Hml e prod usam FARGATE."
  type = list(object({
    capacity_provider = string
    weight            = number
    base              = optional(number, 0)
  }))
  default = [
    {
      capacity_provider = "FARGATE"
      weight            = 1
      base              = 0
    }
  ]
}

variable "task_secret_arns" {
  description = "Secrets que a task role le em runtime (AWS JDBC Wrapper). Nao vao para a execution role."
  type        = list(string)
  default     = []
}

variable "task_secret_kms_key_arns" {
  description = "CMKs dos secrets lidos pela task role. A policy concede apenas kms:Decrypt."
  type        = list(string)
  default     = []
}

variable "data_plane_access" {
  description = "Saida para RDS ou Redis do proprio servico. O modulo tambem abre o ingress no SG de destino."
  type = list(object({
    security_group_id = string
    port              = number
    description       = string
  }))
  default = []
}

variable "readonly_root_filesystem" {
  description = "Filesystem raiz somente leitura, com /tmp montado a parte."
  type        = bool
  default     = true
}

variable "enable_execute_command" {
  description = "Liga o ECS Exec. Util em dev, desligado em prod."
  type        = bool
  default     = false
}

variable "enable_request_count_scaling" {
  description = "Target tracking de ALBRequestCountPerTarget."
  type        = bool
  default     = false

  validation {
    condition     = !var.enable_request_count_scaling || var.alb_resource_label != null
    error_message = "enable_request_count_scaling exige alb_resource_label."
  }
}

variable "request_count_target" {
  description = "Requisicoes por minuto por task. 1500 equivale a cerca de 25 req/s."
  type        = number
  default     = 1500
}

variable "alb_resource_label" {
  description = "arn_suffix do ALB + / + arn_suffix do target group."
  type        = string
  default     = null
}

variable "cpu_target" {
  description = "Percentual de CPU alvo do target tracking."
  type        = number
  default     = 60
}

variable "queue_name" {
  description = "Fila usada no scaling por backlog. Nulo desliga esse scaling."
  type        = string
  default     = null

  validation {
    condition     = var.queue_backlog_per_task == null || var.queue_name != null
    error_message = "queue_backlog_per_task exige queue_name."
  }
}

variable "queue_backlog_per_task" {
  description = "Mensagens visiveis por task. Nulo desliga o scaling da fila."
  type        = number
  default     = null
}

variable "adot_image" {
  description = "Imagem do ADOT collector, com tag fixa e suporte a ARM64."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.51.0"
}

variable "health_check_grace_period_seconds" {
  description = "Tempo para o Spring Boot subir antes do ALB contar falha de readiness."
  type        = number
  default     = 120
}

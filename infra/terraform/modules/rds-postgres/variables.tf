variable "identifier" {
  description = "Identificador da instancia RDS."
  type        = string
}

variable "db_name" {
  description = "Database inicial. Um banco por servico."
  type        = string
}

variable "username" {
  description = "Usuario mestre. A senha fica no Secrets Manager (manage_master_user_password)."
  type        = string
}

variable "engine_version" {
  description = "Versao menor do PostgreSQL 17 disponivel na regiao."
  type        = string
  default     = "17.11"
}

variable "instance_class" {
  description = "Classe da instancia. Dev usa burstable pequena; prod pode subir sem trocar o modulo."
  type        = string
}

variable "allocated_storage" {
  description = "Storage inicial em GiB. gp3 exige pelo menos 20."
  type        = number
}

variable "max_allocated_storage" {
  description = "Teto do autoscaling de storage."
  type        = number
}

variable "multi_az" {
  description = "Multi-AZ. true em prod, false em dev."
  type        = bool
}

variable "deletion_protection" {
  description = "Impede o delete da instancia. true em prod."
  type        = bool
}

variable "skip_final_snapshot" {
  description = "Pula o snapshot final. true somente em dev."
  type        = bool
}

variable "backup_retention_days" {
  description = "Retencao de backup. PITR fica ativo com valor maior que zero. Meta de RPO: 5 minutos."
  type        = number

  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35
    error_message = "backup_retention_days deve ficar entre 1 e 35."
  }
}

variable "apply_immediately" {
  description = "Aplica mudancas na hora. true em dev, false em prod."
  type        = bool
  default     = false
}

variable "kms_key_arn" {
  description = "CMK do storage, do Performance Insights e do secret mestre."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets de dados."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC do security group."
  type        = string
}

variable "secret_kms_key_arn" {
  description = "CMK exclusiva do secret da senha mestre. Distinta da CMK do storage."
  type        = string
}

variable "manage_master_user_password" {
  description = "RDS cria o secret e rotaciona a senha. Permanece true para a task usar DB_SECRET_ARN."
  type        = bool
  default     = true

  validation {
    condition     = var.manage_master_user_password
    error_message = "manage_master_user_password deve ser true. A aplicacao nao recebe DB_PASSWORD."
  }
}

variable "master_password_rotation_days" {
  description = "Intervalo da rotacao do secret mestre. Dev 90, hml e prod 30. O RDS ja rotaciona; este valor ajusta a agenda."
  type        = number
  default     = 30

  validation {
    condition     = var.master_password_rotation_days >= 1 && var.master_password_rotation_days <= 365
    error_message = "master_password_rotation_days deve ficar entre 1 e 365."
  }
}

variable "read_replica_count" {
  description = "Replicas de leitura classicas. Padrao 0. Com manage_master_user_password a AWS recusa read replica de PostgreSQL. A combinacao e barrada pela precondition de aws_db_instance.this. Alternativas: Multi-AZ DB Cluster, Aurora, ou senha rotacionada por Lambda."
  type        = number
  default     = 0

  validation {
    condition     = var.read_replica_count >= 0 && var.read_replica_count <= 5
    error_message = "read_replica_count deve ficar entre 0 e 5."
  }

  validation {
    condition     = var.read_replica_count == 0 || !var.manage_master_user_password
    error_message = "A AWS nao cria read replica de RDS for PostgreSQL quando manage_master_user_password esta ligado (https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html). Mantenha read_replica_count = 0. Alternativas: RDS Multi-AZ DB Cluster com reader endpoint, Aurora PostgreSQL com readers, ou senha fora do Secrets Manager com rotacao por Lambda. No gatilho de leitura, a recomendacao e o Multi-AZ DB Cluster. Ver ADR 0030."
  }
}

variable "replica_instance_class" {
  description = "Classe da replica. Nulo reutiliza instance_class."
  type        = string
  default     = null
}

variable "enable_rds_proxy" {
  description = "RDS Proxy na frente da instancia. Padrao false. Nao exige replica."
  type        = bool
  default     = false
}

variable "monitoring_interval" {
  description = "Enhanced Monitoring em segundos. 0 desliga. Prod usa 60."
  type        = number
  default     = 0

  validation {
    condition     = contains([0, 1, 5, 10, 15, 30, 60], var.monitoring_interval)
    error_message = "monitoring_interval deve ser 0, 1, 5, 10, 15, 30 ou 60."
  }
}

variable "performance_insights_retention_days" {
  description = "Retencao do Performance Insights. 7 dias esta no free tier."
  type        = number
  default     = 7

  validation {
    condition     = contains([7, 31, 62, 93, 124, 155, 186, 217, 248, 279, 310, 341, 372, 403, 434, 465, 496, 527, 558, 589, 620, 651, 682, 713, 731], var.performance_insights_retention_days)
    error_message = "performance_insights_retention_days deve ser 7 ou um valor mensal aceito pela AWS."
  }
}

variable "log_min_duration_statement_ms" {
  description = "log_min_duration_statement em milissegundos. Consultas mais lentas vao para o log do PostgreSQL."
  type        = number
  default     = 500
}

variable "statement_timeout_ms" {
  description = "statement_timeout padrao do parameter group, em milissegundos."
  type        = number
  default     = 30000
}

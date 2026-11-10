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

variable "name" {
  description = "replication_group_id. Maximo de 40 caracteres, sem underscore."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,39}$", var.name))
    error_message = "name deve ter ate 40 caracteres, comecar com letra e usar apenas minusculas, numeros e hifen."
  }
}

variable "description" {
  description = "Descricao do replication group."
  type        = string
}

variable "engine_version" {
  description = "Versao do Valkey. 8.x evita a licenca do Redis e atende o contrato REDIS_SSL."
  type        = string
  default     = "8.2"
}

variable "node_type" {
  description = "Tipo do no. cache.t4g.micro em dev, cache.t4g.small em prod."
  type        = string
}

variable "num_cache_clusters" {
  description = "Primario mais replicas. 1 em dev, 2 em prod."
  type        = number
}

variable "automatic_failover_enabled" {
  description = "Promove a replica se o primario cair. Exige num_cache_clusters >= 2."
  type        = bool

  validation {
    condition     = !var.automatic_failover_enabled || var.num_cache_clusters >= 2
    error_message = "automatic_failover_enabled exige num_cache_clusters >= 2."
  }
}

variable "multi_az_enabled" {
  description = "Replica em outra AZ. Exige failover automatico."
  type        = bool

  validation {
    condition     = !var.multi_az_enabled || var.automatic_failover_enabled
    error_message = "multi_az_enabled exige automatic_failover_enabled."
  }
}

variable "snapshot_retention_limit" {
  description = "Dias de snapshot. 0 desliga."
  type        = number
  default     = 1
}

variable "apply_immediately" {
  description = "Aplica mudancas na hora."
  type        = bool
  default     = false
}

variable "kms_key_arn" {
  description = "CMK da criptografia em repouso."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets de dados. Pelo menos duas AZs, mesmo com um unico no."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC do security group."
  type        = string
}

variable "name_prefix" {
  description = "Prefixo dos recursos de rede. Ex.: fluxo-caixa-dev."
  type        = string
}

variable "region" {
  description = "Regiao dos VPC endpoints."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR da VPC. O desenho usa /24 derivados deste bloco."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Quantidade de AZs. Dev e hml usam 2. Prod usa 3."
  type        = number
  default     = 3

  validation {
    condition     = contains([2, 3], var.az_count)
    error_message = "az_count deve ser 2 (dev/hml) ou 3 (prod)."
  }
}

variable "nat_gateway_per_az" {
  description = "true cria um NAT por AZ (prod). false cria um unico NAT (dev)."
  type        = bool
}

variable "interface_endpoint_az_count" {
  description = "AZs com interface endpoint. 0 faz a task sair para as APIs AWS pelo NAT. Hml usa 2 e prod usa 3."
  type        = number

  validation {
    condition     = var.interface_endpoint_az_count >= 0 && var.interface_endpoint_az_count <= var.az_count
    error_message = "interface_endpoint_az_count deve estar entre 0 e az_count."
  }
}

variable "logs_kms_key_arn" {
  description = "CMK dos log groups, inclusive o de VPC flow logs."
  type        = string
}

variable "log_retention_days" {
  description = "Retencao dos flow logs."
  type        = number
}

variable "flow_log_traffic_type" {
  description = "Tipo de trafego do flow log. REJECT reduz custo; ALL e mais util em prod."
  type        = string
  default     = "REJECT"

  validation {
    condition     = contains(["ACCEPT", "REJECT", "ALL"], var.flow_log_traffic_type)
    error_message = "flow_log_traffic_type deve ser ACCEPT, REJECT ou ALL."
  }
}

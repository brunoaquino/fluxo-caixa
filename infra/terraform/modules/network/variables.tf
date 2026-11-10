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
  description = "Quantidade de AZs. O blueprint fixa 3."
  type        = number
  default     = 3

  validation {
    condition     = var.az_count == 3
    error_message = "A VPC do fluxo de caixa usa 3 AZs."
  }
}

variable "nat_gateway_per_az" {
  description = "true cria um NAT por AZ (prod). false cria um unico NAT (dev)."
  type        = bool
}

variable "interface_endpoint_az_count" {
  description = "Em quantas AZs os interface endpoints sao criados. Dev pode usar 1 para reduzir custo; prod deve usar 3."
  type        = number

  validation {
    condition     = var.interface_endpoint_az_count >= 1 && var.interface_endpoint_az_count <= var.az_count
    error_message = "interface_endpoint_az_count deve estar entre 1 e az_count."
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

variable "topic_name" {
  description = "Nome do topico SNS. No ambiente local o nome e fixo; na AWS leva o prefixo do ambiente."
  type        = string
}

variable "queue_name" {
  description = "Fila consumida pelo consolidado."
  type        = string
}

variable "dlq_name" {
  description = "DLQ da fila do consolidado."
  type        = string
}

variable "kms_key_arn" {
  description = "CMK de SNS e SQS."
  type        = string
}

variable "publisher_role_arn" {
  description = "Task role de lancamentos. ARN montado pelo caller para nao criar ciclo com o modulo ECS."
  type        = string
}

variable "consumer_role_arn" {
  description = "Task role de consolidado."
  type        = string
}

variable "max_receive_count" {
  description = "Tentativas antes da DLQ."
  type        = number
  default     = 5
}

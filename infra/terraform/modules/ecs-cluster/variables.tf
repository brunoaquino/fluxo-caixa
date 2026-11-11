variable "name" {
  description = "Nome do cluster ECS."
  type        = string
}

variable "container_insights" {
  description = "containerInsights do cluster: disabled em dev, enabled em hml, enhanced em prod."
  type        = string
  default     = "enhanced"

  validation {
    condition     = contains(["disabled", "enabled", "enhanced"], var.container_insights)
    error_message = "container_insights deve ser disabled, enabled ou enhanced."
  }
}

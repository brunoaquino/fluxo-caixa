output "service_name" {
  description = "Nome do servico ECS."
  value       = aws_ecs_service.this.name
}

output "service_arn" {
  description = "ARN do servico ECS."
  value       = aws_ecs_service.this.id
}

output "task_definition_arn" {
  description = "ARN da task definition gerenciada pelo Terraform."
  value       = aws_ecs_task_definition.this.arn
}

output "task_role_arn" {
  description = "Task role. O nome e previsivel para a policy de SNS/SQS."
  value       = aws_iam_role.task.arn
}

output "task_role_name" {
  description = "Nome da task role."
  value       = aws_iam_role.task.name
}

output "security_group_id" {
  description = "Security group das tasks."
  value       = aws_security_group.service.id
}

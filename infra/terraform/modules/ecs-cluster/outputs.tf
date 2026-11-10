output "cluster_id" {
  description = "ID do cluster."
  value       = aws_ecs_cluster.this.id
}

output "cluster_name" {
  description = "Nome do cluster."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ARN do cluster."
  value       = aws_ecs_cluster.this.arn
}

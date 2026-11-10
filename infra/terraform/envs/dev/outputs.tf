output "alb_url" {
  description = "URL HTTPS do ALB."
  value       = module.alb.alb_url
}

output "alb_dns_name" {
  description = "DNS do ALB, para um alias no Route 53."
  value       = module.alb.alb_dns_name
}

output "ecr_repository_urls" {
  description = "Repositorios ECR."
  value       = module.ecr.repository_urls
}

output "cognito_user_pool_id" {
  description = "User pool."
  value       = module.cognito.user_pool_id
}

output "cognito_user_pool_client_id" {
  description = "App client."
  value       = module.cognito.user_pool_client_id
}

output "cognito_issuer_uri" {
  description = "Issuer dos access tokens."
  value       = module.cognito.issuer_uri
}

output "lancamentos_topic_arn" {
  description = "Topico SNS dos eventos de lancamento."
  value       = module.messaging.topic_arn
}

output "consolidado_queue_url" {
  description = "Fila consumida pelo consolidado."
  value       = module.messaging.queue_url
}

output "consolidado_dlq_url" {
  description = "DLQ do consolidado."
  value       = module.messaging.dlq_url
}

output "ecs_cluster_name" {
  description = "Cluster ECS."
  value       = module.cluster.cluster_name
}

output "rds_endpoints" {
  description = "Hostnames dos bancos. As senhas ficam no Secrets Manager."
  value = {
    lancamentos = module.rds_lancamentos.address
    consolidado = module.rds_consolidado.address
  }
}

output "redis_endpoint" {
  description = "Endpoint de escrita do Valkey."
  value       = module.redis.primary_endpoint
}

output "alert_topic_arn" {
  description = "Topico dos alarmes."
  value       = module.observability.alert_topic_arn
}

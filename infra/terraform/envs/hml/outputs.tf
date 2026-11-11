output "cloudfront_domain_name" {
  description = "Entrada publica."
  value       = module.platform.cloudfront_domain_name
}

output "alb_dns_name" {
  description = "DNS interno do ALB."
  value       = module.platform.alb_dns_name
}

output "ecr_repository_urls" {
  description = "Repositorios ECR."
  value       = module.platform.ecr_repository_urls
}

output "cognito_user_pool_id" {
  description = "User pool."
  value       = module.platform.cognito_user_pool_id
}

output "cognito_user_pool_client_id" {
  description = "App client."
  value       = module.platform.cognito_user_pool_client_id
}

output "cognito_issuer_uri" {
  description = "Issuer dos access tokens."
  value       = module.platform.cognito_issuer_uri
}

output "lancamentos_topic_arn" {
  description = "Topico SNS."
  value       = module.platform.lancamentos_topic_arn
}

output "consolidado_queue_url" {
  description = "Fila do consolidado."
  value       = module.platform.consolidado_queue_url
}

output "consolidado_dlq_url" {
  description = "DLQ do consolidado."
  value       = module.platform.consolidado_dlq_url
}

output "ecs_cluster_name" {
  description = "Cluster ECS."
  value       = module.platform.ecs_cluster_name
}

output "rds_endpoints" {
  description = "Hosts de banco usados pela aplicacao."
  value       = module.platform.rds_endpoints
}

output "rds_secret_arns" {
  description = "Secrets referenciados por DB_SECRET_ARN."
  value       = module.platform.rds_secret_arns
}

output "redis_endpoint" {
  description = "Endpoint do Valkey."
  value       = module.platform.redis_endpoint
}

output "alert_topic_arn" {
  description = "Topico de alertas."
  value       = module.platform.alert_topic_arn
}

output "sizing" {
  description = "Dimensionamento efetivo."
  value       = module.platform.sizing
}

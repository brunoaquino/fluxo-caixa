output "environment" {
  description = "Ambiente deste state."
  value       = var.environment
}

output "cloudfront_domain_name" {
  description = "Entrada publica. Use este hostname enquanto nao houver dominio proprio."
  value       = module.edge.distribution_domain_name
}

output "cloudfront_distribution_id" {
  description = "ID da distribuicao CloudFront."
  value       = module.edge.distribution_id
}

output "alb_dns_name" {
  description = "DNS interno do ALB. Nao e a URL publica."
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

output "cognito_domain" {
  description = "Prefixo do dominio hospedado do Cognito."
  value       = module.cognito.domain
}

output "cognito_token_endpoint" {
  description = "Token endpoint OAuth do Cognito."
  value       = module.cognito.token_endpoint
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
  description = "Hosts que a aplicacao usa. Proxy quando enable_rds_proxy."
  value = {
    lancamentos = module.rds_lancamentos.endpoint
    consolidado = module.rds_consolidado.endpoint
  }
}

output "rds_reader_endpoints" {
  description = "Hosts de leitura. Nulos enquanto read_replica_count for 0."
  value = {
    lancamentos = module.rds_lancamentos.reader_endpoint
    consolidado = module.rds_consolidado.reader_endpoint
  }
}

output "rds_secret_arns" {
  description = "Secrets que a task le via DB_SECRET_ARN."
  value = {
    lancamentos = module.rds_lancamentos.master_user_secret_arn
    consolidado = module.rds_consolidado.master_user_secret_arn
  }
}

output "redis_endpoint" {
  description = "Endpoint de escrita do Valkey."
  value       = module.redis.primary_endpoint
}

output "api_gateway_invoke_url" {
  description = "URL regional do stage. A URL publica continua sendo o CloudFront."
  value       = var.enable_api_gateway ? module.api_gateway[0].invoke_url : null
}

output "api_gateway_id" {
  description = "ID da REST API. Nulo quando enable_api_gateway e false."
  value       = var.enable_api_gateway ? module.api_gateway[0].api_id : null
}

output "api_gateway_stage" {
  description = "Stage publicado."
  value       = var.enable_api_gateway ? module.api_gateway[0].stage_name : null
}

output "alert_topic_arn" {
  description = "Topico dos alarmes e das falhas de rotacao."
  value       = module.observability.alert_topic_arn
}

output "sizing" {
  description = "Dimensionamento efetivo, depois dos defaults da secao 15 e das sobrescritas."
  value       = local.cfg
}

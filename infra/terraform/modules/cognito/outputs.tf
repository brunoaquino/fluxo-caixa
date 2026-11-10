output "user_pool_id" {
  description = "ID do user pool."
  value       = aws_cognito_user_pool.this.id
}

output "user_pool_arn" {
  description = "ARN do user pool."
  value       = aws_cognito_user_pool.this.arn
}

output "user_pool_client_id" {
  description = "App client publico do comerciante."
  value       = aws_cognito_user_pool_client.app.id
}

output "issuer_uri" {
  description = "JWT_ISSUER_URI. O prefixo de escopo na AWS e fluxo-caixa/."
  value       = "https://cognito-idp.${local.region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
}

output "resource_server_identifier" {
  description = "Identificador do resource server."
  value       = aws_cognito_resource_server.fluxo_caixa.identifier
}

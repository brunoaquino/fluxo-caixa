output "api_id" {
  description = "ID da REST API."
  value       = aws_api_gateway_rest_api.this.id
}

output "api_name" {
  description = "Nome da REST API. Dimensao ApiName das metricas."
  value       = aws_api_gateway_rest_api.this.name
}

output "stage_name" {
  description = "Stage publicado. O CloudFront usa /<stage> como origin_path."
  value       = aws_api_gateway_stage.v1.stage_name
}

output "invoke_url" {
  description = "URL regional do stage. Nao e a URL publica. A publica e o CloudFront."
  value       = aws_api_gateway_stage.v1.invoke_url
}

output "regional_domain_name" {
  description = "Host execute-api usado como origem HTTPS do CloudFront."
  value       = "${aws_api_gateway_rest_api.this.id}.execute-api.${local.region}.amazonaws.com"
}

output "vpc_link_id" {
  description = "ID do VPC Link v2."
  value       = aws_apigatewayv2_vpc_link.this.id
}

output "waf_arn" {
  description = "ARN do Web ACL regional associado ao stage."
  value       = aws_wafv2_web_acl.regional.arn
}

output "origin_verify_secret_arn" {
  description = "ARN do segredo com current e previous do header X-Origin-Verify."
  value       = aws_secretsmanager_secret.origin.arn
}

output "origin_verify_header_value" {
  description = "Valor current lido de AWSCURRENT. Sensivel. O CloudFront envia este valor."
  value       = local.origin_verify.current
  sensitive   = true
}

output "origin_verify_header_values" {
  description = "Par vigente no segredo. Sensivel. O WAF aceita os dois. O data source grava este par no state."
  value = {
    current  = local.origin_verify.current
    previous = local.origin_verify.previous
  }
  sensitive = true
}

output "api_key_ids" {
  description = "IDs das API keys. O valor fica no state e no console. Nao e escrito em output plano."
  value       = { for name, key in aws_api_gateway_api_key.consumer : name => key.id }
}

output "method_validator_names" {
  description = "Validator de cada rota. parametros nao exige corpo."
  value       = { for key, method in local.methods : key => method.validator }
}

output "distribution_id" {
  description = "ID da distribuicao CloudFront."
  value       = aws_cloudfront_distribution.this.id
}

output "distribution_domain_name" {
  description = "Dominio *.cloudfront.net. E a URL publica quando nao ha alias."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "distribution_arn" {
  description = "ARN da distribuicao."
  value       = aws_cloudfront_distribution.this.arn
}

output "waf_arn" {
  description = "ARN do Web ACL de escopo CLOUDFRONT."
  value       = aws_wafv2_web_acl.this.arn
}

output "alb_arn" {
  description = "ARN do ALB."
  value       = aws_lb.this.arn
}

output "alb_arn_suffix" {
  description = "Sufixo usado em metricas e no scaling por request count."
  value       = aws_lb.this.arn_suffix
}

output "alb_dns_name" {
  description = "DNS publico do ALB."
  value       = aws_lb.this.dns_name
}

output "alb_zone_id" {
  description = "Zone ID do ALB, para um alias no Route 53."
  value       = aws_lb.this.zone_id
}

output "alb_url" {
  description = "URL HTTPS do ALB."
  value       = "https://${aws_lb.this.dns_name}"
}

output "security_group_id" {
  description = "Security group do ALB."
  value       = aws_security_group.alb.id
}

output "target_group_arns" {
  description = "ARNs dos target groups, indexados pelo servico."
  value       = { for name, tg in aws_lb_target_group.service : name => tg.arn }
}

output "target_group_arn_suffixes" {
  description = "Sufixos dos target groups."
  value       = { for name, tg in aws_lb_target_group.service : name => tg.arn_suffix }
}

output "waf_arn" {
  description = "ARN do Web ACL."
  value       = aws_wafv2_web_acl.this.arn
}

output "access_logs_bucket" {
  description = "Bucket dos access logs."
  value       = aws_s3_bucket.logs.id
}

output "log_group_names" {
  description = "Nomes dos log groups, incluindo metrics."
  value       = { for name, group in aws_cloudwatch_log_group.service : name => group.name }
}

output "log_group_arns" {
  description = "ARNs dos log groups."
  value       = { for name, group in aws_cloudwatch_log_group.service : name => group.arn }
}

output "alert_topic_arn" {
  description = "Topico SNS dos alarmes."
  value       = aws_sns_topic.alerts.arn
}

output "dashboard_name" {
  description = "Nome do dashboard operacional."
  value       = aws_cloudwatch_dashboard.operacao.dashboard_name
}

output "topic_arn" {
  description = "ARN do topico de lancamentos. LANCAMENTOS_TOPIC_ARN."
  value       = aws_sns_topic.lancamentos.arn
}

output "topic_name" {
  description = "Nome do topico."
  value       = aws_sns_topic.lancamentos.name
}

output "queue_url" {
  description = "URL da fila do consolidado. CONSOLIDADO_QUEUE_URL."
  value       = aws_sqs_queue.consolidado.url
}

output "queue_arn" {
  description = "ARN da fila do consolidado."
  value       = aws_sqs_queue.consolidado.arn
}

output "queue_name" {
  description = "Nome da fila, usado nos alarmes e no scaling."
  value       = aws_sqs_queue.consolidado.name
}

output "dlq_arn" {
  description = "ARN da DLQ."
  value       = aws_sqs_queue.dlq.arn
}

output "dlq_name" {
  description = "Nome da DLQ."
  value       = aws_sqs_queue.dlq.name
}

output "dlq_url" {
  description = "URL da DLQ."
  value       = aws_sqs_queue.dlq.url
}

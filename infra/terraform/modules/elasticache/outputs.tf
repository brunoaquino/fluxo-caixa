output "primary_endpoint" {
  description = "Endpoint de escrita. REDIS_HOST."
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "port" {
  description = "Porta. Com TLS continua 6379."
  value       = aws_elasticache_replication_group.this.port
}

output "security_group_id" {
  description = "Security group do Redis. O consolidado abre o ingress."
  value       = aws_security_group.this.id
}

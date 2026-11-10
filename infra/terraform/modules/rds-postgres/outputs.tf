output "address" {
  description = "Hostname do RDS."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Porta PostgreSQL."
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "Nome do database."
  value       = aws_db_instance.this.db_name
}

output "master_user_secret_arn" {
  description = "Secret gerenciado pelo RDS com username e password."
  value       = one(aws_db_instance.this.master_user_secret).secret_arn
}

output "security_group_id" {
  description = "Security group do banco. O servico abre o ingress 5432."
  value       = aws_security_group.this.id
}

output "identifier" {
  description = "Identificador da instancia, usado nos alarmes."
  value       = aws_db_instance.this.identifier
}

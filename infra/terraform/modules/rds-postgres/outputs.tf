output "address" {
  description = "Hostname da instancia primaria."
  value       = aws_db_instance.this.address
}

output "endpoint" {
  description = "Host que a aplicacao usa em DB_URL. O proxy, quando enable_rds_proxy; senao a instancia."
  value       = try(aws_db_proxy.this[0].endpoint, aws_db_instance.this.address)
}

output "reader_endpoint" {
  description = "Host de DB_READER_URL. Nulo sem replica."
  value       = try(aws_db_proxy_endpoint.reader[0].endpoint, try(aws_db_instance.replica["0"].address, null))
}

output "client_security_group_id" {
  description = "Security group que a task deve alcancar. O do proxy, se existir; senao o do banco."
  value       = try(aws_security_group.proxy[0].id, aws_security_group.this.id)
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

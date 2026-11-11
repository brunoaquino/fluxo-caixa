output "vpc_id" {
  description = "ID da VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "CIDR da VPC."
  value       = aws_vpc.this.cidr_block
}

output "availability_zones" {
  description = "AZs usadas, na ordem estavel do slice."
  value       = local.azs
}

output "public_subnet_ids" {
  description = "Subnets publicas (ALB e NAT), uma por AZ."
  value       = [for az in local.azs : aws_subnet.public[az].id]
}

output "app_subnet_ids" {
  description = "Subnets privadas de aplicacao (ECS)."
  value       = [for az in local.azs : aws_subnet.app[az].id]
}

output "data_subnet_ids" {
  description = "Subnets privadas de dados, sem rota para a internet."
  value       = [for az in local.azs : aws_subnet.data[az].id]
}

output "endpoint_security_group_id" {
  description = "Security group dos interface endpoints. Nulo quando interface_endpoint_az_count e 0."
  value       = one(aws_security_group.endpoints[*].id)
}

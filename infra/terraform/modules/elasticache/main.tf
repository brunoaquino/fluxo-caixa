resource "aws_elasticache_subnet_group" "this" {
  name        = var.name
  description = "Subnets de dados do ${var.name}."
  subnet_ids  = var.subnet_ids

  tags = {
    Name = var.name
  }
}

resource "aws_security_group" "this" {
  name        = "${var.name}-redis"
  description = "Redis/Valkey somente a partir do consolidado-service."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name}-redis"
  }
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = var.name
  description          = var.description

  engine         = "valkey"
  engine_version = var.engine_version
  node_type      = var.node_type
  port           = 6379

  num_cache_clusters         = var.num_cache_clusters
  automatic_failover_enabled = var.automatic_failover_enabled
  multi_az_enabled           = var.multi_az_enabled

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.this.id]

  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"

  # Sem AUTH token: o contrato do servico so tem REDIS_HOST, REDIS_PORT e REDIS_SSL.
  # O isolamento fica no security group, que so aceita o consolidado.

  snapshot_retention_limit   = var.snapshot_retention_limit
  snapshot_window            = "03:00-04:00"
  maintenance_window         = "sun:04:30-sun:05:30"
  auto_minor_version_upgrade = true
  apply_immediately          = var.apply_immediately

  tags = {
    Name = var.name
  }
}

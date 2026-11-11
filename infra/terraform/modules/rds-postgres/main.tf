resource "aws_db_subnet_group" "this" {
  name        = var.identifier
  description = "Subnets de dados do ${var.identifier}."
  subnet_ids  = var.subnet_ids

  tags = {
    Name = var.identifier
  }
}

resource "aws_security_group" "this" {
  name        = "${var.identifier}-db"
  description = "PostgreSQL somente a partir do servico dono deste banco."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.identifier}-db"
  }
}

resource "aws_db_parameter_group" "this" {
  name        = "${var.identifier}-pg"
  family      = "postgres17"
  description = "SSL obrigatorio, pg_stat_statements e timeout no ${var.identifier}."

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "pg_stat_statements.track"
    value        = "all"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_min_duration_statement"
    value        = tostring(var.log_min_duration_statement_ms)
    apply_method = "immediate"
  }

  parameter {
    name         = "statement_timeout"
    value        = tostring(var.statement_timeout_ms)
    apply_method = "immediate"
  }
}

resource "aws_db_instance" "this" {
  identifier = var.identifier

  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = var.db_name
  username = var.username
  port     = 5432

  # A senha nao entra no Terraform. O RDS grava o JSON {username,password} no Secrets Manager
  # e rotaciona o secret. A agenda fica em rotation.tf.
  manage_master_user_password   = var.manage_master_user_password
  master_user_secret_kms_key_id = var.secret_kms_key_arn

  lifecycle {
    precondition {
      condition     = var.read_replica_count == 0 || !var.manage_master_user_password
      error_message = "A AWS nao cria read replica de RDS for PostgreSQL quando manage_master_user_password esta ligado (https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html). Mantenha read_replica_count = 0. Alternativas compativeis com senha gerenciada: RDS Multi-AZ DB Cluster (2 standbys legiveis e reader endpoint) ou Aurora PostgreSQL com readers. A terceira saida e desligar a senha gerenciada e rotacionar por Lambda. No gatilho de leitura, a recomendacao e o Multi-AZ DB Cluster. Ver ADR 0030."
    }
  }

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  multi_az            = var.multi_az
  publicly_accessible = false
  deletion_protection = var.deletion_protection

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  parameter_group_name   = aws_db_parameter_group.this.name

  backup_retention_period   = var.backup_retention_days
  backup_window             = "03:00-04:00"
  maintenance_window        = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot     = true
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.identifier}-final"

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = var.kms_key_arn
  performance_insights_retention_period = var.performance_insights_retention_days
  monitoring_interval                   = var.monitoring_interval
  monitoring_role_arn                   = var.monitoring_interval > 0 ? aws_iam_role.monitoring[0].arn : null

  iam_database_authentication_enabled = true
  auto_minor_version_upgrade          = true
  apply_immediately                   = var.apply_immediately
  enabled_cloudwatch_logs_exports     = ["postgresql", "upgrade"]

  tags = {
    Name = var.identifier
  }
}

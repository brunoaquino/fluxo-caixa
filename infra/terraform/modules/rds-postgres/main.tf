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
  description = "Forca SSL no ${var.identifier}."

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
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

  # A senha nao entra no Terraform. O RDS grava o JSON {username,password} no Secrets Manager.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

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
  performance_insights_retention_period = 7

  iam_database_authentication_enabled = true
  auto_minor_version_upgrade          = true
  apply_immediately                   = var.apply_immediately
  enabled_cloudwatch_logs_exports     = ["postgresql", "upgrade"]

  tags = {
    Name = var.identifier
  }
}

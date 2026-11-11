# A precondition em aws_db_instance.this recusa read_replica_count > 0 enquanto
# manage_master_user_password for true. O recurso fica declarado para o dia em que
# a AWS liberar a combinacao no PostgreSQL: DB_READER_URL ja sai do output reader_endpoint.
resource "aws_db_instance" "replica" {
  for_each = toset([for index in range(var.read_replica_count) : tostring(index)])

  identifier          = "${var.identifier}-read-${each.key}"
  replicate_source_db = aws_db_instance.this.identifier
  instance_class      = coalesce(var.replica_instance_class, var.instance_class)

  storage_encrypted   = true
  kms_key_id          = var.kms_key_arn
  publicly_accessible = false
  deletion_protection = var.deletion_protection
  skip_final_snapshot = true
  apply_immediately   = var.apply_immediately

  parameter_group_name = aws_db_parameter_group.this.name

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = var.kms_key_arn
  performance_insights_retention_period = var.performance_insights_retention_days
  monitoring_interval                   = var.monitoring_interval
  monitoring_role_arn                   = var.monitoring_interval > 0 ? aws_iam_role.monitoring[0].arn : null

  auto_minor_version_upgrade      = true
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  copy_tags_to_snapshot           = true

  tags = {
    Name = "${var.identifier}-read-${each.key}"
  }
}

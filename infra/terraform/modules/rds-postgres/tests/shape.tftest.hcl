mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_data "aws_region" {
    defaults = {
      name   = "us-east-1"
      region = "us-east-1"
    }
  }
}

variables {
  identifier            = "fluxo-caixa-prod-lancamentos"
  db_name               = "lancamentos"
  username              = "lancamentos"
  instance_class        = "db.t4g.medium"
  allocated_storage     = 50
  max_allocated_storage = 200
  multi_az              = true
  deletion_protection   = true
  skip_final_snapshot   = false
  backup_retention_days = 14
  kms_key_arn           = "arn:aws:kms:us-east-1:123456789012:key/rds"
  secret_kms_key_arn    = "arn:aws:kms:us-east-1:123456789012:key/rds-secret"
  subnet_ids            = ["subnet-a", "subnet-b", "subnet-c"]
  vpc_id                = "vpc-prod"
  monitoring_interval   = 60
}

run "prod_multi_az_ssl_e_rotacao" {
  command = plan

  assert {
    condition     = aws_db_instance.this.multi_az == true
    error_message = "Prod exige RDS Multi-AZ."
  }

  assert {
    condition = alltrue([
      for parameter in aws_db_parameter_group.this.parameter :
      parameter.name != "rds.force_ssl" || parameter.value == "1"
      ]) && anytrue([
      for parameter in aws_db_parameter_group.this.parameter :
      parameter.name == "rds.force_ssl"
    ])
    error_message = "O parameter group tem de forcar rds.force_ssl = 1."
  }

  assert {
    condition = anytrue([
      for parameter in aws_db_parameter_group.this.parameter :
      parameter.name == "shared_preload_libraries" && parameter.value == "pg_stat_statements"
      ]) && anytrue([
      for parameter in aws_db_parameter_group.this.parameter :
      parameter.name == "log_min_duration_statement"
    ])
    error_message = "O parameter group precisa de pg_stat_statements e log_min_duration_statement."
  }

  assert {
    condition     = aws_db_instance.this.monitoring_interval == 60 && aws_db_instance.this.performance_insights_enabled == true
    error_message = "Prod liga Enhanced Monitoring e Performance Insights."
  }

  assert {
    condition     = aws_secretsmanager_secret_rotation.master.rotation_rules[0].automatically_after_days == 30
    error_message = "A rotacao do secret mestre tem de respeitar master_password_rotation_days."
  }

  assert {
    condition     = length(aws_db_proxy.this) == 0 && length(aws_db_instance.replica) == 0
    error_message = "RDS Proxy e replica ficam desligados no default."
  }
}

run "replica_com_senha_gerenciada_falha" {
  command = plan

  variables {
    read_replica_count = 1
  }

  expect_failures = [
    var.read_replica_count,
  ]
}

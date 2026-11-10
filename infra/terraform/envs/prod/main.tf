data "aws_caller_identity" "current" {}

locals {
  name_prefix = "fluxo-caixa-${var.environment}"

  # ARNs montados antes das roles existirem. O modulo ECS cria as roles com estes nomes,
  # e o modulo de mensageria restringe publish/consume a eles sem ciclo.
  lancamentos_task_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${local.name_prefix}-lancamentos-task"
  consolidado_task_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${local.name_prefix}-consolidado-task"

  lancamentos_service_name = "${local.name_prefix}-lancamentos"
  consolidado_service_name = "${local.name_prefix}-consolidado"
}

module "kms" {
  source = "../../modules/security/kms"

  name_prefix          = local.name_prefix
  region               = var.aws_region
  deletion_window_days = var.kms_deletion_window_days
}

module "network" {
  source = "../../modules/network"

  name_prefix                 = local.name_prefix
  region                      = var.aws_region
  vpc_cidr                    = var.vpc_cidr
  nat_gateway_per_az          = var.nat_gateway_per_az
  interface_endpoint_az_count = var.interface_endpoint_az_count
  logs_kms_key_arn            = module.kms.key_arns["logs"]
  log_retention_days          = var.log_retention_days
  flow_log_traffic_type       = var.flow_log_traffic_type
}

module "ecr" {
  source = "../../modules/ecr"

  name_prefix      = local.name_prefix
  repository_names = ["lancamentos", "consolidado"]
  kms_key_arn      = module.kms.key_arns["ecr"]
}

module "cluster" {
  source = "../../modules/ecs-cluster"

  name = "${local.name_prefix}-cluster"
}

module "messaging" {
  source = "../../modules/messaging"

  topic_name         = var.topic_name
  queue_name         = var.queue_name
  dlq_name           = var.dlq_name
  kms_key_arn        = module.kms.key_arns["messaging"]
  publisher_role_arn = local.lancamentos_task_role_arn
  consumer_role_arn  = local.consolidado_task_role_arn
}

module "rds_lancamentos" {
  source = "../../modules/rds-postgres"

  identifier            = "${local.name_prefix}-lancamentos"
  db_name               = "lancamentos"
  username              = "lancamentos"
  engine_version        = var.rds_engine_version
  instance_class        = var.rds_instance_class
  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  multi_az              = var.rds_multi_az
  deletion_protection   = var.rds_deletion_protection
  skip_final_snapshot   = var.rds_skip_final_snapshot
  backup_retention_days = var.rds_backup_retention_days
  apply_immediately     = var.rds_apply_immediately
  kms_key_arn           = module.kms.key_arns["rds-lancamentos"]
  subnet_ids            = module.network.data_subnet_ids
  vpc_id                = module.network.vpc_id
}

module "rds_consolidado" {
  source = "../../modules/rds-postgres"

  identifier            = "${local.name_prefix}-consolidado"
  db_name               = "consolidado"
  username              = "consolidado"
  engine_version        = var.rds_engine_version
  instance_class        = var.rds_instance_class
  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  multi_az              = var.rds_multi_az
  deletion_protection   = var.rds_deletion_protection
  skip_final_snapshot   = var.rds_skip_final_snapshot
  backup_retention_days = var.rds_backup_retention_days
  apply_immediately     = var.rds_apply_immediately
  kms_key_arn           = module.kms.key_arns["rds-consolidado"]
  subnet_ids            = module.network.data_subnet_ids
  vpc_id                = module.network.vpc_id
}

module "redis" {
  source = "../../modules/elasticache"

  name                       = "${local.name_prefix}-redis"
  description                = "Cache do saldo consolidado."
  engine_version             = var.redis_engine_version
  node_type                  = var.redis_node_type
  num_cache_clusters         = var.redis_num_cache_clusters
  automatic_failover_enabled = var.redis_automatic_failover
  multi_az_enabled           = var.redis_multi_az
  snapshot_retention_limit   = var.redis_snapshot_retention
  apply_immediately          = var.rds_apply_immediately
  kms_key_arn                = module.kms.key_arns["elasticache"]
  subnet_ids                 = module.network.data_subnet_ids
  vpc_id                     = module.network.vpc_id
}

module "cognito" {
  source = "../../modules/cognito"

  name_prefix         = local.name_prefix
  deletion_protection = var.cognito_deletion_protection
  log_retention_days  = var.log_retention_days
  logs_kms_key_arn    = module.kms.key_arns["logs"]
}

module "alb" {
  source = "../../modules/alb"

  name_prefix             = local.name_prefix
  vpc_id                  = module.network.vpc_id
  public_subnet_ids       = module.network.public_subnet_ids
  certificate_arn         = var.certificate_arn
  deletion_protection     = var.alb_deletion_protection
  access_logs_bucket_name = var.access_logs_bucket_name
  logs_kms_key_arn        = module.kms.key_arns["alb-logs"]
  waf_rate_limit          = var.waf_rate_limit

  services = {
    lancamentos = {
      port          = 8081
      path_patterns = ["/api/v1/lancamentos*"]
      health_path   = "/actuator/health/readiness"
      priority      = 10
    }
    consolidado = {
      port          = 8082
      path_patterns = ["/api/v1/consolidados*"]
      health_path   = "/actuator/health/readiness"
      priority      = 20
    }
  }
}

module "observability" {
  source = "../../modules/observability"

  name_prefix        = local.name_prefix
  region             = var.aws_region
  log_retention_days = var.log_retention_days
  logs_kms_key_arn   = module.kms.key_arns["logs"]
  alerts_kms_key_arn = module.kms.key_arns["messaging"]
  alert_email        = var.alert_email
  cluster_name       = module.cluster.cluster_name
  alb_arn_suffix     = module.alb.alb_arn_suffix
  queue_name         = module.messaging.queue_name
  dlq_name           = module.messaging.dlq_name

  services = {
    lancamentos = {
      ecs_service_name        = local.lancamentos_service_name
      target_group_arn_suffix = module.alb.target_group_arn_suffixes["lancamentos"]
      latency_p99_seconds     = 0.5
    }
    consolidado = {
      ecs_service_name        = local.consolidado_service_name
      target_group_arn_suffix = module.alb.target_group_arn_suffixes["consolidado"]
      latency_p99_seconds     = 0.3
    }
  }

  rds_instances = {
    lancamentos = {
      identifier             = module.rds_lancamentos.identifier
      connection_threshold   = var.rds_connection_threshold
      free_storage_threshold = var.rds_free_storage_threshold
    }
    consolidado = {
      identifier             = module.rds_consolidado.identifier
      connection_threshold   = var.rds_connection_threshold
      free_storage_threshold = var.rds_free_storage_threshold
    }
  }
}

module "ecs_lancamentos" {
  source = "../../modules/ecs-service"

  name            = local.lancamentos_service_name
  container_name  = "lancamentos"
  cluster_arn     = module.cluster.cluster_arn
  cluster_name    = module.cluster.cluster_name
  vpc_id          = module.network.vpc_id
  vpc_cidr        = module.network.vpc_cidr
  subnet_ids      = module.network.app_subnet_ids
  container_image = "${module.ecr.repository_urls["lancamentos"]}:${var.image_tag}"
  container_port  = 8081
  cpu             = var.lancamentos_cpu
  memory          = var.lancamentos_memory
  desired_count   = var.lancamentos_desired_count
  min_capacity    = var.lancamentos_min_capacity
  max_capacity    = var.lancamentos_max_capacity
  adot_image      = var.adot_image

  environment = {
    SERVER_PORT                 = "8081"
    DB_URL                      = "jdbc:postgresql://${module.rds_lancamentos.address}:${module.rds_lancamentos.port}/${module.rds_lancamentos.db_name}?sslmode=require"
    AWS_REGION                  = var.aws_region
    JWT_ISSUER_URI              = module.cognito.issuer_uri
    APP_SECURITY_SCOPE_PREFIX   = "fluxo-caixa/"
    OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4318"
    SPRING_PROFILES_ACTIVE      = "aws"
    LANCAMENTOS_TOPIC_ARN       = module.messaging.topic_arn
  }

  secrets = [
    {
      name      = "DB_USERNAME"
      valueFrom = "${module.rds_lancamentos.master_user_secret_arn}:username::"
    },
    {
      name      = "DB_PASSWORD"
      valueFrom = "${module.rds_lancamentos.master_user_secret_arn}:password::"
    },
  ]

  secret_arns = [module.rds_lancamentos.master_user_secret_arn]
  execution_kms_key_arns = [
    module.kms.key_arns["rds-lancamentos"],
    module.kms.key_arns["logs"],
  ]

  task_policy_statements = [
    {
      sid       = "PublicarEventos"
      actions   = ["sns:Publish"]
      resources = [module.messaging.topic_arn]
    },
    {
      sid = "CifrarEventos"
      actions = [
        "kms:Decrypt",
        "kms:GenerateDataKey",
        "kms:DescribeKey",
      ]
      resources = [module.kms.key_arns["messaging"]]
    },
  ]

  log_group_name                 = module.observability.log_group_names["lancamentos"]
  log_group_arn                  = module.observability.log_group_arns["lancamentos"]
  metrics_log_group_name         = module.observability.log_group_names["metrics"]
  metrics_log_group_arn          = module.observability.log_group_arns["metrics"]
  aws_region                     = var.aws_region
  target_group_arn               = module.alb.target_group_arns["lancamentos"]
  alb_security_group_id          = module.alb.security_group_id
  vpc_endpoint_security_group_id = module.network.endpoint_security_group_id
  enable_execute_command         = var.enable_execute_command
  enable_request_count_scaling   = true
  request_count_target           = var.lancamentos_request_count_target
  alb_resource_label             = "${module.alb.alb_arn_suffix}/${module.alb.target_group_arn_suffixes["lancamentos"]}"

  data_plane_access = [
    {
      security_group_id = module.rds_lancamentos.security_group_id
      port              = 5432
      description       = "postgres"
    },
  ]
}

module "ecs_consolidado" {
  source = "../../modules/ecs-service"

  name            = local.consolidado_service_name
  container_name  = "consolidado"
  cluster_arn     = module.cluster.cluster_arn
  cluster_name    = module.cluster.cluster_name
  vpc_id          = module.network.vpc_id
  vpc_cidr        = module.network.vpc_cidr
  subnet_ids      = module.network.app_subnet_ids
  container_image = "${module.ecr.repository_urls["consolidado"]}:${var.image_tag}"
  container_port  = 8082
  cpu             = var.consolidado_cpu
  memory          = var.consolidado_memory
  desired_count   = var.consolidado_desired_count
  min_capacity    = var.consolidado_min_capacity
  max_capacity    = var.consolidado_max_capacity
  adot_image      = var.adot_image

  environment = {
    SERVER_PORT                 = "8082"
    DB_URL                      = "jdbc:postgresql://${module.rds_consolidado.address}:${module.rds_consolidado.port}/${module.rds_consolidado.db_name}?sslmode=require"
    AWS_REGION                  = var.aws_region
    JWT_ISSUER_URI              = module.cognito.issuer_uri
    APP_SECURITY_SCOPE_PREFIX   = "fluxo-caixa/"
    OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4318"
    SPRING_PROFILES_ACTIVE      = "aws"
    CONSOLIDADO_QUEUE_URL       = module.messaging.queue_url
    REDIS_HOST                  = module.redis.primary_endpoint
    REDIS_PORT                  = tostring(module.redis.port)
    REDIS_SSL                   = "true"
  }

  secrets = [
    {
      name      = "DB_USERNAME"
      valueFrom = "${module.rds_consolidado.master_user_secret_arn}:username::"
    },
    {
      name      = "DB_PASSWORD"
      valueFrom = "${module.rds_consolidado.master_user_secret_arn}:password::"
    },
  ]

  secret_arns = [module.rds_consolidado.master_user_secret_arn]
  execution_kms_key_arns = [
    module.kms.key_arns["rds-consolidado"],
    module.kms.key_arns["logs"],
  ]

  task_policy_statements = [
    {
      sid = "ConsumirFila"
      actions = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl",
      ]
      resources = [module.messaging.queue_arn]
    },
    {
      sid = "DecifrarFila"
      actions = [
        "kms:Decrypt",
        "kms:DescribeKey",
      ]
      resources = [module.kms.key_arns["messaging"]]
    },
  ]

  log_group_name                 = module.observability.log_group_names["consolidado"]
  log_group_arn                  = module.observability.log_group_arns["consolidado"]
  metrics_log_group_name         = module.observability.log_group_names["metrics"]
  metrics_log_group_arn          = module.observability.log_group_arns["metrics"]
  aws_region                     = var.aws_region
  target_group_arn               = module.alb.target_group_arns["consolidado"]
  alb_security_group_id          = module.alb.security_group_id
  vpc_endpoint_security_group_id = module.network.endpoint_security_group_id
  enable_execute_command         = var.enable_execute_command
  enable_request_count_scaling   = true
  request_count_target           = var.consolidado_request_count_target
  alb_resource_label             = "${module.alb.alb_arn_suffix}/${module.alb.target_group_arn_suffixes["consolidado"]}"
  queue_name                     = module.messaging.queue_name
  queue_backlog_per_task         = var.consolidado_queue_backlog_per_task

  data_plane_access = [
    {
      security_group_id = module.rds_consolidado.security_group_id
      port              = 5432
      description       = "postgres"
    },
    {
      security_group_id = module.redis.security_group_id
      port              = 6379
      description       = "redis"
    },
  ]
}

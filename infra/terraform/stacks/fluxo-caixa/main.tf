module "kms" {
  source = "../../modules/security/kms"

  name_prefix          = local.name_prefix
  region               = var.aws_region
  deletion_window_days = local.cfg.kms_deletion_window_days
}

module "network" {
  source = "../../modules/network"

  name_prefix                 = local.name_prefix
  region                      = var.aws_region
  vpc_cidr                    = local.cfg.vpc_cidr
  az_count                    = local.cfg.az_count
  nat_gateway_per_az          = local.cfg.nat_gateway_per_az
  interface_endpoint_az_count = local.cfg.interface_endpoint_az_count
  logs_kms_key_arn            = module.kms.key_arns["logs"]
  log_retention_days          = local.cfg.log_retention_days
  flow_log_traffic_type       = local.cfg.flow_log_traffic_type
}

module "ecr" {
  source = "../../modules/ecr"

  name_prefix      = local.name_prefix
  repository_names = ["lancamentos", "consolidado"]
  kms_key_arn      = module.kms.key_arns["ecr"]
}

module "cluster" {
  source = "../../modules/ecs-cluster"

  name               = "${local.name_prefix}-cluster"
  container_insights = local.cfg.container_insights
}

module "messaging" {
  source = "../../modules/messaging"

  topic_name         = "${local.name_prefix}-lancamentos-events"
  queue_name         = "${local.name_prefix}-consolidado-lancamentos-queue"
  dlq_name           = "${local.name_prefix}-consolidado-lancamentos-dlq"
  kms_key_arn        = module.kms.key_arns["messaging"]
  publisher_role_arn = local.lancamentos_task_role_arn
  consumer_role_arn  = local.consolidado_task_role_arn
}

module "rds_lancamentos" {
  source = "../../modules/rds-postgres"

  identifier                    = "${local.name_prefix}-lancamentos"
  db_name                       = "lancamentos"
  username                      = "lancamentos"
  engine_version                = var.rds_engine_version
  instance_class                = local.cfg.rds_instance_class
  allocated_storage             = local.cfg.rds_allocated_storage
  max_allocated_storage         = local.cfg.rds_max_allocated_storage
  multi_az                      = local.cfg.rds_multi_az
  deletion_protection           = local.cfg.rds_deletion_protection
  skip_final_snapshot           = local.cfg.rds_skip_final_snapshot
  backup_retention_days         = local.cfg.rds_backup_retention_days
  apply_immediately             = local.cfg.rds_apply_immediately
  kms_key_arn                   = module.kms.key_arns["rds-lancamentos"]
  secret_kms_key_arn            = module.kms.key_arns["rds-secret-lancamentos"]
  master_password_rotation_days = local.cfg.rds_rotation_days
  read_replica_count            = local.cfg.read_replica_count
  enable_rds_proxy              = local.cfg.enable_rds_proxy
  monitoring_interval           = local.cfg.rds_monitoring_interval
  subnet_ids                    = module.network.data_subnet_ids
  vpc_id                        = module.network.vpc_id
}

module "rds_consolidado" {
  source = "../../modules/rds-postgres"

  identifier                    = "${local.name_prefix}-consolidado"
  db_name                       = "consolidado"
  username                      = "consolidado"
  engine_version                = var.rds_engine_version
  instance_class                = local.cfg.rds_instance_class
  allocated_storage             = local.cfg.rds_allocated_storage
  max_allocated_storage         = local.cfg.rds_max_allocated_storage
  multi_az                      = local.cfg.rds_multi_az
  deletion_protection           = local.cfg.rds_deletion_protection
  skip_final_snapshot           = local.cfg.rds_skip_final_snapshot
  backup_retention_days         = local.cfg.rds_backup_retention_days
  apply_immediately             = local.cfg.rds_apply_immediately
  kms_key_arn                   = module.kms.key_arns["rds-consolidado"]
  secret_kms_key_arn            = module.kms.key_arns["rds-secret-consolidado"]
  master_password_rotation_days = local.cfg.rds_rotation_days
  read_replica_count            = local.cfg.read_replica_count
  enable_rds_proxy              = local.cfg.enable_rds_proxy
  monitoring_interval           = local.cfg.rds_monitoring_interval
  subnet_ids                    = module.network.data_subnet_ids
  vpc_id                        = module.network.vpc_id
}

module "redis" {
  source = "../../modules/elasticache"

  name                       = "${local.name_prefix}-redis"
  description                = "Cache do saldo consolidado."
  engine_version             = var.redis_engine_version
  node_type                  = local.cfg.redis_node_type
  num_cache_clusters         = local.cfg.redis_num_cache_clusters
  automatic_failover_enabled = local.cfg.redis_automatic_failover
  multi_az_enabled           = local.cfg.redis_multi_az
  snapshot_retention_limit   = local.cfg.redis_snapshot_retention
  apply_immediately          = local.cfg.rds_apply_immediately
  kms_key_arn                = module.kms.key_arns["elasticache"]
  subnet_ids                 = module.network.data_subnet_ids
  vpc_id                     = module.network.vpc_id
}

module "cognito" {
  source = "../../modules/cognito"

  name_prefix         = local.name_prefix
  deletion_protection = local.cfg.cognito_deletion_protection
  log_retention_days  = local.cfg.log_retention_days
  logs_kms_key_arn    = module.kms.key_arns["logs"]
}

module "alb" {
  source = "../../modules/alb"

  name_prefix             = local.name_prefix
  vpc_id                  = module.network.vpc_id
  subnet_ids              = module.network.app_subnet_ids
  deletion_protection     = local.cfg.alb_deletion_protection
  access_logs_bucket_name = local.access_logs_bucket_name
  logs_kms_key_arn        = module.kms.key_arns["alb-logs"]
  enable_waf              = false

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

  stage_path_prefix = var.enable_api_gateway ? "v1" : ""
}

module "api_gateway" {
  count  = var.enable_api_gateway ? 1 : 0
  source = "../../modules/api-gateway"

  name_prefix                    = local.name_prefix
  vpc_id                         = module.network.vpc_id
  subnet_ids                     = module.network.app_subnet_ids
  alb_arn                        = module.alb.alb_arn
  alb_dns_name                   = module.alb.alb_dns_name
  alb_security_group_id          = module.alb.security_group_id
  cognito_user_pool_arn          = module.cognito.user_pool_arn
  logs_kms_key_arn               = module.kms.key_arns["logs"]
  secret_kms_key_arn             = module.kms.key_arns["origin-verify"]
  log_retention_days             = local.cfg.log_retention_days
  cache_enabled                  = var.api_cache_enabled
  api_key_required               = var.api_key_required
  api_consumers                  = var.api_consumers
  canary                         = var.api_canary
  rotation_days                  = local.cfg.origin_verify_rotation_days
  require_consolidado_above_peak = var.environment != "dev"

  stage_throttling = {
    rate_limit  = local.cfg.api_stage_rate_limit
    burst_limit = local.cfg.api_stage_burst_limit
  }

  route_throttling = {
    consolidado_read = {
      rate_limit  = local.cfg.api_consolidado_rate_limit
      burst_limit = local.cfg.api_consolidado_burst_limit
    }
    lancamentos_write = {
      rate_limit  = local.cfg.api_lancamentos_write_rate_limit
      burst_limit = local.cfg.api_lancamentos_write_burst_limit
    }
    lancamentos_read = {
      rate_limit  = local.cfg.api_lancamentos_read_rate_limit
      burst_limit = local.cfg.api_lancamentos_read_burst_limit
    }
  }
}

module "edge" {
  source = "../../modules/edge"

  providers = {
    aws = aws.us_east_1
  }

  name_prefix                = local.name_prefix
  alb_arn                    = module.alb.alb_arn
  alb_dns_name               = module.alb.alb_dns_name
  acm_certificate_arn        = var.acm_certificate_arn
  domain_names               = var.domain_names
  logs_kms_key_arn           = module.kms.key_arns["logs"]
  log_retention_days         = local.cfg.log_retention_days
  waf_rate_limit             = local.cfg.waf_rate_limit
  anonymous_ip_count_only    = local.cfg.anonymous_ip_count_only
  enable_api_gateway         = var.enable_api_gateway
  api_gateway_domain_name    = var.enable_api_gateway ? module.api_gateway[0].regional_domain_name : null
  api_gateway_origin_path    = var.enable_api_gateway ? "/${module.api_gateway[0].stage_name}" : null
  origin_verify_header_value = var.enable_api_gateway ? module.api_gateway[0].origin_verify_header_value : null
}

module "observability" {
  source = "../../modules/observability"

  name_prefix        = local.name_prefix
  region             = var.aws_region
  log_retention_days = local.cfg.log_retention_days
  logs_kms_key_arn   = module.kms.key_arns["logs"]
  alerts_kms_key_arn = module.kms.key_arns["messaging"]
  alert_email        = var.alert_email
  cluster_name       = module.cluster.cluster_name
  alb_arn_suffix     = module.alb.alb_arn_suffix
  queue_name         = module.messaging.queue_name
  dlq_name           = module.messaging.dlq_name
  rotation_secret_arns = merge(
    {
      lancamentos = module.rds_lancamentos.master_user_secret_arn
      consolidado = module.rds_consolidado.master_user_secret_arn
    },
    var.enable_api_gateway ? {
      origin_verify = module.api_gateway[0].origin_verify_secret_arn
    } : {},
  )

  api_gateway = var.enable_api_gateway ? {
    api_name = module.api_gateway[0].api_name
    stage    = module.api_gateway[0].stage_name
  } : null

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
      connection_threshold   = local.cfg.rds_connection_threshold
      free_storage_threshold = local.cfg.rds_free_storage_threshold
    }
    consolidado = {
      identifier             = module.rds_consolidado.identifier
      connection_threshold   = local.cfg.rds_connection_threshold
      free_storage_threshold = local.cfg.rds_free_storage_threshold
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
  cpu             = local.cfg.lancamentos_cpu
  memory          = local.cfg.lancamentos_memory
  desired_count   = local.cfg.lancamentos_desired_count
  min_capacity    = local.cfg.lancamentos_min_capacity
  max_capacity    = local.cfg.lancamentos_max_capacity
  adot_image      = var.adot_image

  capacity_provider_strategy = local.capacity_provider_strategy

  environment = merge(
    {
      SERVER_PORT                 = "8081"
      DB_URL                      = "jdbc:postgresql://${module.rds_lancamentos.endpoint}:${module.rds_lancamentos.port}/${module.rds_lancamentos.db_name}"
      DB_SECRET_ARN               = module.rds_lancamentos.master_user_secret_arn
      AWS_REGION                  = var.aws_region
      JWT_ISSUER_URI              = module.cognito.issuer_uri
      APP_SECURITY_SCOPE_PREFIX   = "fluxo-caixa/"
      OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4318"
      SPRING_PROFILES_ACTIVE      = "aws"
      LANCAMENTOS_TOPIC_ARN       = module.messaging.topic_arn
    },
    module.rds_lancamentos.reader_endpoint == null ? {} : {
      DB_READER_URL = "jdbc:postgresql://${module.rds_lancamentos.reader_endpoint}:${module.rds_lancamentos.port}/${module.rds_lancamentos.db_name}"
    },
  )

  task_secret_arns         = [module.rds_lancamentos.master_user_secret_arn]
  task_secret_kms_key_arns = [module.kms.key_arns["rds-secret-lancamentos"]]
  execution_kms_key_arns = [
    module.kms.key_arns["logs"],
    module.kms.key_arns["ecr"],
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
  enable_execute_command         = local.cfg.enable_execute_command
  enable_request_count_scaling   = true
  request_count_target           = var.lancamentos_request_count_target
  alb_resource_label             = "${module.alb.alb_arn_suffix}/${module.alb.target_group_arn_suffixes["lancamentos"]}"

  data_plane_access = [
    {
      security_group_id = module.rds_lancamentos.client_security_group_id
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
  cpu             = local.cfg.consolidado_cpu
  memory          = local.cfg.consolidado_memory
  desired_count   = local.cfg.consolidado_desired_count
  min_capacity    = local.cfg.consolidado_min_capacity
  max_capacity    = local.cfg.consolidado_max_capacity
  adot_image      = var.adot_image

  capacity_provider_strategy = local.capacity_provider_strategy

  environment = merge(
    {
      SERVER_PORT                 = "8082"
      DB_URL                      = "jdbc:postgresql://${module.rds_consolidado.endpoint}:${module.rds_consolidado.port}/${module.rds_consolidado.db_name}"
      DB_SECRET_ARN               = module.rds_consolidado.master_user_secret_arn
      AWS_REGION                  = var.aws_region
      JWT_ISSUER_URI              = module.cognito.issuer_uri
      APP_SECURITY_SCOPE_PREFIX   = "fluxo-caixa/"
      OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4318"
      SPRING_PROFILES_ACTIVE      = "aws"
      CONSOLIDADO_QUEUE_URL       = module.messaging.queue_url
      REDIS_HOST                  = module.redis.primary_endpoint
      REDIS_PORT                  = tostring(module.redis.port)
      REDIS_SSL                   = "true"
    },
    module.rds_consolidado.reader_endpoint == null ? {} : {
      DB_READER_URL = "jdbc:postgresql://${module.rds_consolidado.reader_endpoint}:${module.rds_consolidado.port}/${module.rds_consolidado.db_name}"
    },
  )

  task_secret_arns         = [module.rds_consolidado.master_user_secret_arn]
  task_secret_kms_key_arns = [module.kms.key_arns["rds-secret-consolidado"]]
  execution_kms_key_arns = [
    module.kms.key_arns["logs"],
    module.kms.key_arns["ecr"],
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
  enable_execute_command         = local.cfg.enable_execute_command
  enable_request_count_scaling   = true
  request_count_target           = var.consolidado_request_count_target
  alb_resource_label             = "${module.alb.alb_arn_suffix}/${module.alb.target_group_arn_suffixes["consolidado"]}"
  queue_name                     = module.messaging.queue_name
  queue_backlog_per_task         = var.consolidado_queue_backlog_per_task

  data_plane_access = [
    {
      security_group_id = module.rds_consolidado.client_security_group_id
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

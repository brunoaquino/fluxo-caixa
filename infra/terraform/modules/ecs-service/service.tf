locals {
  # O caller pode sobrescrever OTEL_PROPAGATORS. O default injeta W3C e X-Ray.
  environment_map = merge(
    {
      OTEL_PROPAGATORS = var.otel_propagators
    },
    var.environment,
  )

  adot_config = templatefile("${path.module}/adot-config.yaml.tftpl", {
    region            = var.aws_region
    metrics_log_group = var.metrics_log_group_name
    service_name      = var.name
  })

  app_container = merge(
    {
      name      = var.container_name
      image     = var.container_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
          protocol      = "tcp"
        }
      ]
      environment = [
        for key, value in local.environment_map : {
          name  = key
          value = value
        }
      ]
      readonlyRootFilesystem = var.readonly_root_filesystem
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.log_group_name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "app"
        }
      }
      dependsOn = [
        {
          containerName = "adot-collector"
          condition     = "START"
        }
      ]
    },
    length(var.secrets) == 0 ? {} : { secrets = var.secrets },
    var.readonly_root_filesystem ? {
      mountPoints = [
        {
          sourceVolume  = "tmp"
          containerPath = "/tmp"
          readOnly      = false
        }
      ]
    } : {}
  )

  adot_container = {
    name      = "adot-collector"
    image     = var.adot_image
    essential = false
    environment = [
      {
        name  = "ADOT_CONFIG_REVISION"
        value = sha256(local.adot_config)
      },
      {
        name  = "OTEL_PROPAGATORS"
        value = "tracecontext,xray"
      },
    ]
    secrets = [
      {
        name      = "AOT_CONFIG_CONTENT"
        valueFrom = aws_ssm_parameter.adot.arn
      }
    ]
    readonlyRootFilesystem = false
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = var.log_group_name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "adot"
      }
    }
  }
}

resource "aws_ssm_parameter" "adot" {
  name        = "/${var.name}/adot-config"
  description = "Configuracao do ADOT collector da task ${var.name}."
  type        = "String"
  tier        = "Standard"
  value       = local.adot_config
}

resource "aws_security_group" "service" {
  name        = "${var.name}-tasks"
  description = "Tasks ${var.name}. Entrada apenas do ALB."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name}-tasks"
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_alb" {
  security_group_id            = aws_security_group.service.id
  referenced_security_group_id = var.alb_security_group_id
  description                  = "Trafego do ALB na porta do container."
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_service" {
  security_group_id            = var.alb_security_group_id
  referenced_security_group_id = aws_security_group.service.id
  description                  = "ALB para as tasks de ${var.name}."
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "data" {
  for_each = { for access in var.data_plane_access : access.description => access }

  security_group_id            = aws_security_group.service.id
  referenced_security_group_id = each.value.security_group_id
  description                  = each.value.description
  from_port                    = each.value.port
  to_port                      = each.value.port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "data" {
  for_each = { for access in var.data_plane_access : access.description => access }

  security_group_id            = each.value.security_group_id
  referenced_security_group_id = aws_security_group.service.id
  description                  = "Conexoes de ${var.name}."
  from_port                    = each.value.port
  to_port                      = each.value.port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "dns_udp" {
  security_group_id = aws_security_group.service.id
  description       = "DNS UDP do resolver da VPC."
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
}

resource "aws_vpc_security_group_egress_rule" "dns_tcp" {
  security_group_id = aws_security_group.service.id
  description       = "DNS TCP do resolver da VPC."
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "endpoints" {
  count = var.vpc_endpoint_security_group_id == null ? 0 : 1

  security_group_id            = aws_security_group.service.id
  referenced_security_group_id = var.vpc_endpoint_security_group_id
  description                  = "HTTPS para os VPC endpoints."
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

# Dev nao cria interface endpoint (secao 15). A task sem IP publico sai para as APIs AWS pelo NAT.
#trivy:ignore:AVD-AWS-0104
resource "aws_vpc_security_group_egress_rule" "nat_https" {
  count = var.vpc_endpoint_security_group_id == null ? 1 : 0

  security_group_id = aws_security_group.service.id
  description       = "HTTPS de APIs AWS pelo NAT, sem interface endpoint."
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  dynamic "volume" {
    for_each = var.readonly_root_filesystem ? [1] : []

    content {
      name = "tmp"
    }
  }

  container_definitions = jsonencode([
    local.adot_container,
    local.app_container,
  ])

  tags = {
    Name = var.name
  }
}

resource "aws_ecs_service" "this" {
  name             = var.name
  cluster          = var.cluster_arn
  task_definition  = aws_ecs_task_definition.this.arn
  desired_count    = var.desired_count
  platform_version = "LATEST"

  dynamic "capacity_provider_strategy" {
    for_each = var.capacity_provider_strategy

    content {
      capacity_provider = capacity_provider_strategy.value.capacity_provider
      weight            = capacity_provider_strategy.value.weight
      base              = capacity_provider_strategy.value.base
    }
  }

  availability_zone_rebalancing      = "ENABLED"
  enable_execute_command             = var.enable_execute_command
  enable_ecs_managed_tags            = true
  propagate_tags                     = "SERVICE"
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.container_name
    container_port   = var.container_port
  }

  # O autoscaling muda desired_count. A task definition continua sob o Terraform:
  # um deploy feito so pelo CD e reconciliado no proximo apply se image_tag divergir.
  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [
    aws_vpc_security_group_ingress_rule.from_alb,
    aws_vpc_security_group_egress_rule.alb_to_service,
    aws_iam_role_policy.execution,
    aws_iam_role_policy.task,
  ]

  tags = {
    Name = var.name
  }
}

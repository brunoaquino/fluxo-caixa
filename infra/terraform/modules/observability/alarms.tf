locals {
  alarm_actions = [aws_sns_topic.alerts.arn]
  api_dimensions = {
    ApiName = var.api_gateway == null ? "unused" : var.api_gateway.api_name
    Stage   = var.api_gateway == null ? "unused" : var.api_gateway.stage
  }
}

resource "aws_cloudwatch_metric_alarm" "elb_5xx" {
  alarm_name          = "${var.name_prefix}-alb-5xx"
  alarm_description   = "5xx gerados pelo proprio ALB."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 5
  treat_missing_data  = "notBreaching"
  metric_name         = "HTTPCode_ELB_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  for_each = var.services

  alarm_name          = "${var.name_prefix}-${each.key}-5xx"
  alarm_description   = "Respostas 5xx do ${each.key}."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 5
  treat_missing_data  = "notBreaching"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = each.value.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "latency_p99" {
  for_each = var.services

  alarm_name          = "${var.name_prefix}-${each.key}-p99"
  alarm_description   = "Latencia p99 do ${each.key} acima do SLO."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = each.value.latency_p99_seconds
  treat_missing_data  = "notBreaching"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  extended_statistic  = "p99"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = each.value.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  for_each = var.services

  alarm_name          = "${var.name_prefix}-${each.key}-cpu"
  alarm_description   = "CPU do servico ${each.key}."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 80
  treat_missing_data  = "notBreaching"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = each.value.ecs_service_name
  }
}

resource "aws_cloudwatch_metric_alarm" "memory" {
  for_each = var.services

  alarm_name          = "${var.name_prefix}-${each.key}-memoria"
  alarm_description   = "Memoria do servico ${each.key}."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 80
  treat_missing_data  = "notBreaching"
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = each.value.ecs_service_name
  }
}

resource "aws_cloudwatch_metric_alarm" "queue_depth" {
  alarm_name          = "${var.name_prefix}-fila-profundidade"
  alarm_description   = "Mensagens visiveis na fila do consolidado."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = var.queue_visible_threshold
  treat_missing_data  = "notBreaching"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    QueueName = var.queue_name
  }
}

resource "aws_cloudwatch_metric_alarm" "queue_age" {
  alarm_name          = "${var.name_prefix}-fila-idade"
  alarm_description   = "Idade da mensagem mais antiga na fila do consolidado."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = var.queue_age_threshold_seconds
  treat_missing_data  = "notBreaching"
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    QueueName = var.queue_name
  }
}

resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "${var.name_prefix}-dlq"
  alarm_description   = "Existe mensagem na DLQ. O consolidado nao conseguiu processar depois de 5 recebimentos."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1
  treat_missing_data  = "notBreaching"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    QueueName = var.dlq_name
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  for_each = var.rds_instances

  alarm_name          = "${var.name_prefix}-rds-${each.key}-cpu"
  alarm_description   = "CPU do RDS ${each.key}."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 80
  treat_missing_data  = "notBreaching"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = each.value.identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  for_each = var.rds_instances

  alarm_name          = "${var.name_prefix}-rds-${each.key}-storage"
  alarm_description   = "Espaco livre do RDS ${each.key}."
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = each.value.free_storage_threshold
  treat_missing_data  = "notBreaching"
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = each.value.identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  for_each = var.rds_instances

  alarm_name          = "${var.name_prefix}-rds-${each.key}-conexoes"
  alarm_description   = "Conexoes do RDS ${each.key}."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = each.value.connection_threshold
  treat_missing_data  = "notBreaching"
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = each.value.identifier
  }
}

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  count = var.api_gateway == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-api-5xx"
  alarm_description   = "5XX do API Gateway, incluindo falha de integracao."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 5
  treat_missing_data  = "notBreaching"
  metric_name         = "5XXError"
  namespace           = "AWS/ApiGateway"
  period              = 60
  statistic           = "Sum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = local.api_dimensions
}

resource "aws_cloudwatch_metric_alarm" "api_4xx" {
  count = var.api_gateway == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-api-4xx"
  alarm_description   = "4XX elevado no API Gateway. Token invalido conta aqui e nao chega na task."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 100
  treat_missing_data  = "notBreaching"
  metric_name         = "4XXError"
  namespace           = "AWS/ApiGateway"
  period              = 60
  statistic           = "Sum"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = local.api_dimensions
}

resource "aws_cloudwatch_metric_alarm" "api_latency_p99" {
  count = var.api_gateway == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-api-latencia-p99"
  alarm_description   = "Latencia p99 do API Gateway acima de 1 s. A metrica e em milissegundos."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 1000
  treat_missing_data  = "notBreaching"
  metric_name         = "Latency"
  namespace           = "AWS/ApiGateway"
  period              = 60
  extended_statistic  = "p99"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = local.api_dimensions
}

resource "aws_cloudwatch_metric_alarm" "api_integration_latency_p99" {
  count = var.api_gateway == null ? 0 : 1

  alarm_name          = "${var.name_prefix}-api-integracao-p99"
  alarm_description   = "IntegrationLatency p99 acima de 800 ms. O timeout de integracao e 10 s."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 800
  treat_missing_data  = "notBreaching"
  metric_name         = "IntegrationLatency"
  namespace           = "AWS/ApiGateway"
  period              = 60
  extended_statistic  = "p99"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = local.api_dimensions
}

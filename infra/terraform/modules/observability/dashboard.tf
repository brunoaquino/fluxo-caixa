resource "aws_cloudwatch_dashboard" "operacao" {
  dashboard_name = "${var.name_prefix}-operacao"

  dashboard_body = jsonencode({
    widgets = concat(
      [
        {
          type   = "metric"
          x      = 0
          y      = 0
          width  = 12
          height = 6
          properties = {
            title  = "ALB pedidos e 5xx"
            region = var.region
            period = 60
            metrics = [
              ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix, { stat = "Sum" }],
              ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.alb_arn_suffix, { stat = "Sum" }],
              ["AWS/ApplicationELB", "HTTPCode_ELB_5XX_Count", "LoadBalancer", var.alb_arn_suffix, { stat = "Sum" }],
            ]
          }
        },
        {
          type   = "metric"
          x      = 12
          y      = 0
          width  = 12
          height = 6
          properties = {
            title  = "Latencia do ALB"
            region = var.region
            period = 60
            metrics = [
              ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, { stat = "p95" }],
              ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, { stat = "p99" }],
            ]
          }
        },
        {
          type   = "metric"
          x      = 0
          y      = 6
          width  = 12
          height = 6
          properties = {
            title  = "CPU e memoria do ECS"
            region = var.region
            period = 60
            metrics = flatten([
              for name, service in var.services : [
                ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", service.ecs_service_name, { label = "${name} cpu" }],
                ["AWS/ECS", "MemoryUtilization", "ClusterName", var.cluster_name, "ServiceName", service.ecs_service_name, { label = "${name} memoria" }],
              ]
            ])
          }
        },
        {
          type   = "metric"
          x      = 12
          y      = 6
          width  = 12
          height = 6
          properties = {
            title  = "Fila do consolidado"
            region = var.region
            period = 60
            metrics = [
              ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.queue_name, { stat = "Average" }],
              ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", var.queue_name, { stat = "Maximum" }],
              ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.dlq_name, { stat = "Maximum", label = "DLQ" }],
            ]
          }
        },
      ],
      var.api_gateway == null ? [] : [
        {
          type   = "metric"
          x      = 0
          y      = 12
          width  = 24
          height = 6
          properties = {
            title  = "API Gateway"
            region = var.region
            period = 60
            metrics = [
              ["AWS/ApiGateway", "Count", "ApiName", var.api_gateway.api_name, "Stage", var.api_gateway.stage, { stat = "Sum" }],
              ["AWS/ApiGateway", "4XXError", "ApiName", var.api_gateway.api_name, "Stage", var.api_gateway.stage, { stat = "Sum" }],
              ["AWS/ApiGateway", "5XXError", "ApiName", var.api_gateway.api_name, "Stage", var.api_gateway.stage, { stat = "Sum" }],
              ["AWS/ApiGateway", "Latency", "ApiName", var.api_gateway.api_name, "Stage", var.api_gateway.stage, { stat = "p99" }],
              ["AWS/ApiGateway", "IntegrationLatency", "ApiName", var.api_gateway.api_name, "Stage", var.api_gateway.stage, { stat = "p99" }],
            ]
          }
        },
      ],
    )
  })
}

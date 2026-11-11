check "consolidado_acima_do_pico" {
  assert {
    condition     = !var.require_consolidado_above_peak || var.route_throttling.consolidado_read.rate_limit > 50
    error_message = "hml e prod exigem throttle do consolidado acima do pico de 50 req/s."
  }
}

locals {
  api_fingerprint = sha1(jsonencode({
    routes = {
      for key, method in local.methods : key => {
        http_method = method.http_method
        scope       = method.scope
        validator   = method.validator
        method_path = method.method_path
        uri         = method.uri
        parameters  = method.parameters
        integration = method.integration_parameters
        rate        = method.throttle.rate_limit
        burst       = method.throttle.burst_limit
      }
    }
    models = {
      lancamento = local.model_lancamento
      estorno    = local.model_estorno
    }
    alb        = var.alb_arn
    vpc_link   = aws_apigatewayv2_vpc_link.this.id
    authorizer = aws_api_gateway_authorizer.cognito.id
    timeout_ms = 10000
    connection = "VPC_LINK_V2"
  }))
}

resource "aws_cloudwatch_log_group" "access" {
  name              = "/aws/apigateway/${var.name_prefix}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.logs_kms_key_arn
}

resource "aws_cloudwatch_log_resource_policy" "access" {
  policy_name = "${var.name_prefix}-api-access"

  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ApiGatewayAccessLogs"
        Effect = "Allow"
        Principal = {
          Service = "apigateway.amazonaws.com"
        }
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.access.arn}:*"
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = local.account_id
          }
          ArnLike = {
            "aws:SourceArn" = "arn:aws:logs:${local.region}:${local.account_id}:*"
          }
        }
      }
    ]
  })
}

resource "aws_api_gateway_deployment" "this" {
  rest_api_id = aws_api_gateway_rest_api.this.id

  triggers = {
    redeployment = local.api_fingerprint
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.this,
    aws_api_gateway_method.this,
    aws_api_gateway_model.lancamento,
    aws_api_gateway_model.estorno,
  ]
}

resource "aws_api_gateway_stage" "v1" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  stage_name    = var.stage_name
  deployment_id = aws_api_gateway_deployment.this.id
  description   = "Stage ${var.stage_name}. Cache desligado por padrao."

  xray_tracing_enabled  = true
  cache_cluster_enabled = var.cache_enabled
  cache_cluster_size    = var.cache_enabled ? "0.5" : null

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn
    format          = local.access_log_format
  }

  dynamic "canary_settings" {
    for_each = var.canary == null ? [] : [var.canary]

    content {
      percent_traffic          = canary_settings.value.percent_traffic
      deployment_id            = aws_api_gateway_deployment.this.id
      use_stage_cache          = canary_settings.value.use_stage_cache
      stage_variable_overrides = canary_settings.value.stage_variable_overrides
    }
  }

  depends_on = [aws_cloudwatch_log_resource_policy.access]

  tags = {
    Name = "${var.name_prefix}-${var.stage_name}"
  }
}

resource "aws_api_gateway_method_settings" "stage" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.v1.stage_name
  method_path = "*/*"

  settings {
    metrics_enabled                            = true
    logging_level                              = "OFF"
    data_trace_enabled                         = false
    throttling_rate_limit                      = var.stage_throttling.rate_limit
    throttling_burst_limit                     = var.stage_throttling.burst_limit
    caching_enabled                            = var.cache_enabled
    cache_ttl_in_seconds                       = var.cache_enabled ? var.cache_ttl_seconds : 0
    cache_data_encrypted                       = true
    require_authorization_for_cache_control    = true
    unauthorized_cache_control_header_strategy = "FAIL_WITH_403"
  }
}

resource "aws_api_gateway_method_settings" "route" {
  for_each = local.methods

  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.v1.stage_name
  method_path = "${each.value.method_path}/${each.value.http_method}"

  settings {
    metrics_enabled                            = true
    logging_level                              = "OFF"
    data_trace_enabled                         = false
    throttling_rate_limit                      = each.value.throttle.rate_limit
    throttling_burst_limit                     = each.value.throttle.burst_limit
    caching_enabled                            = var.cache_enabled
    cache_ttl_in_seconds                       = var.cache_enabled ? var.cache_ttl_seconds : 0
    cache_data_encrypted                       = true
    require_authorization_for_cache_control    = true
    unauthorized_cache_control_header_strategy = "FAIL_WITH_403"
  }
}

resource "aws_api_gateway_usage_plan" "consumer" {
  for_each = var.api_consumers

  name        = "${var.name_prefix}-${each.key}"
  description = each.value.description

  api_stages {
    api_id = aws_api_gateway_rest_api.this.id
    stage  = aws_api_gateway_stage.v1.stage_name
  }

  quota_settings {
    limit  = each.value.quota_limit
    period = each.value.quota_period
  }

  throttle_settings {
    rate_limit  = each.value.throttle_rate
    burst_limit = each.value.throttle_burst
  }

  tags = {
    Name = "${var.name_prefix}-${each.key}"
  }
}

resource "aws_api_gateway_api_key" "consumer" {
  for_each = var.api_consumers

  name        = "${var.name_prefix}-${each.key}"
  description = each.value.description
  enabled     = true

  tags = {
    Name = "${var.name_prefix}-${each.key}"
  }
}

resource "aws_api_gateway_usage_plan_key" "consumer" {
  for_each = var.api_consumers

  key_id        = aws_api_gateway_api_key.consumer[each.key].id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.consumer[each.key].id
}

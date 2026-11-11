mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
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
  name_prefix           = "fluxo-caixa-dev"
  vpc_id                = "vpc-dev"
  subnet_ids            = ["subnet-a", "subnet-b"]
  alb_arn               = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/fluxo-caixa-dev-alb/abc"
  alb_dns_name          = "internal-fluxo-caixa-dev-alb.us-east-1.elb.amazonaws.com"
  alb_security_group_id = "sg-alb"
  cognito_user_pool_arn = "arn:aws:cognito-idp:us-east-1:123456789012:userpool/us-east-1_example"
  logs_kms_key_arn      = "arn:aws:kms:us-east-1:123456789012:key/logs"
  secret_kms_key_arn    = "arn:aws:kms:us-east-1:123456789012:key/origin-verify"
  log_retention_days    = 7
  stage_throttling = {
    rate_limit  = 30
    burst_limit = 60
  }
  route_throttling = {
    consolidado_read = {
      rate_limit  = 20
      burst_limit = 40
    }
    lancamentos_write = {
      rate_limit  = 10
      burst_limit = 20
    }
    lancamentos_read = {
      rate_limit  = 10
      burst_limit = 20
    }
  }
}

run "so_rotas_de_negocio" {
  command = plan

  override_data {
    target = data.aws_secretsmanager_secret_version.origin

    values = {
      secret_string = "{\"current\":\"valor-vigente\",\"previous\":\"valor-anterior\"}"
    }

    override_during = plan
  }

  assert {
    condition = toset(keys(aws_api_gateway_method.this)) == toset([
      "POST /api/v1/lancamentos",
      "GET /api/v1/lancamentos",
      "GET /api/v1/lancamentos/{id}",
      "POST /api/v1/lancamentos/{id}/estornos",
      "GET /api/v1/consolidados/diario",
      "GET /api/v1/consolidados/diario/{data}",
    ])
    error_message = "O gateway so publica as seis rotas da secao 7."
  }

  assert {
    condition = alltrue([
      for integracao in aws_api_gateway_integration.this :
      !strcontains(integracao.uri, "actuator") &&
      !strcontains(integracao.uri, "swagger") &&
      !strcontains(integracao.uri, "api-docs")
    ])
    error_message = "Nenhuma integracao aponta para actuator, swagger ou api-docs."
  }

  assert {
    condition = alltrue([
      for chave, metodo in aws_api_gateway_method.this :
      metodo.authorization == "COGNITO_USER_POOLS" &&
      one(metodo.authorization_scopes) == {
        "POST /api/v1/lancamentos"               = "fluxo-caixa/lancamentos:write"
        "GET /api/v1/lancamentos"                = "fluxo-caixa/lancamentos:read"
        "GET /api/v1/lancamentos/{id}"           = "fluxo-caixa/lancamentos:read"
        "POST /api/v1/lancamentos/{id}/estornos" = "fluxo-caixa/lancamentos:write"
        "GET /api/v1/consolidados/diario"        = "fluxo-caixa/consolidado:read"
        "GET /api/v1/consolidados/diario/{data}" = "fluxo-caixa/consolidado:read"
      }[chave] &&
      metodo.api_key_required == false
    ])
    error_message = "Todo metodo usa Cognito com o escopo da rota e nao exige API key por padrao."
  }

  assert {
    condition = alltrue([
      for integracao in aws_api_gateway_integration.this :
      integracao.type == "HTTP_PROXY" &&
      integracao.connection_type == "VPC_LINK" &&
      integracao.integration_target == var.alb_arn &&
      integracao.timeout_milliseconds == 10000
    ])
    error_message = "A integracao e HTTP_PROXY via VPC Link v2 no ALB, com timeout de 10 s."
  }

  assert {
    condition     = one(aws_api_gateway_rest_api.this.endpoint_configuration[0].types) == "REGIONAL" || contains(aws_api_gateway_rest_api.this.endpoint_configuration[0].types, "REGIONAL")
    error_message = "A REST API e regional. A borda publica continua no CloudFront."
  }

  assert {
    condition = (
      aws_api_gateway_stage.v1.stage_name == "v1" &&
      aws_api_gateway_stage.v1.xray_tracing_enabled == true &&
      length(aws_api_gateway_stage.v1.access_log_settings) == 1 &&
      aws_api_gateway_stage.v1.cache_cluster_enabled == false
    )
    error_message = "O stage v1 tem X-Ray, access log e cache desligado."
  }

  assert {
    condition     = aws_api_gateway_method_settings.stage.method_path == "*/*" && aws_api_gateway_method_settings.stage.settings[0].metrics_enabled == true
    error_message = "O stage liga metrica detalhada."
  }

  assert {
    condition     = length(aws_api_gateway_usage_plan.consumer) == 0
    error_message = "Sem api_consumers nao ha usage plan."
  }

  assert {
    condition = contains([
      for regra in aws_wafv2_web_acl.regional.rule : regra.name
    ], "x-origin-verify")
    error_message = "O WAF regional tem a regra que exige X-Origin-Verify."
  }

  assert {
    condition     = aws_wafv2_web_acl.regional.scope == "REGIONAL"
    error_message = "O WAF da API e regional, associado ao stage."
  }

  assert {
    condition = (
      data.aws_secretsmanager_secret_version.origin.version_stage == "AWSCURRENT" &&
      aws_secretsmanager_secret_version.origin.secret_string == null &&
      aws_secretsmanager_secret_version.origin.secret_string_wo_version == 1 &&
      output.method_validator_names["POST /api/v1/lancamentos/{id}/estornos"] == "parametros" &&
      output.method_validator_names["POST /api/v1/lancamentos"] == "corpo" &&
      aws_api_gateway_method.this["POST /api/v1/lancamentos/{id}/estornos"].request_models["application/json"] == "EstornarLancamento"
    )
    error_message = "O plan le AWSCURRENT, nao guarda secret_string no resource, e o estorno nao exige corpo."
  }
}

run "api_key_em_uma_rota_e_consumidor" {
  command = plan

  override_data {
    target = data.aws_secretsmanager_secret_version.origin

    values = {
      secret_string = "{\"current\":\"valor-vigente\",\"previous\":\"valor-anterior\"}"
    }

    override_during = plan
  }

  variables {
    api_key_required = {
      "POST /api/v1/lancamentos" = true
    }
    api_consumers = {
      pdv = {
        description    = "PDV do laboratorio"
        quota_limit    = 10000
        quota_period   = "MONTH"
        throttle_rate  = 5
        throttle_burst = 10
      }
    }
    require_consolidado_above_peak = true
    route_throttling = {
      consolidado_read = {
        rate_limit  = 80
        burst_limit = 160
      }
      lancamentos_write = {
        rate_limit  = 40
        burst_limit = 80
      }
      lancamentos_read = {
        rate_limit  = 40
        burst_limit = 80
      }
    }
  }

  assert {
    condition     = aws_api_gateway_method.this["POST /api/v1/lancamentos"].api_key_required == true
    error_message = "A rota indicada passa a exigir API key."
  }

  assert {
    condition     = aws_api_gateway_method.this["GET /api/v1/lancamentos"].api_key_required == false
    error_message = "As outras rotas continuam so com JWT."
  }

  assert {
    condition     = length(aws_api_gateway_usage_plan.consumer) == 1 && aws_api_gateway_usage_plan.consumer["pdv"].quota_settings[0].limit == 10000
    error_message = "O consumidor ganha usage plan com a cota informada."
  }
}

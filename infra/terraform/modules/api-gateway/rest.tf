resource "aws_api_gateway_rest_api" "this" {
  name        = "${var.name_prefix}-api"
  description = "API regional atras do CloudFront. O execute-api continua ligado porque o CloudFront usa esse host. O WAF regional barra quem chega sem X-Origin-Verify."

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = {
    Name = "${var.name_prefix}-api"
  }
}

resource "aws_api_gateway_authorizer" "cognito" {
  name          = "cognito"
  rest_api_id   = aws_api_gateway_rest_api.this.id
  type          = "COGNITO_USER_POOLS"
  provider_arns = [var.cognito_user_pool_arn]
}

resource "aws_api_gateway_request_validator" "corpo" {
  name                        = "corpo-e-parametros"
  rest_api_id                 = aws_api_gateway_rest_api.this.id
  validate_request_body       = true
  validate_request_parameters = true
}

resource "aws_api_gateway_request_validator" "parametros" {
  name                        = "parametros"
  rest_api_id                 = aws_api_gateway_rest_api.this.id
  validate_request_body       = false
  validate_request_parameters = true
}

resource "aws_api_gateway_model" "lancamento" {
  rest_api_id  = aws_api_gateway_rest_api.this.id
  name         = "RegistrarLancamento"
  description  = "POST /api/v1/lancamentos. categoria e opcional. valor tem no maximo 2 casas."
  content_type = "application/json"
  schema       = local.model_lancamento
}

resource "aws_api_gateway_model" "estorno" {
  rest_api_id  = aws_api_gateway_rest_api.this.id
  name         = "EstornarLancamento"
  description  = "Contrato do estorno. O metodo nao valida o corpo: a ausencia e valida. motivo, se vier, e checado no servico."
  content_type = "application/json"
  schema       = local.model_estorno
}

resource "aws_api_gateway_resource" "api" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_rest_api.this.root_resource_id
  path_part   = "api"
}

resource "aws_api_gateway_resource" "v1" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.api.id
  path_part   = "v1"
}

resource "aws_api_gateway_resource" "lancamentos" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "lancamentos"
}

resource "aws_api_gateway_resource" "lancamento_id" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.lancamentos.id
  path_part   = "{id}"
}

resource "aws_api_gateway_resource" "estornos" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.lancamento_id.id
  path_part   = "estornos"
}

resource "aws_api_gateway_resource" "consolidados" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "consolidados"
}

resource "aws_api_gateway_resource" "diario" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.consolidados.id
  path_part   = "diario"
}

resource "aws_api_gateway_resource" "diario_data" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.diario.id
  path_part   = "{data}"
}

locals {
  methods = {
    "POST /api/v1/lancamentos" = {
      resource_id            = aws_api_gateway_resource.lancamentos.id
      http_method            = "POST"
      scope                  = "${var.scope_prefix}lancamentos:write"
      validator              = "corpo"
      model                  = aws_api_gateway_model.lancamento.name
      method_path            = "api/v1/lancamentos"
      uri                    = "http://${var.alb_dns_name}/api/v1/lancamentos"
      throttle               = var.route_throttling.lancamentos_write
      parameters             = {}
      integration_parameters = {}
    }
    "GET /api/v1/lancamentos" = {
      resource_id = aws_api_gateway_resource.lancamentos.id
      http_method = "GET"
      scope       = "${var.scope_prefix}lancamentos:read"
      validator   = "parametros"
      model       = null
      method_path = "api/v1/lancamentos"
      uri         = "http://${var.alb_dns_name}/api/v1/lancamentos"
      throttle    = var.route_throttling.lancamentos_read
      parameters = {
        "method.request.querystring.data" = true
        "method.request.querystring.page" = false
        "method.request.querystring.size" = false
      }
      integration_parameters = {}
    }
    "GET /api/v1/lancamentos/{id}" = {
      resource_id = aws_api_gateway_resource.lancamento_id.id
      http_method = "GET"
      scope       = "${var.scope_prefix}lancamentos:read"
      validator   = "parametros"
      model       = null
      method_path = "api/v1/lancamentos/{id}"
      uri         = "http://${var.alb_dns_name}/api/v1/lancamentos/{id}"
      throttle    = var.route_throttling.lancamentos_read
      parameters = {
        "method.request.path.id" = true
      }
      integration_parameters = {
        "integration.request.path.id" = "method.request.path.id"
      }
    }
    "POST /api/v1/lancamentos/{id}/estornos" = {
      resource_id = aws_api_gateway_resource.estornos.id
      http_method = "POST"
      scope       = "${var.scope_prefix}lancamentos:write"
      validator   = "parametros"
      model       = aws_api_gateway_model.estorno.name
      method_path = "api/v1/lancamentos/{id}/estornos"
      uri         = "http://${var.alb_dns_name}/api/v1/lancamentos/{id}/estornos"
      throttle    = var.route_throttling.lancamentos_write
      parameters = {
        "method.request.path.id" = true
      }
      integration_parameters = {
        "integration.request.path.id" = "method.request.path.id"
      }
    }
    "GET /api/v1/consolidados/diario" = {
      resource_id = aws_api_gateway_resource.diario.id
      http_method = "GET"
      scope       = "${var.scope_prefix}consolidado:read"
      validator   = "parametros"
      model       = null
      method_path = "api/v1/consolidados/diario"
      uri         = "http://${var.alb_dns_name}/api/v1/consolidados/diario"
      throttle    = var.route_throttling.consolidado_read
      parameters = {
        "method.request.querystring.inicio" = true
        "method.request.querystring.fim"    = true
      }
      integration_parameters = {}
    }
    "GET /api/v1/consolidados/diario/{data}" = {
      resource_id = aws_api_gateway_resource.diario_data.id
      http_method = "GET"
      scope       = "${var.scope_prefix}consolidado:read"
      validator   = "parametros"
      model       = null
      method_path = "api/v1/consolidados/diario/{data}"
      uri         = "http://${var.alb_dns_name}/api/v1/consolidados/diario/{data}"
      throttle    = var.route_throttling.consolidado_read
      parameters = {
        "method.request.path.data" = true
      }
      integration_parameters = {
        "integration.request.path.data" = "method.request.path.data"
      }
    }
  }
}

resource "aws_api_gateway_method" "this" {
  for_each = local.methods

  rest_api_id          = aws_api_gateway_rest_api.this.id
  resource_id          = each.value.resource_id
  http_method          = each.value.http_method
  authorization        = "COGNITO_USER_POOLS"
  authorizer_id        = aws_api_gateway_authorizer.cognito.id
  authorization_scopes = [each.value.scope]
  api_key_required     = lookup(var.api_key_required, each.key, false)
  request_validator_id = each.value.validator == "corpo" ? aws_api_gateway_request_validator.corpo.id : aws_api_gateway_request_validator.parametros.id

  request_parameters = merge(local.header_method_parameters, each.value.parameters)

  request_models = each.value.model == null ? {} : {
    "application/json" = each.value.model
  }
}

resource "aws_api_gateway_integration" "this" {
  for_each = local.methods

  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = each.value.resource_id
  http_method             = aws_api_gateway_method.this[each.key].http_method
  integration_http_method = each.value.http_method
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.this.id
  integration_target      = var.alb_arn
  uri                     = each.value.uri
  timeout_milliseconds    = 10000

  request_parameters = merge(
    local.header_integration_parameters,
    each.value.integration_parameters,
  )
}

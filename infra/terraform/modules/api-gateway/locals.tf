data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region

  forwarded_headers = [
    "Authorization",
    "traceparent",
    "tracestate",
    "X-Correlation-Id",
    "Idempotency-Key",
  ]

  header_method_parameters = {
    for header in local.forwarded_headers :
    "method.request.header.${header}" => false
  }

  # HTTP_PROXY repassa o que nao estiver mapeado. O mapa explicito garante
  # Authorization e o contexto de trace. O valor vazio tira o segredo do hop ate o servico.
  header_integration_parameters = merge(
    {
      for header in local.forwarded_headers :
      "integration.request.header.${header}" => "method.request.header.${header}"
    },
    {
      "integration.request.header.X-Origin-Verify" = "''"
    },
  )

  origin_verify = jsondecode(data.aws_secretsmanager_secret_version.origin.secret_string)

  model_lancamento = jsonencode({
    "$schema"            = "http://json-schema.org/draft-04/schema#"
    title                = "RegistrarLancamento"
    type                 = "object"
    additionalProperties = false
    required             = ["tipo", "valor", "dataLancamento", "descricao"]
    properties = {
      tipo = {
        type = "string"
        enum = ["CREDITO", "DEBITO"]
      }
      valor = {
        type       = "number"
        minimum    = 0.01
        multipleOf = 0.01
      }
      dataLancamento = {
        type    = "string"
        pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$"
      }
      descricao = {
        type      = "string"
        minLength = 1
        maxLength = 255
      }
      categoria = {
        type      = "string"
        maxLength = 60
      }
    }
  })

  model_estorno = jsonencode({
    "$schema"            = "http://json-schema.org/draft-04/schema#"
    title                = "EstornarLancamento"
    type                 = "object"
    additionalProperties = false
    properties = {
      motivo = {
        type      = "string"
        maxLength = 255
      }
    }
  })

  access_log_format = jsonencode({
    requestId          = "$context.requestId"
    extendedRequestId  = "$context.extendedRequestId"
    xrayTraceId        = "$context.xrayTraceId"
    ip                 = "$context.identity.sourceIp"
    status             = "$context.status"
    latency            = "$context.responseLatency"
    integrationLatency = "$context.integration.latency"
    apiKeyId           = "$context.identity.apiKeyId"
    httpMethod         = "$context.httpMethod"
    path               = "$context.path"
    traceparent        = "$context.requestHeader.traceparent"
    correlationId      = "$context.requestHeader.X-Correlation-Id"
    wafResponseCode    = "$context.wafResponseCode"
  })
}

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

  mock_data "aws_cloudfront_cache_policy" {
    defaults = {
      id = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    }
  }

  mock_data "aws_cloudfront_origin_request_policy" {
    defaults = {
      id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
    }
  }
}

run "borda_padrao" {
  command = plan

  variables {
    name_prefix        = "fluxo-caixa-dev"
    alb_arn            = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/fluxo-caixa-dev-alb/abc"
    alb_dns_name       = "internal-fluxo-caixa-dev-alb.us-east-1.elb.amazonaws.com"
    logs_kms_key_arn   = "arn:aws:kms:us-east-1:123456789012:key/logs"
    log_retention_days = 7
    waf_rate_limit     = 30000
  }

  assert {
    condition     = aws_cloudfront_distribution.this.price_class == "PriceClass_All" && aws_cloudfront_distribution.this.http_version == "http2and3"
    error_message = "CloudFront usa PriceClass_All e HTTP/2+3."
  }

  assert {
    condition     = one(aws_cloudfront_distribution.this.viewer_certificate).minimum_protocol_version == "TLSv1.2_2021" && one(aws_cloudfront_distribution.this.viewer_certificate).cloudfront_default_certificate == true
    error_message = "Sem dominio, o certificado e o padrao do CloudFront com TLS 1.2 2021."
  }

  assert {
    condition     = aws_wafv2_web_acl.this.scope == "CLOUDFRONT"
    error_message = "O WAF da borda tem escopo CLOUDFRONT."
  }

  assert {
    condition = toset([
      for behavior in aws_cloudfront_distribution.this.ordered_cache_behavior : behavior.path_pattern
      ]) == toset([
      "/api/v1/consolidados*",
      "/api/v1/lancamentos*",
    ])
    error_message = "So /api/v1/lancamentos* e /api/v1/consolidados* vao para o ALB."
  }

  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.this.ordered_cache_behavior :
      behavior.cache_policy_id == one(aws_cloudfront_distribution.this.default_cache_behavior[*].cache_policy_id)
    ])
    error_message = "Os comportamentos da API usam CachingDisabled, o mesmo do default."
  }

  assert {
    condition     = length(one(aws_cloudfront_distribution.this.default_cache_behavior[*].function_association)) == 1
    error_message = "O comportamento padrao devolve 404 na funcao e nao encaminha /actuator ao ALB."
  }

  assert {
    condition     = one(one(aws_cloudfront_distribution.this.default_cache_behavior[*].function_association)).event_type == "viewer-request"
    error_message = "A funcao de 404 roda no viewer-request, antes da origem."
  }

  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.this.ordered_cache_behavior :
      behavior.target_origin_id == "alb"
    ])
    error_message = "Sem API Gateway, /api/v1 vai para a VPC Origin do ALB."
  }
}

run "com_api_gateway" {
  command = plan

  variables {
    name_prefix                = "fluxo-caixa-dev"
    alb_arn                    = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/fluxo-caixa-dev-alb/abc"
    alb_dns_name               = "internal-fluxo-caixa-dev-alb.us-east-1.elb.amazonaws.com"
    logs_kms_key_arn           = "arn:aws:kms:us-east-1:123456789012:key/logs"
    log_retention_days         = 7
    enable_api_gateway         = true
    api_gateway_domain_name    = "abc123.execute-api.us-east-1.amazonaws.com"
    api_gateway_origin_path    = "/v1"
    origin_verify_header_value = "segredo-atual"
  }

  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.this.ordered_cache_behavior :
      behavior.target_origin_id == "api-gateway" &&
      behavior.origin_request_policy_id == data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    ])
    error_message = "Com API Gateway, /api/v1 usa a origem execute-api e AllViewerExceptHostHeader."
  }

  assert {
    condition = anytrue([
      for origin in aws_cloudfront_distribution.this.origin :
      origin.origin_id == "api-gateway" &&
      origin.origin_path == "/v1" &&
      one(origin.custom_origin_config).origin_protocol_policy == "https-only" &&
      length(origin.custom_header) == 1
    ])
    error_message = "A origem do API Gateway e HTTPS, com origin_path do stage e header X-Origin-Verify."
  }

  assert {
    condition     = length(one(aws_cloudfront_distribution.this.default_cache_behavior[*].function_association)) == 1
    error_message = "O comportamento padrao continua devolvendo 404 sem chamar origem."
  }
}

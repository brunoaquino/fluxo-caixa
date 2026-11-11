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

  mock_data "aws_ec2_managed_prefix_list" {
    defaults = {
      id   = "pl-cloudfront"
      name = "com.amazonaws.global.cloudfront.origin-facing"
    }
  }
}

run "somente_prefix_list_do_cloudfront" {
  command = plan

  variables {
    name_prefix             = "fluxo-caixa-dev"
    vpc_id                  = "vpc-dev"
    subnet_ids              = ["subnet-a", "subnet-b"]
    deletion_protection     = false
    access_logs_bucket_name = "fluxo-caixa-dev-alb-logs-123456789012"
    enable_waf              = false
    services = {
      lancamentos = {
        port          = 8081
        path_patterns = ["/api/v1/lancamentos*"]
        health_path   = "/actuator/health/readiness"
        priority      = 10
      }
    }
  }

  assert {
    condition     = aws_lb.this.internal == true
    error_message = "O ALB tem de ser interno. A entrada publica e o CloudFront."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.cloudfront.prefix_list_id == "pl-cloudfront"
    error_message = "O security group do ALB so aceita o prefix list com.amazonaws.global.cloudfront.origin-facing."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.cloudfront.cidr_ipv4 == null
    error_message = "O ALB nao aceita CIDR publico."
  }

  assert {
    condition     = length(aws_wafv2_web_acl.this) == 0
    error_message = "O WAF regional do ALB fica desligado. A inspecao e no WAF de escopo CLOUDFRONT."
  }

  assert {
    condition     = one(one(aws_s3_bucket_server_side_encryption_configuration.logs.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
    error_message = "Access log do ALB usa SSE-S3. O ELB nao grava em bucket com CMK."
  }
}

run "listener_so_encaminha_as_duas_apis" {
  command = plan

  variables {
    name_prefix             = "fluxo-caixa-prod"
    vpc_id                  = "vpc-prod"
    subnet_ids              = ["subnet-a", "subnet-b", "subnet-c"]
    deletion_protection     = true
    access_logs_bucket_name = "fluxo-caixa-prod-alb-logs-123456789012" # gitleaks:allow nome de bucket de teste, nao credencial
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
  }

  assert {
    condition     = one(aws_lb_listener.http.default_action).type == "fixed-response"
    error_message = "A acao padrao do listener e resposta fixa. O que nao casa com a API nao vai para a task."
  }

  assert {
    condition     = one(one(aws_lb_listener.http.default_action).fixed_response).status_code == "404"
    error_message = "A acao padrao do listener devolve 404. /actuator nao e regra de encaminhamento."
  }

  assert {
    condition = toset([
      for rule in aws_lb_listener_rule.service : one(one(one(rule.condition).path_pattern).values)
      ]) == toset([
      "/api/v1/consolidados*",
      "/api/v1/lancamentos*",
    ])
    error_message = "O listener so encaminha /api/v1/lancamentos* e /api/v1/consolidados*."
  }

  assert {
    condition = alltrue([
      for rule in aws_lb_listener_rule.service :
      !strcontains(one(one(one(rule.condition).path_pattern).values), "actuator")
    ])
    error_message = "Nenhuma regra do listener encaminha /actuator. O health check e do target group, direto na task."
  }
}

run "prefixo_do_stage_e_reescrito" {
  command = plan

  variables {
    name_prefix             = "fluxo-caixa-dev"
    vpc_id                  = "vpc-dev"
    subnet_ids              = ["subnet-a", "subnet-b"]
    deletion_protection     = false
    access_logs_bucket_name = "fluxo-caixa-dev-alb-logs-123456789012"
    enable_waf              = false
    stage_path_prefix       = "v1"
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

  assert {
    condition = toset(flatten([
      for rule in aws_lb_listener_rule.service : one(one(rule.condition).path_pattern).values
      ])) == toset([
      "/api/v1/lancamentos*",
      "/v1/api/v1/lancamentos*",
      "/api/v1/consolidados*",
      "/v1/api/v1/consolidados*",
    ])
    error_message = "Com stage, o listener aceita o path do recurso e o path com o prefixo /v1."
  }

  assert {
    condition = alltrue([
      for rule in aws_lb_listener_rule.service :
      length(rule.transform) == 1 && one(rule.transform).type == "url-rewrite"
    ])
    error_message = "O prefixo do stage e removido por url-rewrite antes da task."
  }
}

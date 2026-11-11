mock_provider "aws" {
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

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

run "pool_padrao" {
  command = plan

  variables {
    name_prefix         = "fluxo-caixa-dev"
    deletion_protection = "INACTIVE"
    log_retention_days  = 7
    logs_kms_key_arn    = "arn:aws:kms:us-east-1:123456789012:key/logs"
  }

  assert {
    condition     = aws_cognito_user_pool.this.user_pool_tier == "ESSENTIALS"
    error_message = "O pool fica no plano Essentials, que permite customizar o access token."
  }

  assert {
    condition     = one(one(aws_cognito_user_pool.this.lambda_config).pre_token_generation_config).lambda_version == "V2_0"
    error_message = "O pre-token usa a versao V2_0, que aceita scopesToAdd."
  }

  assert {
    condition     = aws_cognito_user_pool_domain.this.domain == "fluxo-caixa-dev-123456789012"
    error_message = "Sem prefixo explicito, o dominio e <name_prefix>-<account_id>."
  }

  assert {
    condition     = aws_lambda_function.pre_token.environment[0].variables["TOKEN_SCOPES"] == "fluxo-caixa/lancamentos:write fluxo-caixa/lancamentos:read fluxo-caixa/consolidado:read"
    error_message = "A Lambda recebe os tres escopos do resource server pela variavel TOKEN_SCOPES."
  }

  assert {
    condition = toset([
      for item in aws_cognito_resource_server.fluxo_caixa.scope : item.scope_name
    ]) == toset(["lancamentos:write", "lancamentos:read", "consolidado:read"])
    error_message = "O resource server publica os tres escopos do fluxo de caixa."
  }
}

run "prefixo_explicito" {
  command = plan

  variables {
    name_prefix         = "fluxo-caixa-dev"
    domain_prefix       = "fluxo-caixa-lab"
    deletion_protection = "INACTIVE"
    log_retention_days  = 7
    logs_kms_key_arn    = "arn:aws:kms:us-east-1:123456789012:key/logs"
  }

  assert {
    condition     = aws_cognito_user_pool_domain.this.domain == "fluxo-caixa-lab"
    error_message = "domain_prefix substitui o prefixo calculado."
  }
}

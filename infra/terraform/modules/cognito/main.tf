data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  region                     = data.aws_region.current.region
  resource_server_identifier = "fluxo-caixa"
  # O pool so tem comerciante. Todo access token recebe os tres escopos.
  scope_names = [
    "lancamentos:write",
    "lancamentos:read",
    "consolidado:read",
  ]
  scope_descriptions = {
    "lancamentos:write" = "Registrar e estornar lancamentos"
    "lancamentos:read"  = "Consultar lancamentos"
    "consolidado:read"  = "Consultar o saldo consolidado"
  }
  token_scopes  = [for name in local.scope_names : "${local.resource_server_identifier}/${name}"]
  domain_prefix = coalesce(var.domain_prefix, "${var.name_prefix}-${data.aws_caller_identity.current.account_id}")
}

data "archive_file" "pre_token" {
  type        = "zip"
  source_file = "${path.module}/lambda/pre_token_generation.py"
  output_path = "${path.module}/.build/pre_token_generation.zip"
}

resource "aws_cloudwatch_log_group" "pre_token" {
  name              = "/aws/lambda/${var.name_prefix}-pre-token"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.logs_kms_key_arn
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "pre_token" {
  name               = "${var.name_prefix}-pre-token"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "pre_token" {
  statement {
    sid = "Logs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.pre_token.arn}:*"]
  }

  statement {
    sid = "CifrarLogs"
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey",
    ]
    resources = [var.logs_kms_key_arn]
  }

  # X-Ray nao suporta resource-level permission.
  statement {
    sid = "XRay"
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "pre_token" {
  name   = "pre-token"
  role   = aws_iam_role.pre_token.id
  policy = data.aws_iam_policy_document.pre_token.json
}

resource "aws_lambda_function" "pre_token" {
  function_name = "${var.name_prefix}-pre-token"
  description   = "Pre Token Generation v2: claim comerciante_id e escopos do resource server no access token."
  role          = aws_iam_role.pre_token.arn
  runtime       = "python3.12"
  handler       = "pre_token_generation.handler"
  architectures = ["arm64"]
  timeout       = 5
  memory_size   = 128

  filename         = data.archive_file.pre_token.output_path
  source_code_hash = data.archive_file.pre_token.output_base64sha256

  environment {
    variables = {
      TOKEN_SCOPES = join(" ", local.token_scopes)
    }
  }

  reserved_concurrent_executions = 10

  tracing_config {
    mode = "Active"
  }

  depends_on = [
    aws_cloudwatch_log_group.pre_token,
    aws_iam_role_policy.pre_token,
  ]
}

# source_arn com curinga evita ciclo: o user pool referencia a Lambda e a permissao referencia o pool.
resource "aws_lambda_permission" "cognito" {
  statement_id  = "PermitirCognito"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pre_token.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = "arn:aws:cognito-idp:${local.region}:${data.aws_caller_identity.current.account_id}:userpool/*"
}

resource "aws_cognito_user_pool" "this" {
  name                     = "${var.name_prefix}-users"
  user_pool_tier           = "ESSENTIALS"
  deletion_protection      = var.deletion_protection
  auto_verified_attributes = []
  mfa_configuration        = "OFF"

  username_configuration {
    case_sensitive = false
  }

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = false
    temporary_password_validity_days = 7
  }

  # Plano Essentials e obrigatorio para customizar o access token (trigger v2).
  lambda_config {
    pre_token_generation_config {
      lambda_arn     = aws_lambda_function.pre_token.arn
      lambda_version = "V2_0"
    }
  }

  schema {
    name                     = "comerciante_id"
    attribute_data_type      = "String"
    mutable                  = true
    required                 = false
    developer_only_attribute = true

    string_attribute_constraints {
      min_length = 36
      max_length = 36
    }
  }

  # O Cognito devolve o schema completo depois do create e o plan fica ruidoso.
  lifecycle {
    ignore_changes = [schema]
  }

  tags = {
    Name = "${var.name_prefix}-users"
  }
}

resource "aws_cognito_user_pool_domain" "this" {
  domain       = local.domain_prefix
  user_pool_id = aws_cognito_user_pool.this.id

  lifecycle {
    precondition {
      condition     = can(regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$", local.domain_prefix))
      error_message = "O prefixo do dominio do Cognito precisa ter 1 a 63 caracteres, com minusculas, numeros e hifen."
    }
  }
}

resource "aws_cognito_resource_server" "fluxo_caixa" {
  identifier   = local.resource_server_identifier
  name         = local.resource_server_identifier
  user_pool_id = aws_cognito_user_pool.this.id

  dynamic "scope" {
    for_each = local.scope_names

    content {
      scope_name        = scope.value
      scope_description = local.scope_descriptions[scope.value]
    }
  }
}

resource "aws_cognito_user_pool_client" "app" {
  name         = "${var.name_prefix}-app"
  user_pool_id = aws_cognito_user_pool.this.id

  generate_secret               = false
  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = concat(["openid"], local.token_scopes)
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = var.callback_urls
  logout_urls                          = var.logout_urls

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  depends_on = [aws_cognito_resource_server.fluxo_caixa]
}

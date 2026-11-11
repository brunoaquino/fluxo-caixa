# Ephemeral: o sorteio do bootstrap nao entra no state. secret_string_wo_version
# so reenvia o valor quando esse numero muda. A rotacao da Lambda nao mexe nele.
ephemeral "random_password" "origin_verify" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "origin" {
  name                    = "${var.name_prefix}/origin-verify"
  description             = "Header X-Origin-Verify. current e o valor enviado pelo CloudFront. previous continua aceito no WAF ate a proxima rotacao."
  kms_key_id              = var.secret_kms_key_arn
  recovery_window_in_days = 7

  tags = {
    Name = "${var.name_prefix}-origin-verify"
  }
}

resource "aws_secretsmanager_secret_version" "origin" {
  secret_id                = aws_secretsmanager_secret.origin.id
  secret_string_wo_version = 1
  secret_string_wo = jsonencode({
    current  = ephemeral.random_password.origin_verify.result
    previous = ephemeral.random_password.origin_verify.result
  })
}

# AWSCURRENT, nao a versao que o Terraform criou. Depois da rotacao o plan
# acompanha current/previous vivos. No primeiro apply a leitura espera a versao
# existir, sem ciclo: o data source depende da versao, a versao nao depende dele.
data "aws_secretsmanager_secret_version" "origin" {
  secret_id     = aws_secretsmanager_secret.origin.id
  version_stage = "AWSCURRENT"

  depends_on = [aws_secretsmanager_secret_version.origin]
}

data "archive_file" "rotate" {
  type        = "zip"
  source_file = "${path.module}/lambda/rotate_origin_verify.py"
  output_path = "${path.module}/.build/rotate_origin_verify.zip"
}

resource "aws_cloudwatch_log_group" "rotate" {
  name              = "/aws/lambda/${var.name_prefix}-origin-verify"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.logs_kms_key_arn
}

data "aws_iam_policy_document" "rotate_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "rotate" {
  name               = "${var.name_prefix}-origin-verify"
  assume_role_policy = data.aws_iam_policy_document.rotate_assume.json
}

data "aws_iam_policy_document" "rotate" {
  statement {
    sid = "LerEGravarSegredo"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecretVersionStage",
    ]
    resources = [aws_secretsmanager_secret.origin.arn]
  }

  statement {
    sid = "CifrarSegredo"
    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey",
    ]
    resources = [var.secret_kms_key_arn]
  }

  statement {
    sid = "Logs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.rotate.arn}:*"]
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

  statement {
    sid = "WafDaApi"
    actions = [
      "wafv2:GetWebACL",
      "wafv2:UpdateWebACL",
    ]
    resources = [aws_wafv2_web_acl.regional.arn]
  }

  # ListDistributions nao aceita recurso especifico.
  statement {
    sid       = "ListarDistribuicoes"
    actions   = ["cloudfront:ListDistributions"]
    resources = ["*"]
  }

  statement {
    sid = "AtualizarOrigin"
    actions = [
      "cloudfront:GetDistribution",
      "cloudfront:GetDistributionConfig",
      "cloudfront:UpdateDistribution",
      "cloudfront:ListTagsForResource",
    ]
    resources = ["arn:aws:cloudfront::${local.account_id}:distribution/*"]
  }

  statement {
    sid = "XRay"
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "rotate" {
  name   = "origin-verify"
  role   = aws_iam_role.rotate.id
  policy = data.aws_iam_policy_document.rotate.json
}

resource "aws_lambda_function" "rotate" {
  function_name = "${var.name_prefix}-origin-verify"
  description   = "Rotaciona X-Origin-Verify no Secrets Manager, no CloudFront e no WAF regional."
  role          = aws_iam_role.rotate.arn
  runtime       = "python3.12"
  handler       = "rotate_origin_verify.handler"
  architectures = ["arm64"]
  timeout       = 120
  memory_size   = 256

  filename         = data.archive_file.rotate.output_path
  source_code_hash = data.archive_file.rotate.output_base64sha256

  reserved_concurrent_executions = 2

  environment {
    variables = {
      DISTRIBUTION_TAG_NAME = "${var.name_prefix}-cdn"
      WEB_ACL_NAME          = aws_wafv2_web_acl.regional.name
      WEB_ACL_ID            = aws_wafv2_web_acl.regional.id
      WEB_ACL_SCOPE         = "REGIONAL"
      HEADER_NAME           = "X-Origin-Verify"
      ORIGIN_ID             = "api-gateway"
    }
  }

  tracing_config {
    mode = "Active"
  }

  depends_on = [
    aws_cloudwatch_log_group.rotate,
    aws_iam_role_policy.rotate,
  ]
}

resource "aws_lambda_permission" "secrets" {
  statement_id  = "SecretsManagerRotation"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.rotate.function_name
  principal     = "secretsmanager.amazonaws.com"
  source_arn    = aws_secretsmanager_secret.origin.arn
}

resource "aws_secretsmanager_secret_rotation" "origin" {
  secret_id           = aws_secretsmanager_secret.origin.id
  rotation_lambda_arn = aws_lambda_function.rotate.arn
  rotate_immediately  = false

  rotation_rules {
    automatically_after_days = var.rotation_days
  }

  depends_on = [aws_lambda_permission.secrets]
}

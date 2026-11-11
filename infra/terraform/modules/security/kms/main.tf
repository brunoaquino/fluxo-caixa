data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id

  # extra seleciona os service principals que precisam estar na key policy.
  # RDS, ElastiCache e ECR recebem grant via o root da conta; nao listamos o servico
  # para nao bloquear CreateGrant com uma condition estreita demais.
  keys = {
    rds-lancamentos = {
      description = "Storage e Performance Insights do RDS de lancamentos."
      extra       = "none"
    }
    rds-consolidado = {
      description = "Storage e Performance Insights do RDS de consolidado."
      extra       = "none"
    }
    rds-secret-lancamentos = {
      description = "Secret da senha mestre do RDS de lancamentos."
      extra       = "secrets"
    }
    rds-secret-consolidado = {
      description = "Secret da senha mestre do RDS de consolidado."
      extra       = "secrets"
    }
    elasticache = {
      description = "ElastiCache Valkey do consolidado."
      extra       = "none"
    }
    messaging = {
      description = "SNS, SQS, DLQ e topico de alertas."
      extra       = "messaging"
    }
    logs = {
      description = "Log groups do CloudWatch."
      extra       = "logs"
    }
    alb-logs = {
      description = "Access logs do ALB."
      extra       = "alb-logs"
    }
    ecr = {
      description = "Imagens no ECR."
      extra       = "none"
    }
    origin-verify = {
      description = "Segredo do header X-Origin-Verify entre CloudFront e API Gateway."
      extra       = "secrets"
    }
  }
}

data "aws_iam_policy_document" "key" {
  for_each = local.keys

  statement {
    sid       = "AdministracaoViaIAM"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${local.account_id}:root"]
    }
  }

  dynamic "statement" {
    for_each = each.value.extra == "logs" ? [1] : []

    content {
      sid = "CloudWatchLogs"
      actions = [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:ReEncrypt*",
        "kms:GenerateDataKey*",
        "kms:DescribeKey",
      ]
      resources = ["*"]

      principals {
        type = "Service"
        identifiers = [
          "logs.${var.region}.amazonaws.com",
          "delivery.logs.amazonaws.com",
        ]
      }

      condition {
        test     = "ArnLike"
        variable = "kms:EncryptionContext:aws:logs:arn"
        values   = ["arn:aws:logs:${var.region}:${local.account_id}:*"]
      }
    }
  }

  dynamic "statement" {
    for_each = each.value.extra == "secrets" ? [1] : []

    content {
      sid = "SecretsManager"
      actions = [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:ReEncrypt*",
        "kms:GenerateDataKey*",
        "kms:DescribeKey",
        "kms:CreateGrant",
      ]
      resources = ["*"]

      principals {
        type        = "Service"
        identifiers = ["secretsmanager.amazonaws.com"]
      }

      condition {
        test     = "StringEquals"
        variable = "kms:ViaService"
        values   = ["secretsmanager.${var.region}.amazonaws.com"]
      }

      condition {
        test     = "StringEquals"
        variable = "aws:SourceAccount"
        values   = [local.account_id]
      }
    }
  }

  dynamic "statement" {
    for_each = each.value.extra == "alb-logs" ? [1] : []

    content {
      sid = "EntregaDeAccessLogs"
      actions = [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:ReEncrypt*",
        "kms:GenerateDataKey*",
        "kms:DescribeKey",
      ]
      resources = ["*"]

      principals {
        type        = "Service"
        identifiers = ["logdelivery.elasticloadbalancing.amazonaws.com"]
      }

      condition {
        test     = "StringEquals"
        variable = "aws:SourceAccount"
        values   = [local.account_id]
      }

      condition {
        test     = "ArnLike"
        variable = "aws:SourceArn"
        values   = ["arn:aws:elasticloadbalancing:${var.region}:${local.account_id}:loadbalancer/*"]
      }
    }
  }

  dynamic "statement" {
    for_each = each.value.extra == "messaging" ? [1] : []

    content {
      sid = "MensageriaEAlertas"
      actions = [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:ReEncrypt*",
        "kms:GenerateDataKey*",
        "kms:DescribeKey",
      ]
      resources = ["*"]

      principals {
        type = "Service"
        identifiers = [
          "sns.amazonaws.com",
          "sqs.amazonaws.com",
          "cloudwatch.amazonaws.com",
          "events.amazonaws.com",
        ]
      }

      condition {
        test     = "StringEquals"
        variable = "aws:SourceAccount"
        values   = [local.account_id]
      }
    }
  }
}

resource "aws_kms_key" "this" {
  for_each = local.keys

  description             = each.value.description
  deletion_window_in_days = var.deletion_window_days
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.key[each.key].json
}

resource "aws_kms_alias" "this" {
  for_each = aws_kms_key.this

  name          = "alias/${var.name_prefix}-${each.key}"
  target_key_id = each.value.key_id
}

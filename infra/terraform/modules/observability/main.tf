data "aws_caller_identity" "current" {}

locals {
  log_names = setunion(keys(var.services), ["metrics"])
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = local.log_names

  name              = "/ecs/${var.name_prefix}/${each.key}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.logs_kms_key_arn

  tags = {
    Name = "/ecs/${var.name_prefix}/${each.key}"
  }
}

resource "aws_sns_topic" "alerts" {
  name              = "${var.name_prefix}-alertas"
  kms_master_key_id = var.alerts_kms_key_arn

  tags = {
    Name = "${var.name_prefix}-alertas"
  }
}

data "aws_iam_policy_document" "alerts" {
  statement {
    sid       = "PublicarAlarmes"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alerts.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }

  statement {
    sid = "OperacaoDaConta"
    actions = [
      "sns:GetTopicAttributes",
      "sns:SetTopicAttributes",
      "sns:Subscribe",
      "sns:Unsubscribe",
      "sns:ListSubscriptionsByTopic",
      "sns:DeleteTopic",
    ]
    resources = [aws_sns_topic.alerts.arn]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts.json
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alert_email == "" ? 0 : 1

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

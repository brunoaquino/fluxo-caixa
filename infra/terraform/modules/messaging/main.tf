data "aws_caller_identity" "current" {}

locals {
  account_root = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
}

resource "aws_sns_topic" "lancamentos" {
  name              = var.topic_name
  kms_master_key_id = var.kms_key_arn

  tags = {
    Name = var.topic_name
  }
}

resource "aws_sqs_queue" "dlq" {
  name                              = var.dlq_name
  message_retention_seconds         = 1209600
  kms_master_key_id                 = var.kms_key_arn
  kms_data_key_reuse_period_seconds = 300

  tags = {
    Name = var.dlq_name
  }
}

resource "aws_sqs_queue" "consolidado" {
  name                              = var.queue_name
  visibility_timeout_seconds        = 60
  message_retention_seconds         = 1209600
  receive_wait_time_seconds         = 20
  kms_master_key_id                 = var.kms_key_arn
  kms_data_key_reuse_period_seconds = 300

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  tags = {
    Name = var.queue_name
  }
}

resource "aws_sns_topic_subscription" "consolidado" {
  topic_arn            = aws_sns_topic.lancamentos.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.consolidado.arn
  raw_message_delivery = true
}

data "aws_iam_policy_document" "topic" {
  statement {
    sid       = "PublicarLancamentos"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.lancamentos.arn]

    principals {
      type        = "AWS"
      identifiers = [var.publisher_role_arn]
    }
  }

  # O root permanece para o Terraform e para a operacao da conta.
  # A aplicacao nao usa o root: a task role e a unica com sns:Publish no IAM.
  # Unsubscribe nao entra: a policy do topico rejeita essa action.
  statement {
    sid = "OperacaoDaConta"
    actions = [
      "sns:GetTopicAttributes",
      "sns:SetTopicAttributes",
      "sns:ListSubscriptionsByTopic",
      "sns:Subscribe",
      "sns:DeleteTopic",
    ]
    resources = [aws_sns_topic.lancamentos.arn]

    principals {
      type        = "AWS"
      identifiers = [local.account_root]
    }
  }
}

resource "aws_sns_topic_policy" "lancamentos" {
  arn    = aws_sns_topic.lancamentos.arn
  policy = data.aws_iam_policy_document.topic.json
}

data "aws_iam_policy_document" "queue" {
  statement {
    sid       = "EntregaDoSNS"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.consolidado.arn]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.lancamentos.arn]
    }
  }

  statement {
    sid = "ConsumoDoConsolidado"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.consolidado.arn]

    principals {
      type        = "AWS"
      identifiers = [var.consumer_role_arn]
    }
  }

  statement {
    sid = "OperacaoDaConta"
    actions = [
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:SetQueueAttributes",
      "sqs:PurgeQueue",
      "sqs:DeleteQueue",
    ]
    resources = [aws_sqs_queue.consolidado.arn]

    principals {
      type        = "AWS"
      identifiers = [local.account_root]
    }
  }
}

resource "aws_sqs_queue_policy" "consolidado" {
  queue_url = aws_sqs_queue.consolidado.id
  policy    = data.aws_iam_policy_document.queue.json
}

data "aws_iam_policy_document" "dlq" {
  statement {
    sid       = "RedriveDaFilaPrincipal"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq.arn]

    principals {
      type        = "Service"
      identifiers = ["sqs.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sqs_queue.consolidado.arn]
    }
  }

  statement {
    sid = "OperacaoDaConta"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:SetQueueAttributes",
      "sqs:PurgeQueue",
      "sqs:DeleteQueue",
    ]
    resources = [aws_sqs_queue.dlq.arn]

    principals {
      type        = "AWS"
      identifiers = [local.account_root]
    }
  }
}

resource "aws_sqs_queue_policy" "dlq" {
  queue_url = aws_sqs_queue.dlq.id
  policy    = data.aws_iam_policy_document.dlq.json
}

mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
      arn        = "arn:aws:iam::123456789012:root"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_resource "aws_sns_topic" {
    defaults = {
      arn = "arn:aws:sns:us-east-1:123456789012:fluxo-caixa-dev-lancamentos-events"
      id  = "arn:aws:sns:us-east-1:123456789012:fluxo-caixa-dev-lancamentos-events"
    }
  }

  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:us-east-1:123456789012:fluxo-caixa-dev-consolidado-lancamentos-dlq"
      id  = "https://sqs.us-east-1.amazonaws.com/123456789012/fluxo-caixa-dev-consolidado-lancamentos-dlq"
      url = "https://sqs.us-east-1.amazonaws.com/123456789012/fluxo-caixa-dev-consolidado-lancamentos-dlq"
    }
  }
}

run "dlq_max_receive_count" {
  command = apply

  variables {
    topic_name         = "fluxo-caixa-dev-lancamentos-events"
    queue_name         = "fluxo-caixa-dev-consolidado-lancamentos-queue"
    dlq_name           = "fluxo-caixa-dev-consolidado-lancamentos-dlq"
    kms_key_arn        = "arn:aws:kms:us-east-1:123456789012:key/messaging"
    publisher_role_arn = "arn:aws:iam::123456789012:role/fluxo-caixa-dev-lancamentos-task"
    consumer_role_arn  = "arn:aws:iam::123456789012:role/fluxo-caixa-dev-consolidado-task"
  }

  assert {
    condition     = jsondecode(aws_sqs_queue.consolidado.redrive_policy).maxReceiveCount == 5
    error_message = "A DLQ deve receber a mensagem depois de maxReceiveCount = 5."
  }

  assert {
    condition     = aws_sqs_queue.consolidado.visibility_timeout_seconds == 60 && aws_sqs_queue.consolidado.receive_wait_time_seconds == 20
    error_message = "A fila precisa de visibility 60s e long polling de 20s."
  }
}

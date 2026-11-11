resource "aws_cloudwatch_event_rule" "rotation_failed" {
  name = "${var.name_prefix}-rotation-failed"
  description = trimspace(<<-EOT
    RotationFailed no Secrets Manager.
    ${join(" ", values(var.rotation_secret_arns))}
  EOT
  )

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS Secrets Manager Rotation Failed"]
  })
}

resource "aws_cloudwatch_event_target" "rotation_failed" {
  rule = aws_cloudwatch_event_rule.rotation_failed.name
  arn  = aws_sns_topic.alerts.arn
}

resource "aws_cloudwatch_event_rule" "rotation_abandoned" {
  name        = "${var.name_prefix}-rotation-abandoned"
  description = "RotationFailed ou RotationAbandoned via CloudTrail."

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS Service Event via CloudTrail"]
    detail = {
      eventSource = ["secretsmanager.amazonaws.com"]
      eventName   = ["RotationFailed", "RotationAbandoned"]
    }
  })
}

resource "aws_cloudwatch_event_target" "rotation_abandoned" {
  rule = aws_cloudwatch_event_rule.rotation_abandoned.name
  arn  = aws_sns_topic.alerts.arn
}

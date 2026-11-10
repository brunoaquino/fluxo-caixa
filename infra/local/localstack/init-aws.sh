#!/usr/bin/env bash
# Cria o topico, a fila, a DLQ e a subscription com raw delivery.
# Roda dentro do LocalStack quando os servicos SNS/SQS ficam prontos.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-sa-east-1}"
TOPIC_NAME="fluxo-caixa-lancamentos-events"
QUEUE_NAME="consolidado-lancamentos-queue"
DLQ_NAME="consolidado-lancamentos-dlq"

TOPIC_ARN="$(awslocal sns create-topic --name "${TOPIC_NAME}" --region "${REGION}" --query TopicArn --output text)"
DLQ_URL="$(awslocal sqs create-queue --queue-name "${DLQ_NAME}" --region "${REGION}" --query QueueUrl --output text)"
QUEUE_URL="$(awslocal sqs create-queue --queue-name "${QUEUE_NAME}" --region "${REGION}" --query QueueUrl --output text)"

DLQ_ARN="$(awslocal sqs get-queue-attributes --queue-url "${DLQ_URL}" --attribute-names QueueArn --region "${REGION}" --query Attributes.QueueArn --output text)"
QUEUE_ARN="$(awslocal sqs get-queue-attributes --queue-url "${QUEUE_URL}" --attribute-names QueueArn --region "${REGION}" --query Attributes.QueueArn --output text)"

export TOPIC_ARN QUEUE_ARN DLQ_ARN
python3 - <<'PY'
import json
import os

policy = {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "EntregaDoSNS",
            "Effect": "Allow",
            "Principal": {"Service": "sns.amazonaws.com"},
            "Action": "sqs:SendMessage",
            "Resource": os.environ["QUEUE_ARN"],
            "Condition": {"ArnEquals": {"aws:SourceArn": os.environ["TOPIC_ARN"]}},
        }
    ],
}
attributes = {
    "Policy": json.dumps(policy),
    "VisibilityTimeout": "60",
    "MessageRetentionPeriod": "1209600",
    "ReceiveMessageWaitTimeSeconds": "20",
    "RedrivePolicy": json.dumps(
        {
            "deadLetterTargetArn": os.environ["DLQ_ARN"],
            "maxReceiveCount": "5",
        }
    ),
}
with open("/tmp/consolidado-queue-attributes.json", "w", encoding="utf-8") as handle:
    json.dump(attributes, handle)
PY

awslocal sqs set-queue-attributes \
  --queue-url "${QUEUE_URL}" \
  --attributes "file:///tmp/consolidado-queue-attributes.json" \
  --region "${REGION}"

awslocal sqs set-queue-attributes \
  --queue-url "${DLQ_URL}" \
  --attributes MessageRetentionPeriod=1209600 \
  --region "${REGION}"

EXISTING="$(awslocal sns list-subscriptions-by-topic \
  --topic-arn "${TOPIC_ARN}" \
  --region "${REGION}" \
  --query "Subscriptions[?Endpoint=='${QUEUE_ARN}'].SubscriptionArn" \
  --output text || true)"

if [[ -z "${EXISTING}" || "${EXISTING}" == "None" ]]; then
  awslocal sns subscribe \
    --topic-arn "${TOPIC_ARN}" \
    --protocol sqs \
    --notification-endpoint "${QUEUE_ARN}" \
    --attributes RawMessageDelivery=true \
    --region "${REGION}"
fi

echo "LocalStack pronto: ${TOPIC_ARN} -> ${QUEUE_URL} (DLQ ${DLQ_ARN}, maxReceiveCount=5, raw delivery)"

# messaging

SNS, SQS e DLQ. Raw delivery e maxReceiveCount 5.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "messaging" {
  source = "./modules/messaging"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `topic_name` | `string` | Nome do topico SNS. No ambiente local o nome e fixo; na AWS leva o prefixo do ambiente. |
| `queue_name` | `string` | Fila consumida pelo consolidado. |
| `dlq_name` | `string` | DLQ da fila do consolidado. |
| `kms_key_arn` | `string` | CMK de SNS e SQS. |
| `publisher_role_arn` | `string` | Task role de lancamentos. ARN montado pelo caller para nao criar ciclo com o modulo ECS. |
| `consumer_role_arn` | `string` | Task role de consolidado. |
| `max_receive_count` | `number` | Tentativas antes da DLQ. |

## Outputs

| Nome | Descricao |
|---|---|
| `topic_arn` | ARN do topico de lancamentos. LANCAMENTOS_TOPIC_ARN. |
| `topic_name` | Nome do topico. |
| `queue_url` | URL da fila do consolidado. CONSOLIDADO_QUEUE_URL. |
| `queue_arn` | ARN da fila do consolidado. |
| `queue_name` | Nome da fila, usado nos alarmes e no scaling. |
| `dlq_arn` | ARN da DLQ. |
| `dlq_name` | Nome da DLQ. |
| `dlq_url` | URL da DLQ. |

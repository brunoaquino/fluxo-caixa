# observability

Logs, dashboard, alarmes e eventos de falha de rotacao.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "observability" {
  source = "./modules/observability"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo dos log groups, do dashboard e dos alarmes. |
| `region` | `string` | Regiao do dashboard. |
| `log_retention_days` | `number` | Retencao dos logs de aplicacao e de metricas EMF. |
| `logs_kms_key_arn` | `string` | CMK dos log groups. |
| `alerts_kms_key_arn` | `string` | CMK do topico de alertas. A key policy precisa autorizar o CloudWatch. |
| `alert_email` | `string` | E-mail inscrito no topico. Vazio nao cria inscricao. A AWS envia um e-mail de confirmacao. |
| `cluster_name` | `string` | Cluster ECS das metricas de CPU e memoria. |
| `alb_arn_suffix` | `string` | Sufixo do ALB. |
| `services` | `map(object({` | Servicos monitorados. O nome da task ECS precisa bater com ecs_service_name. |
| `queue_name` | `string` | Fila principal do consolidado. |
| `dlq_name` | `string` | DLQ do consolidado. |
| `queue_visible_threshold` | `number` | Alarme de profundidade da fila. |
| `queue_age_threshold_seconds` | `number` | Alarme da idade da mensagem mais antiga. Acima do SLO de 5s para nao disparar em atraso curto. |
| `rotation_secret_arns` | `map(string)` | Secrets de banco cuja falha de rotacao deve ir para o topico de alertas. O mapa entra na descricao da regra para o apply esperar o secret existir. |
| `rds_instances` | `map(object({` | Instancias RDS monitoradas. |

## Outputs

| Nome | Descricao |
|---|---|
| `log_group_names` | Nomes dos log groups, incluindo metrics. |
| `log_group_arns` | ARNs dos log groups. |
| `alert_topic_arn` | Topico SNS dos alarmes. |
| `dashboard_name` | Nome do dashboard operacional. |

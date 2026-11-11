# ecs-service

Task ARM64, ADOT, security group minimo, scaling e circuit breaker.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "ecs_service" {
  source = "./modules/ecs-service"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name` | `string` | Nome do servico ECS e prefixo das roles. Ex.: fluxo-caixa-dev-lancamentos. |
| `container_name` | `string` | Nome do container da aplicacao. O CD usa este nome no render da task definition. |
| `cluster_arn` | `string` | ARN do cluster. |
| `cluster_name` | `string` | Nome do cluster, usado no scaling da fila. |
| `vpc_id` | `string` | VPC das tasks. |
| `vpc_cidr` | `string` | CIDR da VPC, liberado apenas para DNS. |
| `subnet_ids` | `list(string)` | Subnets de aplicacao. Uma task por AZ quando desired_count acompanha a quantidade de AZs. |
| `container_image` | `string` | Imagem no ECR, com tag imutavel. |
| `container_port` | `number` | Porta HTTP do Spring Boot. |
| `cpu` | `number` | CPU da task Fargate. 256, 512, 1024... |
| `memory` | `number` | Memoria da task em MiB, compativel com a CPU. |
| `desired_count` | `number` | Tasks iniciais. O autoscaling assume o valor depois; o Terraform ignora mudancas posteriores. |
| `min_capacity` | `number` | Minimo do autoscaling. Em prod, 2 para espalhar entre AZs. |
| `max_capacity` | `number` | Maximo do autoscaling. |
| `environment` | `map(string)` | Variaveis da aplicacao, sem segredo. O Boot nao le `OTEL_PROPAGATORS`; o bean Java compoe W3C e X-Ray. O sidecar ADOT continua com `tracecontext,baggage,xray`. |
| `secrets` | `list(object({` | Segredos injetados pelo ECS a partir do Secrets Manager. valueFrom pode apontar para uma chave JSON. |
| `secret_arns` | `list(string)` | ARNs puros dos secrets, para o IAM da execution role. |
| `execution_kms_key_arns` | `list(string)` | CMKs que a execution role precisa decifrar (secret do RDS e logs). |
| `task_policy_statements` | `list(object({` | Permissoes extras da task role. Lancamentos publica no SNS; consolidado consome a fila. |
| `log_group_name` | `string` | Log group da aplicacao e do sidecar. |
| `log_group_arn` | `string` | ARN do log group da aplicacao. |
| `metrics_log_group_name` | `string` | Log group das metricas EMF. |
| `metrics_log_group_arn` | `string` | ARN do log group de metricas. |
| `aws_region` | `string` | Regiao usada pelo awslogs e pelo ADOT. |
| `target_group_arn` | `string` | Target group do ALB. |
| `alb_security_group_id` | `string` | Security group do ALB. |
| `vpc_endpoint_security_group_id` | `string` | Security group dos interface endpoints. Nulo abre HTTPS 443 para a internet (NAT), usado em dev. |
| `capacity_provider_strategy` | `list(object({` | Capacity providers da task. Dev usa FARGATE_SPOT. Hml e prod usam FARGATE. |
| `task_secret_arns` | `list(string)` | Secrets que a task role le em runtime (AWS JDBC Wrapper). Nao vao para a execution role. |
| `task_secret_kms_key_arns` | `list(string)` | CMKs dos secrets lidos pela task role. A policy concede apenas kms:Decrypt. |
| `data_plane_access` | `list(object({` | Saida para RDS ou Redis do proprio servico. O modulo tambem abre o ingress no SG de destino. |
| `readonly_root_filesystem` | `bool` | Filesystem raiz somente leitura, com /tmp montado a parte. No Fargate esse volume nasce como root; a imagem faz chmod 1777 e desce para o usuario app. |
| `enable_execute_command` | `bool` | Liga o ECS Exec. Util em dev, desligado em prod. |
| `enable_request_count_scaling` | `bool` | Target tracking de ALBRequestCountPerTarget. |
| `request_count_target` | `number` | Requisicoes por minuto por task. 1500 equivale a cerca de 25 req/s. |
| `alb_resource_label` | `string` | arn_suffix do ALB + / + arn_suffix do target group. |
| `cpu_target` | `number` | Percentual de CPU alvo do target tracking. |
| `queue_name` | `string` | Fila usada no scaling por backlog. Nulo desliga esse scaling. |
| `queue_backlog_per_task` | `number` | Mensagens visiveis por task. Nulo desliga o scaling da fila. |
| `adot_image` | `string` | Imagem do ADOT collector, com tag fixa e suporte a ARM64. |
| `health_check_grace_period_seconds` | `number` | Tempo para o Spring Boot subir antes do ALB contar falha de readiness. |

## Outputs

| Nome | Descricao |
|---|---|
| `service_name` | Nome do servico ECS. |
| `service_arn` | ARN do servico ECS. |
| `task_definition_arn` | ARN da task definition gerenciada pelo Terraform. |
| `task_role_arn` | Task role. O nome e previsivel para a policy de SNS/SQS. |
| `task_role_name` | Nome da task role. |
| `security_group_id` | Security group das tasks. |

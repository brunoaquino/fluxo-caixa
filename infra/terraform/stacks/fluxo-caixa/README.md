# fluxo-caixa

Composicao da plataforma. Os ambientes so passam sizing e identidade.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "fluxo_caixa" {
  source = "./stacks/fluxo-caixa"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `environment` | `string` | Ambiente. Entra no prefixo fluxo-caixa-<env>-<recurso>. |
| `aws_region` | `string` | Regiao da carga. A borda CloudFront e WAF continua em us-east-1. |
| `alert_email` | `string` | E-mail dos alarmes. Vazio deixa o topico sem inscricao. |
| `image_tag` | `string` | Tag imutavel publicada no ECR. |
| `acm_certificate_arn` | `string` | Certificado ACM us-east-1 do CloudFront. Nulo usa *.cloudfront.net. |
| `domain_names` | `list(string)` | Aliases do CloudFront. Vazio junto com acm_certificate_arn nulo. |
| `adot_image` | `string` | Imagem do ADOT collector, com tag fixa e suporte a ARM64. |
| `rds_engine_version` | `string` | Versao menor do PostgreSQL 17. |
| `redis_engine_version` | `string` | Versao do Valkey. |
| `lancamentos_request_count_target` | `number` | Requests por minuto por task de lancamentos. |
| `consolidado_request_count_target` | `number` | Requests por minuto por task do consolidado. 1500 cobre cerca de 25 req/s. |
| `consolidado_queue_backlog_per_task` | `number` | Mensagens visiveis por task antes de subir mais tasks. |
| `sizing` | `object({` | Sobrescreve o dimensionamento da secao 15. Atributo nulo herda o default do ambiente. |

## Outputs

| Nome | Descricao |
|---|---|
| `environment` | Ambiente deste state. |
| `cloudfront_domain_name` | Entrada publica. Use este hostname enquanto nao houver dominio proprio. |
| `cloudfront_distribution_id` | ID da distribuicao CloudFront. |
| `alb_dns_name` | DNS interno do ALB. Nao e a URL publica. |
| `ecr_repository_urls` | Repositorios ECR. |
| `cognito_user_pool_id` | User pool. |
| `cognito_user_pool_client_id` | App client. |
| `cognito_issuer_uri` | Issuer dos access tokens. |
| `lancamentos_topic_arn` | Topico SNS dos eventos de lancamento. |
| `consolidado_queue_url` | Fila consumida pelo consolidado. |
| `consolidado_dlq_url` | DLQ do consolidado. |
| `ecs_cluster_name` | Cluster ECS. |
| `rds_endpoints` | Hosts que a aplicacao usa. Proxy quando enable_rds_proxy. |
| `rds_reader_endpoints` | Hosts de leitura. Nulos enquanto read_replica_count for 0. |
| `rds_secret_arns` | Secrets que a task le via DB_SECRET_ARN. |
| `redis_endpoint` | Endpoint de escrita do Valkey. |
| `alert_topic_arn` | Topico dos alarmes e das falhas de rotacao. |
| `sizing` | Dimensionamento efetivo, depois dos defaults da secao 15 e das sobrescritas. |

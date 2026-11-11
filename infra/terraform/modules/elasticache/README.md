# elasticache

Valkey com TLS. Sem senha, porque o contrato nao tem REDIS_PASSWORD.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "elasticache" {
  source = "./modules/elasticache"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name` | `string` | replication_group_id. Maximo de 40 caracteres, sem underscore. |
| `description` | `string` | Descricao do replication group. |
| `engine_version` | `string` | Versao do Valkey. 8.x evita a licenca do Redis e atende o contrato REDIS_SSL. |
| `node_type` | `string` | Tipo do no. cache.t4g.micro em dev, cache.t4g.small em prod. |
| `num_cache_clusters` | `number` | Primario mais replicas. 1 em dev, 2 em prod. |
| `automatic_failover_enabled` | `bool` | Promove a replica se o primario cair. Exige num_cache_clusters >= 2. |
| `multi_az_enabled` | `bool` | Replica em outra AZ. Exige failover automatico. |
| `snapshot_retention_limit` | `number` | Dias de snapshot. 0 desliga. |
| `apply_immediately` | `bool` | Aplica mudancas na hora. |
| `kms_key_arn` | `string` | CMK da criptografia em repouso. |
| `subnet_ids` | `list(string)` | Subnets de dados. Pelo menos duas AZs, mesmo com um unico no. |
| `vpc_id` | `string` | VPC do security group. |

## Outputs

| Nome | Descricao |
|---|---|
| `primary_endpoint` | Endpoint de escrita. REDIS_HOST. |
| `port` | Porta. Com TLS continua 6379. |
| `security_group_id` | Security group do Redis. O consolidado abre o ingress. |

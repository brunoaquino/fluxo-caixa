# alb

ALB interno. O security group aceita so o prefix list do CloudFront.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "alb" {
  source = "./modules/alb"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo do ALB, dos target groups e do bucket de logs. |
| `vpc_id` | `string` | VPC do ALB. |
| `subnet_ids` | `list(string)` | Subnets privadas do ALB interno, uma por AZ. O CloudFront chega por VPC Origin. |
| `enable_waf` | `bool` | WAF regional no ALB. Padrao false: o ALB e interno e o WAF de borda (escopo CLOUDFRONT) ja inspeciona o cliente. Um WAF regional veria so o IP do CloudFront e cobraria de novo. |
| `deletion_protection` | `bool` | Protege o ALB contra delete. true em prod. |
| `access_logs_bucket_name` | `string` | Nome globalmente unico do bucket de access logs. O bucket usa SSE-S3: o ALB nao grava em bucket com CMK. |
| `access_log_expiration_days` | `number` | Dias ate expirar o access log. |
| `waf_rate_limit` | `number` | Requisicoes por IP em 5 minutos. 50 req/s sustentados geram 15000, entao o teto fica acima disso. |
| `services` | `map(object({` | Target groups e regras de caminho. |

## Outputs

| Nome | Descricao |
|---|---|
| `alb_arn` | ARN do ALB. |
| `alb_arn_suffix` | Sufixo usado em metricas e no scaling por request count. |
| `alb_dns_name` | DNS interno do ALB. O CloudFront usa este nome na VPC Origin. |
| `alb_zone_id` | Zone ID do ALB, para um alias no Route 53. |
| `alb_url` | URL HTTP interna do ALB. A URL publica e a do CloudFront. |
| `security_group_id` | Security group do ALB. |
| `target_group_arns` | ARNs dos target groups, indexados pelo servico. |
| `target_group_arn_suffixes` | Sufixos dos target groups. |
| `waf_arn` | ARN do Web ACL regional. Nulo quando enable_waf e false. |
| `access_logs_bucket` | Bucket dos access logs. |

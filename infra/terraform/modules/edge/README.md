# edge

CloudFront, VPC Origin, WAF CLOUDFRONT e headers de seguranca.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "edge" {
  source = "./modules/edge"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo dos recursos de borda. Ex.: fluxo-caixa-dev. |
| `alb_arn` | `string` | ARN do ALB interno usado como VPC Origin. |
| `alb_dns_name` | `string` | DNS do ALB interno. O CloudFront usa este hostname na origem. |
| `acm_certificate_arn` | `string` | Certificado ACM em us-east-1. Nulo usa o certificado padrao *.cloudfront.net. |
| `domain_names` | `list(string)` | Aliases da distribuicao. Vazio quando o certificado padrao do CloudFront e usado. |
| `logs_kms_key_arn` | `string` | CMK do log group do WAF. A chave precisa estar em us-east-1, mesma regiao do escopo CLOUDFRONT. |
| `log_retention_days` | `number` | Retencao dos logs do WAF. |
| `waf_rate_limit` | `number` | Requisicoes por IP em 5 minutos no WAF de borda. |
| `anonymous_ip_count_only` | `bool` | AnonymousIpList em count (dev) em vez de block. Count deixa o trafego passar e so mede. |

## Outputs

| Nome | Descricao |
|---|---|
| `distribution_id` | ID da distribuicao CloudFront. |
| `distribution_domain_name` | Dominio *.cloudfront.net. E a URL publica quando nao ha alias. |
| `distribution_arn` | ARN da distribuicao. |
| `waf_arn` | ARN do Web ACL de escopo CLOUDFRONT. |

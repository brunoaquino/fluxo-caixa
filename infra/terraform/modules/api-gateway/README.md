# api-gateway

REST API regional na frente do ALB interno. CloudFront é a URL pública. Este módulo é o authorizer, o throttle, o validator e a trava contra quem chama o `execute-api` direto.

terraform-docs não estava instalado. Esta página lista o contrato do módulo.

## O que cria

- REST API `REGIONAL`, stage `v1`, só as rotas da seção 7 do blueprint.
- Authorizer `COGNITO_USER_POOLS` com escopo por método.
- Models JSON Schema para o POST de lançamento e o POST de estorno.
- VPC Link v2 (`aws_apigatewayv2_vpc_link`) com `integration_target` no ARN do ALB. Timeout 10 s. Sem NLB.
- Usage plan e API key quando `api_consumers` não é vazio.
- WAF regional no stage: managed rules e a regra `x-origin-verify`.
- Segredo no Secrets Manager, CMK recebida de fora, Lambda de rotação.

## Integração privada

O provider 6.72 aceita VPC Link v2 na REST API. `connection_id` é o id do `aws_apigatewayv2_vpc_link`. `integration_target` é o ARN do ALB. A URI define o Host e o path, inclusive `{id}` e `{data}`.

A AWS inclui o nome do stage no path que chega ao backend. A composição liga `stage_path_prefix = "v1"` no ALB, e o listener reescreve `^/v1(/.*)$` para `$1`. Este módulo não faz essa reescrita.

O security group do VPC Link só sai na porta 80 para o security group do ALB. O módulo abre essa entrada no ALB.

## Rotação

O valor inicial nasce em `ephemeral.random_password` e entra no Secrets Manager por `secret_string_wo`. `secret_string_wo_version` fica em 1: a senha efêmera não é regravada a cada plan. CloudFront e o WAF não leem essa versão. Leem `data.aws_secretsmanager_secret_version` em `AWSCURRENT`, que depois da Lambda é o par vigente. Não há `ignore_changes` na distribuição nem na Web ACL.

O header continua no state da distribuição, o par continua no state do data source e nas strings da regra do WAF. O resource da versão do segredo não guarda `secret_string`.

## Estorno

O validator do POST de estorno é só de parâmetros. Corpo ausente passa. O model documenta `motivo` opcional; quem limita o tamanho é o serviço.

## Inputs principais

| Nome | Descrição |
|---|---|
| `name_prefix` | Prefixo. Ex.: `fluxo-caixa-dev`. |
| `alb_arn`, `alb_dns_name`, `alb_security_group_id` | ALB interno. |
| `subnet_ids` | Subnets do VPC Link, no mínimo duas. |
| `cognito_user_pool_arn` | Authorizer. |
| `scope_prefix` | Padrão `fluxo-caixa/`. |
| `secret_kms_key_arn` | CMK do header. |
| `stage_throttling`, `route_throttling` | Rate e burst. Fora de dev o consolidado precisa passar de 50 req/s. |
| `cache_enabled` | Padrão `false`. |
| `canary` | Nulo não cria. O canary usa o deployment corrente. |
| `api_key_required` | Mapa `METHOD /path` → bool. Padrão false. |
| `api_consumers` | Usage plan. Padrão vazio. |
| `rotation_days` | Agenda do header. |

## Outputs

| Nome | Descrição |
|---|---|
| `invoke_url` | URL regional do stage. Não é a URL pública. |
| `api_id` | ID da REST API. |
| `stage_name` | `v1`. |
| `regional_domain_name` | Host que o CloudFront usa como origem. |
| `origin_verify_header_value` | Valor `current`. Sensível. |
| `origin_verify_secret_arn` | ARN do segredo. |
| `api_key_ids` | IDs das chaves. O valor fica no state. |

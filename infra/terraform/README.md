# Terraform — fluxo de caixa na AWS

Terraform `~> 1.14` e provider `hashicorp/aws ~> 6.72`. Região padrão `us-east-1`.

O state de cada ambiente é remoto, no S3, com lock nativo (`use_lockfile = true`). Não há tabela DynamoDB: a partir do Terraform 1.10 o lock é um objeto `.tflock` no próprio bucket. DynamoDB só faria sentido em runtime anterior a 1.10, e cobraria uma tabela à parte sem ganho de isolamento.

## Arquitetura

```
Cliente
  -> CloudFront (PriceClass_All, HTTP/2 e HTTP/3, TLS 1.2_2021)
       WAF escopo CLOUDFRONT
       /api/v1/lancamentos* e /api/v1/consolidados* vao ao API Gateway
       o comportamento default devolve 404 na funcao, sem chamar a origem
  -> API Gateway REST regional (stage v1)
       authorizer Cognito com escopo por rota
       WAF regional exige X-Origin-Verify (valor atual ou anterior)
       VPC Link v2, HTTP_PROXY, timeout 10 s
  -> ALB interno, sem IP publico
       security group aceita o prefix list do CloudFront e o security group do VPC Link
       rewrite tira o prefixo /v1 que a integracao privada acrescenta
         /api/v1/lancamentos*  -> ECS lancamentos
         /api/v1/consolidados* -> ECS consolidado
  -> tasks Fargate ARM64 nas subnets de aplicacao
       lancamentos -> RDS proprio + SNS
       consolidado -> RDS proprio + SQS + Valkey
  SNS (raw delivery) -> SQS -> DLQ depois de 5 recebimentos
```

`enable_api_gateway = false` tira o gateway, o WAF regional e o VPC Link. O CloudFront volta a usar a VPC Origin no ALB, como na seção 14 do blueprint.

O TLS da internet termina no CloudFront. O trecho API Gateway → ALB é HTTP na VPC: o hostname interno do ALB não cabe num certificado ACM público. A origem do CloudFront para o gateway é HTTPS, com `AllViewerExceptHostHeader`, para o `Host` ser o do `execute-api`. O header `X-Origin-Verify` é custom header da origem e substitui o valor que o cliente mandar.

O provider 6.72 aceita REST API com VPC Link v2 direto no ALB (`aws_apigatewayv2_vpc_link` + `integration_target`). Não há NLB.

A task não recebe `DB_USERNAME` nem `DB_PASSWORD`. Recebe `DB_SECRET_ARN` e `DB_URL=jdbc:postgresql://<host>:5432/<db>`. O serviço acrescenta um único prefixo `jdbc:aws-wrapper:` quando o segredo existe. Se o host for o do RDS Proxy (`.proxy-`), o plugin `failover` fica de fora. A task role lê só esse ARN (`GetSecretValue`, `DescribeSecret`) e decifra a CMK do segredo. `DB_READER_URL` usa o mesmo esquema e só entra quando existe reader.

O sidecar ADOT entrega trace no X-Ray. A aplicação propaga `tracecontext` (W3C) e `xray`.

## Árvore

```
infra/terraform
├── bootstrap/                 bucket de state, CMK, lifecycle, Object Lock opcional
├── stacks/fluxo-caixa/        composicao. Toda a logica mora aqui.
├── envs/dev|hml|prod/         so chamam a composicao, com tfvars e backend.hcl
└── modules/
    ├── network
    ├── security/kms
    ├── ecr
    ├── ecs-cluster
    ├── ecs-service
    ├── alb
    ├── edge                   CloudFront, VPC Origin e WAF CLOUDFRONT
    ├── api-gateway            REST regional, VPC Link v2, WAF regional, rotacao do header
    ├── rds-postgres
    ├── elasticache
    ├── messaging
    ├── cognito
    └── observability
```

Nome de recurso: `fluxo-caixa-<env>-<recurso>`. Tags padrão do provider: `Project`, `Environment`, `Owner`, `CostCenter`, `ManagedBy`.

Contas separadas são opcionais. `assume_role_arn` no `terraform.tfvars` faz o provider assumir a role da conta do ambiente. O bootstrap roda uma vez em cada conta que guarda state. Sem a variável, o Terraform usa a credencial corrente.

## Dimensionamento

Os números da seção 15 do blueprint são o default da composição e estão repetidos no `terraform.tfvars.example` de cada ambiente. Qualquer campo do objeto `sizing` pode ser sobrescrito. Em prod, um `check` recusa plano sem 3 AZs, NAT por AZ, interface endpoints nas 3 AZs, RDS Multi-AZ e deletion protection.

| | dev | hml | prod |
|---|---|---|---|
| AZs | 2 | 2 | 3 |
| NAT | 1 | 1 | 3 |
| Interface endpoints | 0 (sai pelo NAT) | 2 AZs | 3 AZs |
| Fargate | Spot, 0,5 vCPU / 1 GB, min 1 max 2 | on-demand | on-demand, min 2 max 10 |
| consolidado | 0,5 vCPU / 1 GB | 1 vCPU / 2 GB, max 3 | 1 vCPU / 2 GB |
| RDS (um por serviço) | db.t4g.micro, 20 GB, 1 dia | db.t4g.small, 20 GB, 7 dias | db.t4g.medium, 50 GB, Multi-AZ, 14 dias, deletion protection |
| Rotação da senha | 90 dias | 30 dias | 30 dias |
| Réplica / RDS Proxy | 0 / não | 0 / não | 0 / não, parametrizável |
| Valkey | cache.t4g.micro, 1 nó | cache.t4g.small, 1 nó | cache.t4g.small, primário + réplica |
| Logs | 7 dias | 14 dias | 30 dias |
| Container Insights | desligado | ligado | enhanced |
| Monitoring do RDS | não | não | 60 s |

O gateway de S3 existe nos três ambientes. Não tem custo horário e tira a camada de imagem do NAT.

## Bootstrap e state

O bootstrap é local de propósito: ele cria o bucket que os outros states vão usar. O bucket tem versionamento, SSE-KMS, block public access, policy que recusa transporte sem TLS, lifecycle das versões antigas e Object Lock opcional (`enable_object_lock`, modo GOVERNANCE). Object Lock só pode ser ligado na criação do bucket.

```bash
cd infra/terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply

terraform output -raw backend_hcl_dev  > ../envs/dev/backend.hcl
terraform output -raw backend_hcl_hml  > ../envs/hml/backend.hcl
terraform output -raw backend_hcl_prod > ../envs/prod/backend.hcl
```

O `backend "s3" {}` do ambiente é parcial. Bucket, key `fluxo-caixa/<env>/terraform.tfstate`, region, `encrypt`, `kms_key_id` e `use_lockfile` ficam no `backend.hcl`, que não entra no git.

```bash
make tf-init ENV=dev
make tf-plan ENV=dev
make tf-validate
make tf-test
```

`tf-plan` grava `<env>.tfplan`. Não aplique produção sem esse arquivo revisado e sem aprovação. O bucket de state tem `prevent_destroy`.

Promoção entre ambientes é de imagem, não de state. O CD constrói uma vez (tag = SHA), publica no ECR de dev, assina com cosign e, em hml/prod, copia o mesmo digest. O Terraform de cada ambiente continua dono da task definition; alinhe `image_tag` antes do próximo apply para ele não voltar uma revisão antiga.

## Rotação de segredos

O RDS cria o secret (`manage_master_user_password`) e já associa a função de rotação gerenciada. O provider 6.72 não tem agenda no `aws_db_instance`. `aws_secretsmanager_secret_rotation` só ajusta `automatically_after_days` e deixa `rotation_lambda_arn` de fora, para não substituir a função do RDS. `rotate_immediately = false` evita rotacionar a senha em todo apply.

A CMK do secret é outra, distinta da CMK do storage. Toda CMK do projeto tem `enable_key_rotation = true`.

EventBridge escuta `AWS Secrets Manager Rotation Failed` e os eventos de CloudTrail `RotationFailed` / `RotationAbandoned`, e publica no tópico de alertas. O segredo `X-Origin-Verify` entra nesse mapa quando o API Gateway está ligado.

## API Gateway

Módulo `modules/api-gateway`. REST API regional, stage `v1`, authorizer Cognito, VPC Link v2 no ALB interno, WAF regional e rotação do header. O POST de lançamento valida corpo e parâmetros. O POST de estorno só valida parâmetros: o corpo é opcional, e `motivo`, quando vem, é limitado no serviço. O detalhe da decisão está no [ADR 0031](../../docs/04-adr/0031-api-gateway-rest-regional.md).

### Alternar `enable_api_gateway`

O default é `true` nos três ambientes. Para voltar ao caminho CloudFront → VPC Origin → ALB:

```hcl
enable_api_gateway = false
```

no `terraform.tfvars` do ambiente. O módulo do gateway não é criado. O CloudFront manda `/api/v1/*` para a origem `alb`. O listener não reescreve `/v1`.

### Cadastrar consumidor com API key

Cliente final usa só JWT. API key é para integração. No tfvars:

```hcl
api_consumers = {
  pdv = {
    description    = "PDV da loja"
    quota_limit    = 100000
    quota_period   = "MONTH"
    throttle_rate  = 10
    throttle_burst = 20
  }
}

api_key_required = {
  "POST /api/v1/lancamentos" = true
}
```

`quota_period` é `DAY`, `WEEK` ou `MONTH`. A chave é gerada pela AWS e fica no state; o output publica só o id. Sem `api_key_required`, a chave pode ser enviada para cair no usage plan, mas a rota não exige. Com o mapa acima, o POST de lançamento exige a chave e o JWT.

Throttle do consolidado: 20 req/s em dev, 80 em hml, 100 em prod. hml e prod ficam acima do pico de 50. O módulo recusa o contrário quando `require_consolidado_above_peak` está ligado, o que a composição faz fora de dev.

### Rotação do `X-Origin-Verify`

O segredo `${name_prefix}/origin-verify` guarda `current` e `previous`, cifrado na CMK `origin-verify`. A Lambda `rotate_origin_verify` implementa `createSecret`, `setSecret`, `testSecret` e `finishSecret`. No `setSecret` ela move o current antigo para `previous`, grava o valor novo e atualiza o custom header da origem `api-gateway` (a distribuição é a que tem a tag `Name` do ambiente) e os dois byte matches da regra `x-origin-verify`. O valor anterior continua aceito até a rotação seguinte. `rotate_immediately = false`. A agenda é `origin_verify_rotation_days`: 90 dias em dev, 30 em hml e prod.

O Terraform não fica preso à versão que criou. O bootstrap usa `ephemeral.random_password` em `secret_string_wo`, com `secret_string_wo_version = 1`. Esse número é o gatilho de reenvio: a senha efêmera muda a cada plan, mas o provider só grava de novo se a versão mudar. Não use `ignore_changes` na distribuição nem na Web ACL: uma regra nova ou um behavior novo precisa continuar aparecendo no plan. CloudFront e o WAF leem `current` e `previous` de `data.aws_secretsmanager_secret_version` com stage `AWSCURRENT`. No primeiro apply o data source espera a versão existir (`depends_on`), sem ciclo. No plan seguinte o valor vigente entra na configuração. Se a Lambda já atualizou a borda, o plan fica vazio. Se só o segredo girou, o apply empurra CloudFront e o WAF para o mesmo par, em vez de reverter para o bootstrap.

O que ainda fica no state: o par lido pelo data source, o custom header da origem do CloudFront e os `search_string` da regra do WAF. O header na distribuição é inevitável. O `secret_string` do resource da versão não fica no state. Não aplique no meio de uma rotação: até o `finishSecret`, `AWSCURRENT` ainda é o valor anterior, e o apply pode desfazer o header que o `setSecret` já gravou.

## Réplica e RDS Proxy

`sizing.read_replica_count` default 0. `sizing.enable_rds_proxy` default false.

A AWS não cria read replica de PostgreSQL quando a senha mestre é gerenciada pelo Secrets Manager ([limitação](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)). A variável `read_replica_count` valida a combinação, e a instância repete a regra numa precondition. O plano falha com mensagem que aponta o Multi-AZ DB Cluster, o Aurora ou a rotação por Lambda. Enquanto `read_replica_count` for 0, `DB_READER_URL` não é definida e a leitura vai ao primário. O gatilho e a recomendação (Multi-AZ DB Cluster) estão no [ADR 0030](../../docs/04-adr/0030-replica-leitura-estrategia-de-dados.md).

RDS Proxy não depende de réplica. Com `enable_rds_proxy = true`, `DB_URL` aponta para o endpoint do proxy (`jdbc:postgresql://…proxy-…`) e a task alcança o security group do proxy, que por sua vez alcança o banco. O proxy também lê o secret gerenciado pelo RDS. O serviço reconhece `.proxy-` e não liga o plugin `failover` contra esse endpoint.

## Contrato da task

`AWS_ENDPOINT_URL` não é definida na AWS. `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` fala com o sidecar no mesmo network namespace. O health check do target group é `GET /actuator/health/readiness`.

## Validação

```bash
terraform fmt -recursive -check infra/terraform
make tf-validate
make tf-test
```

Nada disso faz apply. `tflint` e `trivy config` rodam no workflow quando as ferramentas existem. O pre-commit está em `.pre-commit-config.yaml`.

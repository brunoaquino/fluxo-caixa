# Terraform — fluxo de caixa na AWS

Stack de `sa-east-1` para os serviços `lancamentos` e `consolidado`. Terraform `>= 1.10`, provider `hashicorp/aws ~> 6.0`. O state de cada ambiente é remoto, com lock nativo do S3 (`use_lockfile = true`, sem DynamoDB).

Dev e prod são states separados. Dentro de um ambiente, rede, dados e compute ficam no mesmo state: o conjunto é pequeno e sobe junto. Se o stack crescer, o próximo corte natural é separar dados de compute.

## Arquitetura

```
Internet
  -> ALB público (HTTPS, redirect 80) + WAF
       /api/v1/lancamentos*   -> ECS lancamentos
       /api/v1/consolidados*  -> ECS consolidado
  -> tasks Fargate ARM64 em subnets de aplicação (mínimo 2 em prod, AZs diferentes)
       lancamentos -> RDS próprio + SNS
       consolidado -> RDS próprio + SQS + Valkey
  SNS (raw delivery) -> SQS (visibility 60s, retenção 14d, long polling 20s)
                     -> DLQ após 5 recebimentos
```

O lançamentos não chama o consolidado. Se o consolidado cair, a fila segura o evento por 14 dias. As tasks não têm IP público: AWS API sai por VPC endpoint e o que sobrar (pouca coisa) pelo NAT. Subnets de dados não têm rota para a internet.

VPC endpoints de interface: `ecr.api`, `ecr.dkr`, `logs`, `sqs`, `sns`, `secretsmanager`, `sts`, `kms` e `xray`. O X-Ray não está na lista mínima do blueprint; ele existe para o sidecar ADOT entregar trace sem abrir egress `0.0.0.0/0`. Gateway de S3 nas route tables de aplicação, para a camada de imagem do ECR.

## Módulos

| Módulo | O que cria |
|---|---|
| `modules/network` | VPC em 3 AZs, subnets públicas/app/dados, NAT, endpoints, flow logs |
| `modules/security/kms` | CMKs com rotação: RDS de cada serviço, Valkey, mensageria, logs, access log, ECR |
| `modules/ecr` | Repositórios imutáveis, scan on push, criptografia KMS |
| `modules/ecs-cluster` | Cluster Fargate com Container Insights enhanced |
| `modules/ecs-service` | Task ARM64, ADOT, SG mínimo, scaling, circuit breaker |
| `modules/alb` | ALB, regras de caminho, WAF, access logs |
| `modules/rds-postgres` | PostgreSQL 17, senha no Secrets Manager, SSL forçado, PITR |
| `modules/elasticache` | Valkey, TLS obrigatório, sem senha (o contrato não tem `REDIS_PASSWORD`) |
| `modules/messaging` | SNS + SQS + DLQ, raw delivery, policies da task role |
| `modules/cognito` | User pool Essentials, resource server `fluxo-caixa`, Lambda de pre-token v2 |
| `modules/observability` | Log groups, dashboard, alarmes e tópico de alerta |
| `bootstrap` | Bucket de state. State local de propósito: é o ovo e a galinha |

O scaling é target tracking de CPU e de `ALBRequestCountPerTarget` nos dois serviços. O consolidado soma um target tracking de backlog por task (`mensagens visíveis / tasks em execução`, métrica do Container Insights). Não há step scaling junto: as duas políticas na mesma métrica brigam no scale-in. O `desired_count` do serviço é ignorado depois do create, para o autoscaling não ser desfeito no próximo apply.

## Dev e prod

| | dev | prod |
|---|---|---|
| NAT | 1 | 1 por AZ |
| Interface endpoints | 1 AZ | 3 AZs |
| Tasks iniciais | 1 por serviço | 2, mínimo 2 |
| ECS Exec | ligado | desligado |
| RDS | `db.t4g.micro`, single-AZ, sem delete protection | `db.t4g.small`, Multi-AZ, delete protection, snapshot final |
| Backup RDS | 7 dias | 14 dias (PITR, RPO alvo de 5 min) |
| Valkey | 1 nó `cache.t4g.micro` | primário + réplica, failover e Multi-AZ, `cache.t4g.small` |
| Flow logs | REJECT | ALL |
| Retenção de log | 7 dias | 30 dias |

Nomes de SNS/SQS levam o prefixo do ambiente (`fluxo-caixa-dev-...`). A seção 9 do blueprint fixa os nomes para o LocalStack. Na AWS o serviço lê `LANCAMENTOS_TOPIC_ARN` e `CONSOLIDADO_QUEUE_URL`, então os dois ambientes podem existir na mesma conta. CIDRs diferentes (`10.20.0.0/16` e `10.30.0.0/16`) deixam um peering futuro possível.

Custo, com as quantidades deste Terraform e preço de tabela de `sa-east-1` (detalhe em [docs/08-custos.md](../../docs/08-custos.md)): prod fica em cerca de **USD 1.214/mês** e dev, ligado o mês inteiro, em cerca de **USD 385/mês**. NAT em São Paulo é cerca de USD 68 por gateway (não os USD 32 de `us-east-1`) e cada interface endpoint em uma AZ é cerca de USD 15. Prod tem 3 NAT e 9 endpoints × 3 AZs, incluindo KMS e X-Ray. Parar a task de dev à noite não é o maior corte: NAT, endpoint e RDS continuam.

## Como provisionar

Não rode `apply` em produção sem um plan revisado. O bootstrap tem `prevent_destroy` no bucket.

```bash
cd infra/terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars   # bucket único
terraform init
terraform apply

terraform output -raw backend_hcl_dev  > ../envs/dev/backend.hcl
terraform output -raw backend_hcl_prod > ../envs/prod/backend.hcl
```

O `backend.hcl` gerado inclui `use_lockfile = true` e a CMK do bucket. O bloco `backend "s3"` no ambiente deixa esses campos comentados de propósito.

```bash
cd infra/terraform/envs/dev
cp terraform.tfvars.example terraform.tfvars
# troque certificate_arn e access_logs_bucket_name
terraform init -backend-config=backend.hcl
terraform plan -out=dev.tfplan
terraform apply dev.tfplan
```

Prod é o mesmo fluxo em `envs/prod`, com o `backend.hcl` da chave `fluxo-caixa/prod/terraform.tfstate`.

Confira a versão do Postgres antes do apply. O default é `17.11`:

```bash
aws rds describe-db-engine-versions --region sa-east-1 --engine postgres --engine-version 17.11
```

### Imagem no ECR

O repositório é imutável. Publique uma tag que não seja `latest` e use a mesma em `image_tag`. A task é ARM64.

```bash
aws ecr get-login-password --region sa-east-1 | docker login --username AWS --password-stdin ACCOUNT.dkr.ecr.sa-east-1.amazonaws.com
docker buildx build --platform linux/arm64 -t ACCOUNT.dkr.ecr.sa-east-1.amazonaws.com/fluxo-caixa-dev/lancamentos:TAG --push services/lancamentos-service
```

O primeiro apply pode criar o serviço antes da imagem existir. O circuit breaker marca esse deploy como falho. Publique a imagem e force um novo deployment, ou aplique de novo com `image_tag` preenchida.

O workflow `.github/workflows/cd.yml` faz build, push e `render-task-definition` + deploy. Ele só troca a imagem. Se o `image_tag` do Terraform ficar para trás, o próximo apply volta a revisão antiga. Alinhe a tag antes desse apply.

### Usuário no Cognito

O plano do user pool é Essentials: a customização do access token (trigger v2) não existe no plano Lite. O atributo `custom:comerciante_id` é developer-only. A Lambda copia esse valor para o claim `comerciante_id`.

```bash
POOL=$(terraform output -raw cognito_user_pool_id)
aws cognito-idp admin-create-user \
  --user-pool-id "$POOL" \
  --username comerciante1 \
  --message-action SUPPRESS \
  --user-attributes Name=custom:comerciante_id,Value=11111111-1111-1111-1111-111111111111
aws cognito-idp admin-set-user-password \
  --user-pool-id "$POOL" \
  --username comerciante1 \
  --password 'TroqueEstaSenha12' \
  --permanent
```

O app client pede os escopos `fluxo-caixa/lancamentos:write`, `fluxo-caixa/lancamentos:read` e `fluxo-caixa/consolidado:read`. O issuer sai em `cognito_issuer_uri`.

## Contrato de ambiente na task

Além das variáveis da seção 10 do blueprint, a task recebe `DB_USERNAME` e `DB_PASSWORD` do secret que o RDS gerencia (`manage_master_user_password`). `DB_URL` usa `sslmode=require`: o parameter group recusa conexão sem SSL. `verify-full` fica para quando a imagem trouxer o bundle de CA da RDS.

`AWS_ENDPOINT_URL` não é definida na AWS. String vazia faz o SDK tratar o endpoint como inválido. No compose local ela aponta para o LocalStack.

`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` fala com o sidecar ADOT no mesmo network namespace da task. O coletor manda trace para o X-Ray e métrica EMF para o CloudWatch. Ele não é essencial: se o coletor cair, a task de negócio continua.

O health check do target group é `GET /actuator/health/readiness`.

## Validação local deste repositório

```bash
terraform fmt -recursive -check infra/terraform
cd infra/terraform/envs/dev  && terraform init -backend=false && terraform validate
cd infra/terraform/envs/prod && terraform init -backend=false && terraform validate
```

`tflint` e `checkov` não estão instalados nesta máquina. O workflow `terraform.yml` roda os dois; o Checkov em soft-fail.

## Rollback

`terraform apply` de um plan anterior, ou `terraform state` apenas com o plan na mão. Produção tem delete protection no RDS, no ALB e no user pool: para destruir, desligue a proteção num apply separado e guarde o snapshot final. O bucket de state não sai com `destroy` por causa do `prevent_destroy`.

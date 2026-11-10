# 08 — Custos e licenças

Estimativa de **novembro de 2026** para o que `infra/terraform/envs/*/terraform.tfvars.example` pede para criar, na topologia do [deployment](03-arquitetura/deployment-aws.md). Não é fatura. Os preços unitários são os da tabela abaixo; as quantidades são as do Terraform, não um desenho paralelo.

**Câmbio usado:** USD 1 = BRL 5,70. Premissa de trabalho em 2026-11-10, não cotação oficial. Trocar o câmbio muda a coluna em reais e não muda a decisão em dólar.

**Fonte.** Preço de Fargate ARM, RDS PostgreSQL, ElastiCache Valkey, ALB, NAT Gateway e hora de VPC endpoint consultado na AWS Price List API em 2026-11-10 (publicação da oferta em torno de 2026-10-01 a 2026-11-01). Itens pequenos (WAF, SNS, SQS, CloudWatch, Cognito, Secrets Manager, KMS, ECR, transferência) usam a ordem de grandeza da tabela pública e estão marcados como estimativa. Validar o total no [AWS Pricing Calculator](https://calculator.aws/) antes de aprovar orçamento. Horas/mês = 730.

## 1. O que a conta ensina antes dos números

O pico de 50 req/s não é o que domina o custo. Quatro tasks Fargate (duas de 0,5 vCPU/1 GiB e duas de 1 vCPU/2 GiB) custam menos que três NAT Gateways e muito menos que nove VPC endpoints de interface em três zonas. RDS em São Paulo continua sendo o maior item de dado. Licença de software de runtime é zero. O prêmio de `sa-east-1` contra `us-east-1`, na mesma topologia, fica perto de **1,9 vez**.

Uma conta anterior deste documento fechava em cerca de **USD 1.170** porque tratava as quatro tasks como 1 vCPU/2 GiB e contava sete endpoints (a lista mínima do blueprint, sem KMS e sem X-Ray). Uma ordem de grandeza de cerca de **USD 600** no README da infra usava hora de NAT perto de USD 32 e hora de endpoint perto de USD 7, que são os preços de `us-east-1`, e não fechava storage, o nono endpoint nem o RDS de São Paulo. As duas divergem do HCL. A conta abaixo usa as quantidades do Terraform e os preços de `sa-east-1` da seção 3.

Ambiente diário do desenvolvedor: Docker local, **USD 0** de AWS. A linha “dev” é a conta do `terraform.tfvars.example` de dev se o ambiente ficar ligado o mês inteiro. O módulo de rede não desliga task, NAT nem endpoint fora do horário.

## 2. Premissas de volume (produção)

| Premissa | Valor | Efeito na conta |
|---|---|---|
| Tasks no ar | Lançamentos: 2 × 0,5 vCPU e 1 GiB. Consolidado: 2 × 1 vCPU e 2 GiB. O mês inteiro, no mínimo do scaling. O sidecar ADOT divide a CPU da task; não soma outra task | Fargate ARM64 |
| HTTP | ~5 milhões de requests/mês (média baixa; o pico de 50 req/s é pontual) | Cerca de 1 LCU médio no ALB, WAF, X-Ray |
| Eventos | ~2 milhões/mês | SNS/SQS, irrelevante |
| NAT | 3 gateways em prod (um por AZ) e 20 GB/mês de egress residual. SQS, SNS, ECR, Logs, Secrets, STS, KMS e X-Ray vão por endpoint | Hora do NAT mais o GB residual |
| Endpoints | 9 interfaces (`ecr.api`, `ecr.dkr`, `logs`, `sqs`, `sns`, `secretsmanager`, `sts`, `kms`, `xray`) × 3 AZs. S3 é gateway, sem hora | Hora por endpoint por AZ |
| Log | dezenas de GB/mês, não DEBUG. Retenção de 30 dias em prod | CloudWatch Logs |
| Cognito | User pool Essentials, 1.000 MAU | Dentro da franquia típica do Essentials (até 10.000). Se a franquia não se aplicar, 1.000 × USD 0,015 ≈ USD 15 |
| RDS | 2 × `db.t4g.small` Multi-AZ, 50 GB gp3 (teto de autoscaling 200 GB, não cobrado até crescer) | Classe do [tfvars de prod](../infra/terraform/envs/prod/terraform.tfvars.example) |
| ElastiCache | Valkey 8.2, 2 × `cache.t4g.small`, TLS, sem senha | Primário e réplica |
| WAF | 1 Web ACL regional e 5 regras (rate por IP, Common, Known Bad Inputs, SQLi, IP Reputation), nos dois ambientes | O módulo `alb` associa o ACL sempre |

## 3. Preço unitário confirmado na Price List

| Item | sa-east-1 | us-east-1 |
|---|---|---|
| Fargate ARM, vCPU-hora | 0,0557 | 0,03238 |
| Fargate ARM, GB-hora | 0,00612 | 0,00356 |
| RDS PostgreSQL `db.t4g.small` Multi-AZ, hora | 0,137 | 0,065 |
| RDS `db.t4g.medium` Multi-AZ, hora | 0,275 | 0,129 |
| RDS `db.t4g.micro` Single-AZ, hora | 0,034 | 0,016 |
| gp3 Multi-AZ, GB-mês | 0,438 | 0,230 |
| gp3 Single-AZ, GB-mês | 0,219 | 0,115 |
| ElastiCache Valkey `cache.t4g.small`, hora | 0,0488 | 0,0256 |
| ElastiCache Valkey `cache.t4g.micro`, hora | 0,0240 | 0,0128 |
| ALB, hora | 0,034 | 0,0225 |
| ALB, LCU-hora | 0,011 | 0,008 |
| NAT, hora e por GB | 0,093 | 0,045 |
| VPC endpoint de interface, hora por AZ | 0,021 | 0,010 |

O GB-mês do gp3 Multi-AZ na tabela já é o dobro do Single-AZ. Não se multiplica de novo pelo standby.

## 4. Produção em sa-east-1

Topologia do `envs/prod/terraform.tfvars.example`: 3 AZs, NAT por AZ, 9 endpoints de interface × 3 AZs, 2 RDS `db.t4g.small` Multi-AZ com 50 GB, Valkey `cache.t4g.small` com réplica, tasks no mínimo do scaling o mês inteiro. O X-Ray entra além da lista do blueprint para o sidecar ADOT não depender de egress `0.0.0.0/0`. O KMS entra porque as CMKs são chamadas de dentro da VPC.

| Linha | Conta | USD/mês |
|---|---|---:|
| Fargate ARM, lançamentos, 2 × (0,5 vCPU + 1 GB) | 2 × 0,03397 × 730 | 49,60 |
| Fargate ARM, consolidado, 2 × (1 vCPU + 2 GB) | 2 × 0,06794 × 730 | 99,19 |
| ALB | 0,034 × 730 + 1 LCU × 0,011 × 730 | 32,85 |
| WAF | 1 Web ACL × 5 + 5 regras × 1 + 5 milhões × 0,60 / milhão | 13,00 |
| NAT Gateway × 3 | 3 × 0,093 × 730 + 20 GB × 0,093 | 205,53 |
| VPC endpoints de interface | 27 × 0,021 × 730 | 413,91 |
| Dados processados nos endpoints | 50 GB × 0,01 (piso de us-east-1; São Paulo pode ser maior) | 0,50 |
| RDS compute, 2 × small Multi-AZ | 2 × 0,137 × 730 | 200,02 |
| RDS gp3, 2 × 50 GB Multi-AZ | 2 × 50 × 0,438 | 43,80 |
| ElastiCache Valkey, 2 × small | 2 × 0,0488 × 730 | 71,25 |
| SNS + SQS + DLQ | ~2 milhões de eventos, requests de publish/receive/delete | 8,00 |
| Cognito Essentials | 1.000 MAU na franquia | 0,00 |
| CloudWatch, Container Insights, X-Ray | logs, métricas customizadas moderadas, traces amostrados a 10%, alarmes, dashboard | 50,00 |
| Secrets Manager | 2 segredos gerenciados pelo RDS (username/password) e chamadas das tasks | 2,00 |
| KMS | 7 CMKs (dois RDS, Valkey, mensageria, logs, access log, ECR) + requests | 8,00 |
| ECR | poucos GB, scan no push | 1,00 |
| Transferência de dados | inter-AZ residual e egress pequeno | 15,00 |
| Lambda de pre-token | dentro da franquia | 0,00 |
| **Total** | | **1.213,65** |

**USD 1.214 por mês**, cerca de **R$ 6.918**.

Backup de RDS dentro de 100% do armazenamento provisionado não foi somado de novo. A retenção de prod é 14 dias; PITR que retenha muito além do tamanho do banco passa a cobrar storage de backup. Performance Insights com 7 dias, como no módulo RDS, fica na franquia. Snapshot do Valkey (7 dias) só pesa se o backup passar do que a AWS inclui no nó; no volume inicial isso é alguns dólares.

## 5. A mesma produção em us-east-1

Mesmos tamanhos, preços da coluna us-east-1. WAF tratado como preço global. Observabilidade e mensageria um pouco menores porque log e request são mais baratos na Virgínia; o grosso da diferença não está neles.

| Linha | USD/mês |
|---|---:|
| Fargate, mesmos tamanhos de task | 86,51 |
| ALB | 22,27 |
| WAF | 13,00 |
| NAT × 3 | 99,45 |
| Endpoints × 27 | 197,10 |
| Dados dos endpoints | 0,50 |
| RDS compute 2 × small Multi-AZ | 94,90 |
| RDS storage | 23,00 |
| ElastiCache 2 × small | 37,38 |
| Mensageria, observabilidade, segredos, KMS, ECR, transferência | 67,00 |
| **Total** | **641,11** |

**USD 641 por mês**, cerca de **R$ 3.654**. A produção em São Paulo custa **1,89 vez** a de Virgínia nesta conta (1.213,65 / 641,11). A diferença é o motivo do [ADR 0019](04-adr/0019-regiao-sa-east-1-lgpd.md): residência do dado, não desconhecimento do preço.

## 6. Dev AWS (opcional) em sa-east-1

O local continua sendo o padrão. O exemplo de dev no Terraform não é “prod menor só no horário comercial”. Ele cria, 24×7:

| Escolha no tfvars | Efeito |
|---|---|
| Lançamentos 0,25 vCPU / 0,5 GiB, 1 task. Consolidado 0,5 vCPU / 1 GiB, 1 task | Sem mínimo de duas tasks |
| 1 NAT e 9 interface endpoints em 1 AZ | O endpoint existe; não é a variante “sem endpoint” |
| 2 × `db.t4g.micro` Single-AZ, 20 GB | Sem Multi-AZ e sem delete protection |
| 1 × `cache.t4g.micro`, sem réplica | TLS continua ligado no módulo |
| ALB e WAF | O módulo de ALB associa o Web ACL também em dev |
| Nada desliga sozinho à noite | Parar o service ECS economiza a linha de Fargate, não NAT, endpoint nem RDS |

| Linha | Conta | USD/mês |
|---|---|---:|
| Fargate 24×7 | (0,25 × 0,0557 + 0,5 × 0,00612) × 730 + (0,5 × 0,0557 + 1 × 0,00612) × 730 | 37,20 |
| ALB | 0,034 × 730 + 0,25 LCU × 0,011 × 730 | 26,83 |
| WAF | Web ACL + 5 regras, requests desprezíveis | 10,00 |
| NAT × 1 | 0,093 × 730 + 5 GB × 0,093 | 68,36 |
| Endpoints × 9 em 1 AZ | 9 × 0,021 × 730 | 137,97 |
| Dados dos endpoints | 30 GB × 0,01 | 0,30 |
| RDS 2 × micro + 20 GB | 2 × 0,034 × 730 + 2 × 20 × 0,219 | 58,40 |
| ElastiCache micro | 0,0240 × 730 | 17,52 |
| KMS, 7 CMKs | 7 × 1 + requests | 8,00 |
| Secrets, logs, SNS/SQS, ECR | 2 segredos, retenção de 7 dias, pouco evento | 20,00 |
| **Total** | | **384,58** |

**USD 385 por mês**, cerca de **R$ 2.192**, se o dev ficar ligado o mês inteiro. Parar as duas tasks fora do expediente (264 h em vez de 730 h) tira cerca de **USD 24** da linha de Fargate. O corte grande é não subir o dev, ou aceitar mudar o Terraform para não criar os nove endpoints. Copiar os endpoints para as três AZs e os três NAT soma cerca de **USD 412** em cima desta conta (mais 18 endpoint-AZ e mais 2 NAT) e deixa o dev na casa de USD 800.

## 7. Sensibilidade que muda conversa

| Variante em sa-east-1 | Efeito | Total aproximado |
|---|---|---|
| RDS medium Multi-AZ em vez de small | +USD 201 | USD 1.415 |
| Tasks de prod todas em 1 vCPU / 2 GiB, como na conta antiga | +USD 50 | USD 1.263 |
| 2 NAT e endpoints em 2 AZs (9 × 2). O módulo hoje recusa `az_count` diferente de 3 | −USD 206 | USD 1.008 |
| Dev com endpoint e NAT nas 3 AZs | +USD 412 | dev perto de USD 800 |
| Savings Plan / Compute SP de 1 ano no Fargate | cerca de −20% em USD 149 | −USD 30, não é a alavanca |
| Aurora Serverless v2 | mínimo 0,5 ACU o mês inteiro, não escala a zero | No volume atual empata ou perde de `t4g.small` Multi-AZ e acrescenta modelo operacional. Fica como alternativa se o banco passar o dia ocioso e os picos ficarem curtos e altos — ainda assim o piso horário existe |

A alavanca real é rede fixa (NAT e endpoint) e a classe do RDS, não o request do pico.

Otimizações que valem o esforço, em ordem:

1. Não criar dev AWS “igual produção”. O exemplo de dev já é menor, e ainda assim o endpoint em uma AZ custa mais que as duas tasks.
2. Questionar 3 AZs contra 2 para este porte, sabendo que o código trava `az_count` em 3 e que 2 ainda cumpririam o N−1 das tasks e do RDS Multi-AZ. A economia de cerca de USD 206 exige mudar essa validação.
3. Manter `db.t4g.small` até o credit balance ou a memória pedirem medium.
4. O nono endpoint (X-Ray) e o de KMS existem para o ADOT e as CMKs não saírem pelo NAT. Tirar um deles devolve cerca de USD 46 em prod (3 AZs × USD 15,33) e empurra esse tráfego para o NAT. Só vale se o volume nesse caminho for pequeno de propósito.
5. Desligar o service ECS de dev à noite economiza cerca de USD 24. Não resolve NAT, endpoint nem RDS.
6. Graviton já está na conta. Voltar para x86 piora o preço da vCPU.
7. Reserved Instance ou Savings Plan só depois de 2–3 faturas estáveis. Comprometer cedo trava o small que talvez vire micro, ou o contrário.
8. Amostragem de trace em 10% e proibição de DEBUG em produção, para a linha de CloudWatch não crescer escondida.
9. Aurora Serverless v2 revisitada se o relatório de período virar consulta pesada e o resto do dia o banco ficar ocioso. Não é o default.

## 8. Licenças

Custo de licença de runtime e de biblioteca de aplicação: **zero**. Nada aqui exige WebLogic, Oracle Database, MQ comercial, Confluent Cloud com licença de broker, nem JDK Oracle com subscrição.

| Componente | Licença | Custo | Observação |
|---|---|---|---|
| Eclipse Temurin 25 | GPLv2 com Classpath Exception | 0 | JDK da imagem. Oracle JDK comercial fica de fora. |
| Spring Boot 4, Spring Security, Micrometer | Apache 2.0 | 0 | |
| AWS SDK v2, OpenTelemetry | Apache 2.0 | 0 | |
| Flyway Community | Apache 2.0 | 0 | Não usar o tier Teams. |
| PostgreSQL 17 | PostgreSQL License | 0 | O RDS cobra infra, não licença. |
| Redis 7 (local) / Valkey (ElastiCache) | BSD / BSD | 0 | ElastiCache cobra o nó. |
| Keycloak | Apache 2.0 | 0 | Só local. |
| LocalStack | imagem community | 0 | SNS, SQS e o básico do fluxo cabem na community. Não depender de recurso Pro. |
| Grafana `otel-lgtm`, k6 | AGPL | 0 | Só dev e teste. Não são empacotados dentro da imagem do serviço, então a AGPL não se estende ao código do caixa. Não se “embute” Grafana no produto. |
| Testcontainers, ArchUnit, JUnit, springdoc | Apache / EPL conforme o projeto | 0 | |
| Terraform CLI | BSL (HashiCorp), não é licença OSI | 0 para este uso | A CLI não entra no runtime. Se a BSL for impeditiva na organização, OpenTofu (MPL) lê o mesmo HCL. Registrado no [ADR 0010](04-adr/0010-terraform-iac.md). |
| Cognito, WAF, o resto da AWS | serviço | infra, não licença | Já está nas tabelas. |

Não há linha de licença escondida. Se alguém adicionar Oracle JDBC comercial, Debezium com plataforma paga, ou LocalStack Pro como dependência do `make up`, a premissa desta seção deixa de valer e a estimativa precisa de uma linha nova.

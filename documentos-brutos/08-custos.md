# 08 — Custos e licenças

Estimativa de **10 de novembro de 2026** para os três ambientes do [blueprint, seção 15](blueprint-tecnico.md), na topologia do [deployment](03-arquitetura/deployment-aws.md). Não é fatura. A coluna de `us-east-1` é o orçamento. A coluna de `sa-east-1` repete o mesmo desenho só para mostrar o prêmio que o [ADR 0021](04-adr/0021-regiao-us-east-1.md) deixou de pagar.

**Câmbio:** USD 1 = BRL 5,70. Premissa de trabalho em 2026-11-10, não cotação oficial. Trocar o câmbio muda o real e não muda a decisão em dólar.

**Horas/mês:** 730.

**Fonte dos unitários.** AWS Price List API consultada em 2026-11-10. Vigência publicada: Fargate em 2026-10-01, RDS e gp3 em 2026-10-01 (publicação 2026-10-29), ElastiCache em 2026-10-01 (publicação 2026-10-28), CloudFront em 2026-10-01 (publicação 2026-10-19), NAT e ALB em 2026-11-01 (publicação 2026-11-10), VPC endpoint em 2026-11-01 (publicação 2026-11-05), EKS em 2026-10-01 (publicação 2026-10-27), `m7g.large` em 2026-11-01. WAF, Secrets Manager, KMS, mensageria, CloudWatch e transferência residual estão marcados como estimativa de lista. Validar o total no [AWS Pricing Calculator](https://calculator.aws/) antes de aprovar orçamento.

## 1. O que a conta ensina antes dos números

O pico de 50 req/s não domina o custo. Em produção, 27 endpoints de interface (9 serviços × 3 zonas) custam mais do que as quatro tasks Fargate, e dois RDS `db.t4g.medium` Multi-AZ custam mais do que a borda inteira. CloudFront, neste volume, é uma linha pequena: o ganho dele é TLS no edge e WAF, não desconto de request.

A mesma produção em São Paulo sai **1,89 vez** a de Virgínia (USD 1.466,93 / USD 777,35). O grosso está em NAT, endpoint, RDS e Fargate. CloudFront cobra pelo edge que atende o comerciante (América do Sul), não pela região da origem: mudar o ALB para `us-east-1` não aumenta essa linha. O API Gateway desta conta usa o unitário de request de `us-east-1` nas duas colunas.

Réplica de leitura e RDS Proxy ficam **fora** do total. O blueprint deixa os dois desligados. Ligá-los soma as linhas da seção 7.

Ambiente diário do desenvolvedor: Docker local, **USD 0**. A linha “dev” é a conta AWS se o ambiente ficar ligado o mês inteiro.

## 2. Premissas de quantidade

| Premissa | dev | hml | prod |
|---|---|---|---|
| AZs | 2 | 2 | 3 |
| NAT | 1 | 1 | 3 |
| Endpoints de interface | 0 (o tráfego AWS sai pelo NAT) | 9 × 2 AZs | 9 × 3 AZs |
| Tasks no ar o mês inteiro | 1 + 1, Fargate Spot orçado a 50% do on-demand | 1 + 1, on-demand | 2 + 2, on-demand |
| Tamanho | ambos 0,5 vCPU / 1 GiB | lançamentos 0,5/1; consolidado 1/2 | lançamentos 2 × 0,5/1; consolidado 2 × 1/2 |
| RDS | 2 × `db.t4g.micro` Single-AZ, 20 GB gp3 | 2 × `db.t4g.small` Single-AZ, 20 GB gp3 | 2 × `db.t4g.medium` Multi-AZ, 50 GB gp3 |
| ElastiCache Valkey | 1 × `cache.t4g.micro` | 1 × `cache.t4g.small` | 2 × `cache.t4g.small` |
| ALB | interno, 0,1 LCU | interno, 0,25 LCU | interno, 1 LCU |
| CloudFront | 0,2 milhão de requests, 1 GB | 1 milhão, 4 GB | 5 milhões, 10 GB |
| WAF | 1 Web ACL, 6 regras, requests do dev | 1 Web ACL, 5 regras | 1 Web ACL, 5 regras, 5 milhões |
| NAT, dados | 40 GB (sem endpoint: pull de imagem, log, API) | 10 GB residual | 20 GB residual |
| Endpoint, dados | 0 | 30 GB | 50 GB |
| Segredos | 2 (um RDS por serviço) | 2 | 2 |
| CMK | 4 | 5 | 7 |
| Réplica / Proxy | não | não | não, no total-base |

Os nove endpoints são `ecr.api`, `ecr.dkr`, `logs`, `sqs`, `sns`, `secretsmanager`, `sts`, `kms` e `xray`. A lista mínima do blueprint (seção 12) não cita KMS nem X-Ray. Os dois entram para a CMK e o sidecar ADOT não voltarem pelo NAT. S3 é gateway, sem hora. O agente de integração confere se o Terraform criou os nove.

Fargate Spot não tem SKU on-demand na Price List. A AWS publica desconto de até 70%. O orçamento de dev usa **50%** do on-demand para não subestimar o mês em que o desconto realizado for menor. Hml e prod ficam on-demand: hml ensaia a produção, e interrupção de Spot sujaria o ensaio.

## 3. Preço unitário

| Item | us-east-1 | sa-east-1 | sa / us |
|---|---:|---:|---:|
| Fargate ARM, vCPU-hora | 0,03238 | 0,05570 | 1,72 |
| Fargate ARM, GB-hora | 0,00356 | 0,00612 | 1,72 |
| RDS `db.t4g.micro` Single-AZ, hora | 0,016 | 0,034 | 2,13 |
| RDS `db.t4g.small` Single-AZ, hora | 0,032 | 0,069 | 2,16 |
| RDS `db.t4g.medium` Single-AZ, hora | 0,065 | 0,137 | 2,11 |
| RDS `db.t4g.medium` Multi-AZ, hora | 0,129 | 0,275 | 2,13 |
| gp3 Single-AZ, GB-mês | 0,115 | 0,219 | 1,90 |
| gp3 Multi-AZ, GB-mês | 0,230 | 0,438 | 1,90 |
| Valkey `cache.t4g.micro`, hora | 0,0128 | 0,0240 | 1,88 |
| Valkey `cache.t4g.small`, hora | 0,0256 | 0,0488 | 1,91 |
| ALB, hora | 0,0225 | 0,0340 | 1,51 |
| ALB, LCU-hora | 0,008 | 0,011 | 1,38 |
| NAT, hora e por GB | 0,045 | 0,093 | 2,07 |
| VPC endpoint, hora por AZ | 0,010 | 0,021 | 2,10 |
| VPC endpoint, GB (primeiro PB) | 0,01 | 0,01 | 1,00 |
| CloudFront, GB, edge América do Sul, primeiros 10 TB | 0,110 | 0,110 | igual |
| CloudFront, GB, edge Estados Unidos, primeiros 10 TB | 0,085 | — | o edge americano é mais barato e mais longe do comerciante |
| CloudFront HTTPS, América do Sul, por 10 mil (GET e POST encaminhado) | 0,0220 | 0,0220 | igual |
| EKS, control plane, hora | 0,10 | 0,10 | 1,00 |
| EC2 `m7g.large` Linux, hora | 0,0816 | — | usado só no cenário EKS |

O GB-mês do gp3 Multi-AZ já é o dobro do Single-AZ. Não se multiplica de novo pelo standby.

CloudFront não tem preço “da região da conta”. O comerciante no Brasil, com `PriceClass_All`, cai no edge de São Paulo e paga a tarifa da América do Sul qualquer que seja a origem. `PriceClass_100` empurraria o TLS até um edge americano: no volume de produção a diferença é cerca de USD 6 por mês (10 GB × 0,025 de delta no GB, mais o request mais barato do edge americano) e devolve o handshake para um RTT de 110–130 ms. Não compensa. A conta usa `PriceClass_All`.

WAF de escopo CloudFront (lista pública, igual nas duas regiões): USD 5 por Web ACL/mês, USD 1 por regra/mês, USD 0,60 por milhão de requests. Managed rules usadas aqui não acrescentam a tarifa de Bot Control. Shield Standard não tem linha.

Secrets Manager: USD 0,40 por segredo/mês. Três segredos (duas senhas de RDS e o header `X-Origin-Verify`) = USD 1,20. As chamadas `GetSecretValue` deste volume ficam abaixo de um centavo (USD 0,05 por 10 mil). A Lambda de rotação do header roda uma vez por período e fica abaixo de um centavo.

KMS: USD 1 por CMK/mês. As chamadas deste volume ficam abaixo de USD 1 e estão absorvidas na linha residual, não somadas de novo.

## 4. Três ambientes em us-east-1

Cada linha é o produto arredondado a centavo. O total é a soma das linhas arredondadas.

| Linha | dev | hml | prod |
|---|---:|---:|---:|
| Fargate ARM | 14,42 | 43,25 | 86,51 |
| ALB interno | 17,01 | 17,89 | 22,27 |
| WAF CloudFront | 11,12 | 10,60 | 13,00 |
| CloudFront | 0,55 | 2,64 | 12,10 |
| API Gateway REST | 0,70 | 3,50 | 17,50 |
| WAF regional | 10,12 | 10,60 | 13,00 |
| NAT | 34,65 | 33,30 | 99,45 |
| VPC endpoints | 0,00 | 131,40 | 197,10 |
| Dados dos endpoints | 0,00 | 0,30 | 0,50 |
| RDS compute | 23,36 | 46,72 | 188,34 |
| RDS storage | 4,60 | 4,60 | 23,00 |
| ElastiCache | 9,34 | 18,69 | 37,38 |
| Mensageria (estimativa) | 0,50 | 1,50 | 4,00 |
| Observabilidade (estimativa) | 8,00 | 20,00 | 45,00 |
| Secrets Manager | 1,20 | 1,20 | 1,20 |
| KMS | 5,00 | 6,00 | 8,00 |
| ECR | 0,50 | 0,50 | 1,00 |
| Transferência residual (estimativa) | 2,00 | 3,00 | 8,00 |
| **Total** | **143,07** | **355,69** | **777,35** |

**Dev USD 143/mês** (R$ 815). **Hml USD 356/mês** (R$ 2.029). **Prod USD 777/mês** (R$ 4.429).

Os três ligados 24×7: **USD 1.276,11/mês**, **USD 15.313,32/ano**, cerca de **R$ 87.286/ano**.

Fórmulas de produção que ancoram o resto:

- Fargate: 2 × (0,5 × 0,03238 + 1 × 0,00356) × 730 + 2 × (1 × 0,03238 + 2 × 0,00356) × 730 = 86,51.
- Endpoints: 27 × 0,010 × 730 = 197,10.
- RDS: 2 × 0,129 × 730 = 188,34. Storage: 2 × 50 × 0,230 = 23,00.
- NAT: 3 × 0,045 × 730 + 20 × 0,045 = 99,45.
- CloudFront: 5.000.000 × 0,0000022 + 10 × 0,110 = 12,10.
- API Gateway REST: 5.000.000 × 3,50 / 1.000.000 = 17,50. A HTTP API, na mesma premissa, seria 5,00. A diferença é 12,50.
- WAF regional: 5 de Web ACL + 5 regras × 1 + 5 × 0,60 = 13,00. As regras são o header `X-Origin-Verify` e quatro managed rules (Common, Known Bad Inputs, SQLi, IP Reputation).

A premissa de volume é a mesma da linha de CloudFront: **0,2 milhão** de requests em dev, **1 milhão** em hml e **5 milhões** em prod. REST API em `us-east-1`: USD 3,50 por milhão. HTTP API: USD 1,00 por milhão. Dev: 0,70 contra 0,20. Hml: 3,50 contra 1,00. O WAF regional usa a mesma lista do WAF de borda (USD 5 por ACL, USD 1 por regra, USD 0,60 por milhão): piso de USD 10 mais o request. Segredo extra do header: USD 0,40, então a linha de Secrets sobe de 0,80 para 1,20. CMK própria: USD 1, e a linha de KMS sobe um dólar em cada ambiente. Lambda de rotação: uma invocação por período, abaixo de um centavo, sem linha. VPC Link v2 não tem hora nesta conta; o NLB que ele evita teria hora e NLCU.

| | dev | hml | prod |
|---|---:|---:|---:|
| REST API | 0,70 | 3,50 | 17,50 |
| HTTP API, se fosse a escolha | 0,20 | 1,00 | 5,00 |
| Diferença | 0,50 | 2,50 | 12,50 |

HTTP API passa a ser a melhor escolha quando não houver usage plan, validator, canary nem mTLS de parceiro, e o volume subir o bastante para os USD 2,50 por milhão pesarem. Não é este contrato. O [ADR 0031](04-adr/0031-api-gateway-rest-regional.md) registra o trade-off.

Mensageria, observabilidade e transferência residual são ordem de grandeza. Dois milhões de eventos por mês ficam abaixo de USD 10 mesmo com o request regional mais caro; a linha de USD 4 não muda a decisão. Observabilidade de produção junta log com retenção de 30 dias, Container Insights enhanced, X-Ray com amostragem de 10% e alarmes. Se o log passar de dezenas de GB, a linha cresce a USD 0,50/GB de ingestão em `us-east-1` (preço de lista público, não reconsultado na API nesta passagem) e o gatilho de [observabilidade](13-ecs-vs-eks.md) passa a valer.

Backup de RDS dentro de 100% do armazenamento provisionado não foi somado de novo. Performance Insights de 7 dias fica na franquia. Cognito Essentials com 1.000 MAU e a Lambda de pre-token ficam na franquia (USD 0).

## 5. Os mesmos três ambientes em sa-east-1

Mesmas quantidades, preços da coluna `sa-east-1`. WAF e CloudFront não mudam: o Web ACL de escopo CloudFront é global na tarifa, e o edge do comerciante continua sendo a América do Sul.

| Linha | dev | hml | prod |
|---|---:|---:|---:|
| Fargate ARM | 24,80 | 74,39 | 148,79 |
| ALB interno | 25,62 | 26,83 | 32,85 |
| WAF CloudFront | 11,12 | 10,60 | 13,00 |
| CloudFront | 0,55 | 2,64 | 12,10 |
| API Gateway REST | 0,70 | 3,50 | 17,50 |
| WAF regional | 10,12 | 10,60 | 13,00 |
| NAT | 71,61 | 68,82 | 205,53 |
| VPC endpoints | 0,00 | 275,94 | 413,91 |
| Dados dos endpoints | 0,00 | 0,30 | 0,50 |
| RDS compute | 49,64 | 100,74 | 401,50 |
| RDS storage | 8,76 | 8,76 | 43,80 |
| ElastiCache | 17,52 | 35,62 | 71,25 |
| Mensageria (estimativa) | 1,00 | 3,00 | 8,00 |
| Observabilidade (estimativa) | 12,00 | 28,00 | 60,00 |
| Secrets Manager | 1,20 | 1,20 | 1,20 |
| KMS | 5,00 | 6,00 | 8,00 |
| ECR | 0,50 | 0,50 | 1,00 |
| Transferência residual (estimativa) | 4,00 | 6,00 | 15,00 |
| **Total** | **244,14** | **663,44** | **1.466,93** |

O request da REST API nesta coluna usa o unitário de `us-east-1` (USD 3,50 por milhão). Esta passagem não reconsultou o Price List de `sa-east-1` para o API Gateway. WAF, segredo e CMK seguem a mesma lista já usada nas duas regiões. Se o request em São Paulo for mais caro, a linha sobe e o ADR 0021 continua sendo o lugar dessa conta.

| Ambiente | us-east-1 | sa-east-1 | Diferença | sa / us |
|---|---:|---:|---:|---:|
| dev | 143,07 | 244,14 | +101,07 | 1,71 (+71%) |
| hml | 355,69 | 663,44 | +307,75 | 1,87 (+87%) |
| prod | 777,35 | 1.466,93 | +689,58 | 1,89 (+89%) |
| Soma 24×7 | 1.276,11 | 2.374,51 | +1.098,40 | 1,86 |
| Ano | 15.313,32 | 28.494,12 | +13.180,80 | 1,86 |

O ano em São Paulo seria cerca de **R$ 162.416**. A diferença anual de **USD 13.181** (R$ 75.130) é o preço de manter o dado no Brasil com esta topologia. O [ADR 0021](04-adr/0021-regiao-us-east-1.md) registra por que a transferência internacional com salvaguarda foi aceita, e o que se refaz se um contrato exigir residência.

Não há transferência inter-região nesta conta: nada replica de Virgínia para São Paulo. O preço de saída entre regiões não entra no total. O que a decisão compara é o preço de **morar** em cada região.

## 6. Onde o dinheiro de produção está

| Bloco em us-east-1 | USD/mês | Parte |
|---|---:|---:|
| Rede fixa (NAT + endpoints) | 296,55 | 38% |
| RDS compute + storage | 211,34 | 27% |
| Fargate | 86,51 | 11% |
| Borda (ALB + WAF CloudFront + CloudFront + REST API + WAF regional) | 77,87 | 10% |
| ElastiCache | 37,38 | 5% |
| Observabilidade estimada | 45,00 | 6% |
| O resto | 22,70 | 3% |

A alavanca continua sendo rede e classe de banco. O request do pico cabe na linha da REST API (USD 17,50 em cinco milhões) e não muda essa ordem.

## 7. Opcionais que o total não inclui

| Opcional | Conta em us-east-1 | USD/mês | Em sa-east-1 |
|---|---|---:|---:|
| Réplica de leitura do consolidado, `db.t4g.medium` Single-AZ, 50 GB | 0,065 × 730 + 50 × 0,115 | 53,20 | 110,96 |
| Réplica também no lançamentos | o dobro | 106,40 | 221,92 |
| RDS Proxy nos dois bancos (`db.t4g.medium` = 2 vCPU cada) | 4 × 0,015 × 730 | 43,80 | 87,60 (4 × 0,030 × 730) |
| RDS Proxy só no consolidado | 2 × 0,015 × 730 | 21,90 | 43,80 |

São Paulo do Proxy foi confirmado na Price List: **USD 0,030 por vCPU-hora** para PostgreSQL. O filtro da API em `us-east-1` não devolveu a linha nesta consulta. A conta usa **USD 0,015**, metade do SKU confirmado e o valor de lista histórico da página do RDS Proxy. Conferir na calculadora antes de ligar `rds_proxy_enabled`.

VPC Origin não tem linha própria conhecida nesta consulta. A cobrança usada é a do CloudFront mais a do ALB. Se a AWS passar a tarifar a origem VPC, a linha entra aqui antes do parâmetro ir a prod.

## 8. Cenário EKS equivalente (produção, us-east-1)

Troca só a computação. O resto da tabela de produção permanece (RDS, NAT, endpoint, borda, cache).

| Linha | USD/mês |
|---|---:|
| Produção ECS, base | 777,35 |
| Menos Fargate | −86,51 |
| Control plane EKS, 0,10 × 730 | 73,00 |
| 3 × `m7g.large` on-demand, 3 × 0,0816 × 730 | 178,70 |
| EBS gp3, 3 × 30 GB × 0,08 | 7,20 |
| **EKS com a mesma observabilidade gerenciada** | **949,74** |

O EBS gp3 a USD 0,08/GB-mês é preço de lista público de `us-east-1`, não reconsultado na API nesta passagem.

Três nós `m7g.large` (2 vCPU, 8 GiB) cabem as quatro tasks de hoje mais o sidecar e os DaemonSets, um nó por AZ. É o mínimo honesto de N−1. Karpenter não reduz essa conta: com três nós estáveis não há o que consolidar. O critério e a stack estão no [documento 13](13-ecs-vs-eks.md).

Stack open source no lugar dos USD 45 de CloudWatch/X-Ray: mais 2 × `m7g.large` (119,14) + 200 GB gp3 (16,00) + 50 GB S3 (1,15, a USD 0,023/GB de lista). Sobe **USD 136,29** e tira os 45. Total **USD 1.041,03/mês** de infra, antes de gente. O control plane em São Paulo também é USD 0,10/hora; o que encarece o EKS lá é o nó, não o control plane.

Três clusters (dev, hml, prod) seriam 3 × 73 = USD 219 só de control plane, mais nós. O cenário acima é um cluster de produção. Dev e hml continuam em ECS no desenho de custo, que é o que o [ADR 0026](04-adr/0026-ecs-agora-eks-evolucao.md) decide.

## 9. Observabilidade SaaS no volume deste projeto

Premissas de produção: 4 tasks (6 GiB de memória de task no total), cerca de 2.000 séries ativas, 20 GB de log/mês, 5 GB de trace/mês, amostragem de 10%. Preços de lista consultados em 2026-11-10, com a fonte em cada linha. O detalhe de 30 serviços está no [documento 13](13-ecs-vs-eks.md).

| Opção | USD/mês neste volume | Por quê nessa ordem |
|---|---:|---|
| CloudWatch + X-Ray + ADOT (a linha já somada) | 45 | Já está na conta. Sem mais uma plataforma. |
| Grafana Cloud Pro | 19 | Taxa de plataforma. 2.000 séries cabem nas 10.000 inclusas; 20 GB de log e 5 GB de trace cabem nos 50 GB inclusos de cada um. Página grafana.com/pricing. |
| AMP + Amazon Managed Grafana | ~34 | Ingestão: 2.000 × 86.400 amostras/mês = 172,8 milhões. A USD 0,90 por 10 milhões no primeiro degrau (página pública do AMP, não revalidada na API) = 15,55. Dois editores a USD 9 = 18. Log continua no CloudWatch. |
| Datadog, SKU serverless anual | ~52 | Workload USD 3 × 4 tasks + APM serverless USD 6 × 4, mais 20 GB × 0,10 e 5 milhões de eventos indexados a USD 1,70/milhão (15 dias). Tabela datadoghq.com/pricing/list. |
| Dynatrace full-stack | ~48 | USD 58 por 8 GiB/mês na página de preço. 6 GiB de task × (58/8) = 43,50, mais 20 GiB de log a USD 0,20 (rate card). |

Nenhuma dessas linhas, sozinha, paga um engenheiro. A premissa de planejamento é custo carregado de **USD 8.000/mês** por engenheiro sênior (não é pesquisa salarial). Um quarto de pessoa (USD 2.000) é maior do que qualquer SaaS desta tabela e maior do que os USD 136 da stack self-hosted. Por isso a decisão de agora é ADOT + CloudWatch + X-Ray ([ADR 0027](04-adr/0027-observabilidade-oss-vs-saas.md)). Grafana Cloud é a primeira troca se o time quiser PromQL sem operar Loki. Self-hosted espera o time de plataforma que também justificaria o EKS.

## 10. Otimizações, na ordem em que o dinheiro responde

1. Não criar dev “igual produção”. O dev desta conta já não tem endpoint. Ainda assim o NAT (USD 35) pesa mais do que as duas tasks em Spot (USD 14).
2. Desligar task de dev e hml fora do expediente (264 h em vez de 730 h, cerca de 36% das horas) reduz a linha de Fargate para esse fator: dev cai cerca de USD 9, hml cerca de USD 28. NAT, RDS e, no hml, os endpoints continuam. Parar o RDS de dev no fim de semana é o corte seguinte (USD 28 de compute+disco), com o limite da AWS de 7 dias parado.
3. Não ligar reader nem Proxy até o gatilho do [documento 12](12-desempenho-e-dados.md). A réplica clássica de USD 53 nem pode subir: PostgreSQL com senha gerenciada pelo Secrets Manager não aceita read replica. No gatilho, a saída compatível é o Multi-AZ DB Cluster (três instâncias e reader endpoint), não essa réplica. O Proxy dos dois bancos, cerca de USD 44, continua desligado pelo mesmo motivo de conexão.
4. Questionar os nove endpoints no hml. Metade das AZs economizaria USD 66 e deixaria o ensaio de PrivateLink diferente da produção. Só vale se hml deixar de ser o ensaio da rede.
5. Savings Plan de computação de 1 ano, cerca de 20% em USD 87 de Fargate de prod, economiza perto de USD 17. Não é a alavanca. Reserved do RDS medium, historicamente na casa de 30% a 40% no on-demand, em USD 188 seria da ordem de USD 60 a 75 por mês. A célula de RI não foi fechada nesta consulta. Comprometer só depois de duas faturas estáveis: o medium pode voltar a small se o crédito de CPU sobrar.
6. Graviton já está na conta. Voltar para x86 piora a vCPU.
7. Amostragem de trace em 10% e proibição de DEBUG em produção. Log por comerciante como dimensão de métrica estoura CloudWatch (USD 0,30 por métrica customizada/mês no primeiro degrau de lista) e vaza tenant.
8. `PriceClass_All` permanece. A economia de trocar de classe é cerca de USD 6 e piora o handshake de quem está no Brasil.

## 11. Licenças

Custo de licença de runtime e de biblioteca de aplicação: **zero**.

| Componente | Licença | Custo | Observação |
|---|---|---|---|
| Eclipse Temurin 25 | GPLv2 com Classpath Exception | 0 | JDK da imagem. Oracle JDK comercial fica de fora. |
| Spring Boot 4.0.9, Spring Security, Micrometer | Apache 2.0 | 0 | |
| Resilience4j 2.4.0 | Apache 2.0 | 0 | [ADR 0023](04-adr/0023-resilience4j-e-versoes.md) |
| AWS SDK v2, Advanced JDBC Wrapper, OpenTelemetry | Apache 2.0 | 0 | |
| Flyway Community | Apache 2.0 | 0 | Não usar o tier Teams. |
| PostgreSQL 17 | PostgreSQL License | 0 | O RDS cobra infra, não licença. |
| Redis 7 (local) / Valkey (ElastiCache) | BSD / BSD | 0 | ElastiCache cobra o nó. |
| Keycloak | Apache 2.0 | 0 | Só local. |
| LocalStack | imagem community | 0 | Não depender de recurso Pro. |
| Grafana `otel-lgtm`, k6 | AGPL | 0 | Só dev e teste. Não entram na imagem do serviço. |
| Testcontainers, ArchUnit, JUnit, springdoc | Apache / EPL conforme o projeto | 0 | |
| Terraform CLI | BSL (HashiCorp) | 0 para este uso | A CLI não entra no runtime. OpenTofu (MPL) é a saída se a BSL for impeditiva. [ADR 0010](04-adr/0010-terraform-iac.md). |
| Cognito, WAF, CloudFront, o resto da AWS | serviço | infra, não licença | Já está nas tabelas. |

Stack open source de EKS (Karpenter, Argo, Kyverno, Istio, Prometheus, Loki, Tempo, Grafana) também é licença zero de runtime e custo de nó mais gente. Esse custo está na seção 8 e no [documento 13](13-ecs-vs-eks.md), não numa linha de licença escondida.

Se alguém adicionar Oracle JDBC comercial, Debezium com plataforma paga, Datadog como agente obrigatório da imagem, ou LocalStack Pro como dependência do `make up`, a premissa desta seção deixa de valer e a estimativa ganha uma linha nova.

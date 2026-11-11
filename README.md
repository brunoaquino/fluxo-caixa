# Fluxo de Caixa

Sistema para um comerciante registrar créditos e débitos e consultar o saldo diário consolidado. A escrita do lançamento não depende do serviço de relatório: se o consolidado cair, o caixa continua aceitando movimento, e o saldo alcança o fato quando o consumidor volta.

O requisito que organiza a arquitetura é este: o controle de lançamentos permanece disponível se o consolidado diário cair; em pico, o consolidado atende **50 requisições por segundo** com **no máximo 5% de perda** (meta interna: 1%).

## Documentos entregáveis (PDF)

A documentação final do desafio está em PDF, diagramada e com sumário, na pasta [`documentos-entregaveis-desafio/`](documentos-entregaveis-desafio/README.md). Para ler tudo de uma vez, abra a [documentação completa](documentos-entregaveis-desafio/fluxo-caixa-documentacao-completa.pdf). Os documentos também estão separados por tema (requisitos, DDD, arquitetura e C4, ADRs, segurança, observabilidade, custos, transição, testes, entre outros).

A pasta [`documentos-brutos/`](documentos-brutos/README.md) contém os **documentos brutos**: as fontes em Markdown, com os diagramas em `documentos-brutos/assets/diagramas/`, a partir das quais os PDFs são gerados (`make pdf`). Elas ficam versionadas junto com o código e prevalecem se houver divergência. A correspondência entre cada PDF e sua fonte está na seção [Documentação](#documentação).

| Pasta | O que é | Para quem |
|---|---|---|
| [`documentos-entregaveis-desafio/`](documentos-entregaveis-desafio/README.md) | Entregáveis finais em PDF, com capa, sumário e diagramas | Avaliação e leitura |
| [`documentos-brutos/`](documentos-brutos/README.md) | Fontes em Markdown, ADRs, blueprint, Structurizr e diagramas | Manutenção e revisão no GitHub |

## Como o requisito é atendido

O `lancamentos-service` não chama o `consolidado-service`. O POST grava o lançamento e o evento na mesma transação (tabela `outbox_eventos`) e responde. Um relay publica no tópico cujo ARN vem de `LANCAMENTOS_TOPIC_ARN`. O consolidado consome a fila em `CONSOLIDADO_QUEUE_URL` (retenção de 14 dias, DLQ após 5 tentativas), aplica a soma uma única vez por `eventId` e serve a leitura por uma projeção em Postgres mais cache. No laboratório os nomes são `fluxo-caixa-lancamentos-events` e `consolidado-lancamentos-queue`. Na AWS o nome leva o prefixo do ambiente (`fluxo-caixa-dev-` ou `fluxo-caixa-prod-`); o serviço não tem nome de fila fixo no código. A conta de capacidade (Little: 2,8 requisições em voo no pico, duas tasks, cerca de 17% de utilização, 0,16 conexão de banco ativa) está em [documentos-brutos/01-contexto-e-requisitos.md](documentos-brutos/01-contexto-e-requisitos.md#analise-do-rnf-critico).

![Containers do Fluxo de Caixa: o comerciante passa por CloudFront com WAF, API Gateway REST regional e ALB interno até o lancamentos-service e o consolidado-service; o lancamentos-service grava fato e outbox no Postgres de lançamentos e publica no SNS, que entrega na SQS consumida pelo consolidado-service, que atualiza a projeção no Postgres consolidado e usa cache Valkey/Redis; os dois serviços validam o JWT de novo no Cognito ou Keycloak](documentos-brutos/assets/diagramas/d01-containers.svg)

## Stack

| Peça | Escolha |
|---|---|
| Aplicação | Java 25, Spring Boot 4.0.9, Resilience4j 2.4.0, Maven wrapper, um projeto por serviço |
| Arquitetura | Dois microsserviços, hexagonal, CQRS, outbox, consumidor idempotente |
| Dados | PostgreSQL 17, um banco por serviço, Flyway. RDS Multi-AZ em produção. Réplica de leitura desligada até gatilho |
| Mensageria | SNS + SQS + DLQ. LocalStack no laboratório. AWS SDK v2. Trace propagado por atributo |
| Cache | Redis 7 local, Valkey no ElastiCache, só no consolidado |
| Segurança | OAuth2/OIDC. Cognito na AWS, Keycloak no local. JWT validado no serviço. Rotação de senha do RDS |
| Borda | CloudFront + WAF + API Gateway REST regional + VPC Link v2 + ALB interno, região `us-east-1` (VPC Origin direto como alternativa) |
| Execução | ECS Fargate (ARM). EKS é evolução, não o deploy de hoje |
| Observabilidade | OpenTelemetry. ADOT, CloudWatch e X-Ray na AWS. Grafana LGTM no local |
| Infra | Terraform (provider AWS 6.x), ambientes `dev`, `hml` e `prod`, state em S3 |

Licença de runtime: nenhuma paga. A estimativa em `us-east-1`, com os três ambientes ligados o mês inteiro e o API Gateway REST ligado, fica em cerca de **USD 143/mês** (dev), **USD 356/mês** (hml) e **USD 777/mês** (prod). A mesma produção em `sa-east-1`, com o mesmo unitário de request da REST API (não reconsultado naquela região nesta passagem), sairia cerca de **USD 1.467/mês**. Local continua **USD 0**. Detalhe em [documentos-brutos/08-custos.md](documentos-brutos/08-custos.md).

## Estrutura do repositório

```text
fluxo-caixa/
├── README.md
├── Makefile
├── documentos-entregaveis-desafio/ # entregáveis finais em PDF
├── documentos-brutos/             # documentos brutos: fontes Markdown, ADRs e diagramas
├── services/
│   ├── lancamentos-service/       # porta 8081
│   └── consolidado-service/       # porta 8082
├── infra/
│   ├── terraform/                 # dev, hml e prod
│   └── local/                     # docker-compose
├── tests/
│   ├── load/                      # k6
│   └── e2e/                       # smoke.sh e resiliencia.sh
├── tools/pdf/                     # gerador dos PDFs (pandoc + Chrome)
└── .github/workflows/
```

O contrato compartilhado (paths, evento, nomes de fila, variáveis, portas) está em [documentos-brutos/blueprint-tecnico.md](documentos-brutos/blueprint-tecnico.md). A narrativa de arquitetura começa em [documentos-brutos/README.md](documentos-brutos/README.md).

## Pré-requisitos

- Java 25 (Temurin).
- Docker com Compose v2.
- Make.
- Opcional: k6 (`make load`), Terraform e AWS CLI (deploy).

O Maven não precisa estar instalado: cada serviço traz `mvnw`.

## Subir no local

Na raiz do repositório:

```bash
make infra-up
make up
```

`make infra-up` sobe a infraestrutura local. `make up` sobe os dois serviços.

| Serviço | URL |
|---|---|
| Keycloak | http://localhost:8080 (realm `fluxo-caixa`) |
| Lançamentos | http://localhost:8081 |
| Consolidado | http://localhost:8082 |
| Swagger lançamentos | http://localhost:8081/swagger-ui.html |
| Swagger consolidado | http://localhost:8082/swagger-ui.html |
| OpenAPI | http://localhost:8081/v3/api-docs e http://localhost:8082/v3/api-docs |
| Grafana (otel-lgtm) | http://localhost:3000 |
| Postgres | localhost:5432, bancos `lancamentos` e `consolidado` |
| Redis | localhost:6379 |
| LocalStack | http://localhost:4566 |

Usuário de laboratório: `comerciante1` / `comerciante1`, com `comerciante_id` `11111111-1111-1111-1111-111111111111`. Quando o Grafana pedir senha, a padrão da imagem `grafana/otel-lgtm` é `admin` / `admin`.

Para derrubar: `make down`.

### Token

```bash
TOKEN=$(make token)
```

`make token` imprime o access token do `comerciante1` no stdout (client `fluxo-caixa-app`, direct access grant no Keycloak local).

### Registrar um crédito

```bash
curl -sS -X POST http://localhost:8081/api/v1/lancamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão","categoria":"VENDAS"}'
```

A primeira resposta é **201**. Repetir com a mesma `Idempotency-Key` e o mesmo corpo devolve **200** e o mesmo recurso, sem segundo lançamento. O `comercianteId` não vai no JSON: o serviço lê o claim do token.

### Consultar o lançamento e o dia

```bash
curl -sS http://localhost:8081/api/v1/lancamentos/COLE_O_ID \
  -H "Authorization: Bearer $TOKEN"

curl -sS "http://localhost:8081/api/v1/lancamentos?data=2026-11-10&page=0&size=50" \
  -H "Authorization: Bearer $TOKEN"
```

### Estornar

```bash
curl -sS -X POST http://localhost:8081/api/v1/lancamentos/COLE_O_ID/estornos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"motivo":"Valor digitado errado"}'
```

O estorno é um lançamento novo, de tipo oposto, na mesma data de competência. O original não muda.

### Saldo consolidado

O saldo é eventual (meta p95 abaixo de 5 s). Se o GET vier zerado logo depois do POST, espere um instante e repita.

```bash
curl -sS http://localhost:8082/api/v1/consolidados/diario/2026-11-10 \
  -H "Authorization: Bearer $TOKEN"

curl -sS "http://localhost:8082/api/v1/consolidados/diario?inicio=2026-11-01&fim=2026-11-10" \
  -H "Authorization: Bearer $TOKEN"
```

Dia sem movimento responde **200** com zeros. Período maior que 366 dias responde **400** `application/problem+json`.

Escopos exigidos: `lancamentos:write`, `lancamentos:read`, `consolidado:read`. No local o prefixo é vazio. Na AWS a autoridade leva o prefixo `fluxo-caixa/`.

## Testes

```bash
make test          # unidade, ArchUnit e integração (Testcontainers)
make smoke         # jornada ponta a ponta
make resiliencia   # consolidado parado, POST segue, fila recupera
make load          # k6, opcional
make tf-validate   # Terraform fmt e validate
```

O que cada camada cobre está em [documentos-brutos/11-testes.md](documentos-brutos/11-testes.md).

## Deploy na AWS

Produção em `us-east-1`: CloudFront com WAF, API Gateway REST regional com WAF próprio e VPC Link v2 até um ALB interno, VPC em três zonas, ECS Fargate com no mínimo duas tasks por serviço, RDS PostgreSQL separado e Multi-AZ para cada serviço, ElastiCache com réplica, SNS/SQS/DLQ, Cognito, Secrets Manager com rotação e ADOT. Há também `dev` e `hml`, menores. `enable_api_gateway = false` tira o gateway e volta o CloudFront à VPC Origin. O passo a passo de apply, state e variáveis fica em [infra/terraform/README.md](infra/terraform/README.md). A topologia e o DR estão em [documentos-brutos/03-arquitetura/deployment-aws.md](documentos-brutos/03-arquitetura/deployment-aws.md). O deploy da aplicação é rolling com circuit breaker e rollback ([ADR 0020](documentos-brutos/04-adr/0020-estrategia-de-deploy.md)), promovendo a mesma imagem de dev para hml e prod.

## Documentação

Cada tema tem o PDF entregável, em [`documentos-entregaveis-desafio/`](documentos-entregaveis-desafio/README.md), e o documento bruto em Markdown que o origina, em [`documentos-brutos/`](documentos-brutos/README.md). O volume único com tudo é a [documentação completa em PDF](documentos-entregaveis-desafio/fluxo-caixa-documentacao-completa.pdf).

| Tema | PDF entregável | Documento bruto | Conteúdo |
|---|---|---|---|
| Visão geral | [00](documentos-entregaveis-desafio/00-visao-geral-e-guia-de-execucao.pdf) | [README](README.md) e [índice](documentos-brutos/README.md) | Solução, execução local e mapa dos requisitos |
| Requisitos | [01](documentos-entregaveis-desafio/01-contexto-e-requisitos.pdf) | [01-contexto-e-requisitos.md](documentos-brutos/01-contexto-e-requisitos.md) | RF, RNF e capacidade |
| Domínio (DDD) | [02](documentos-entregaveis-desafio/02-dominio-e-capacidades-ddd.pdf) | [02-dominio-ddd.md](documentos-brutos/02-dominio-ddd.md) | Capacidades, context map, evento |
| Arquitetura e C4 | [03](documentos-entregaveis-desafio/03-arquitetura-alvo-c4-e-fluxos.pdf) | [03-arquitetura/](documentos-brutos/03-arquitetura/README.md) | C4, fluxos, integração, AWS, Structurizr |
| ADRs | [04](documentos-entregaveis-desafio/04-decisoes-arquiteturais-adrs.pdf) | [04-adr/](documentos-brutos/04-adr/README.md) | Decisões de 0001 a 0031 |
| RNF operacionais | [05](documentos-entregaveis-desafio/05-requisitos-nao-funcionais.pdf) | [05-requisitos-nao-funcionais.md](documentos-brutos/05-requisitos-nao-funcionais.md) | SLO, error budget, k6 |
| Segurança | [06](documentos-entregaveis-desafio/06-seguranca.pdf) | [06-seguranca.md](documentos-brutos/06-seguranca.md) | STRIDE, OWASP API, LGPD, rotação |
| Observabilidade | [07](documentos-entregaveis-desafio/07-observabilidade-e-monitoramento.pdf) | [07-observabilidade.md](documentos-brutos/07-observabilidade.md) | Trace ponta a ponta, alarmes, runbooks |
| Custos | [08](documentos-entregaveis-desafio/08-estimativa-de-custos.pdf) | [08-custos.md](documentos-brutos/08-custos.md) | USD e BRL, dev, hml e prod |
| Transição | [09](documentos-entregaveis-desafio/09-arquitetura-de-transicao.pdf) | [09-arquitetura-de-transicao.md](documentos-brutos/09-arquitetura-de-transicao.md) | Strangler a partir do legado |
| Evoluções | [10](documentos-entregaveis-desafio/10-evolucoes-futuras.pdf) | [10-evolucoes-futuras.md](documentos-brutos/10-evolucoes-futuras.md) | O que ficou de fora e por quê |
| Testes | [11](documentos-entregaveis-desafio/11-testes.pdf) | [11-testes.md](documentos-brutos/11-testes.md) | Pirâmide e comandos |
| Desempenho | [12](documentos-entregaveis-desafio/12-desempenho-e-dados.pdf) | [12-desempenho-e-dados.md](documentos-brutos/12-desempenho-e-dados.md) | Índices, réplica, cache, lake |
| ECS e EKS | [13](documentos-entregaveis-desafio/13-ecs-vs-eks.pdf) | [13-ecs-vs-eks.md](documentos-brutos/13-ecs-vs-eks.md) | Critério, stack e preço de observar |
| CI/CD | [14](documentos-entregaveis-desafio/14-cicd-e-qualidade.pdf) | [14-cicd-e-qualidade.md](documentos-brutos/14-cicd-e-qualidade.md) | Pipeline e ferramentas |
| Blueprint | [15](documentos-entregaveis-desafio/15-blueprint-tecnico.pdf) | [blueprint-tecnico.md](documentos-brutos/blueprint-tecnico.md) | Contrato: nomes, portas, evento, SLOs |
| Serviços e local | [16](documentos-entregaveis-desafio/16-servicos-e-ambiente-local.pdf) | READMEs de [lançamentos](services/lancamentos-service/README.md), [consolidado](services/consolidado-service/README.md) e [infra local](infra/local/README.md) | Guia dos serviços e laboratório |
| Terraform | [17](documentos-entregaveis-desafio/17-infraestrutura-terraform.pdf) | [infra/terraform/README.md](infra/terraform/README.md) e módulos | Stack, ambientes e módulos |

Depois de alterar um documento bruto, rode `make pdf` para atualizar os PDFs.

## Saúde dos processos

O ALB usa readiness. No local:

- http://localhost:8081/actuator/health/liveness
- http://localhost:8081/actuator/health/readiness
- http://localhost:8082/actuator/health/readiness

Métrica Prometheus: `/actuator/prometheus` em cada serviço (não publicada na internet em produção).

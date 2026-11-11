# Documentação de entrega — Fluxo de Caixa

Índice da documentação de arquitetura do desafio de Arquiteto de Soluções. O contrato executável (nomes, portas, eventos, recursos AWS e metas de SLO) está no [blueprint técnico](blueprint-tecnico.md). Esta pasta explica o porquê, os trade-offs e como operar a solução. Em caso de divergência de contrato, vale o blueprint.

| Documento | O que responde |
|---|---|
| [01 — Contexto e requisitos](01-contexto-e-requisitos.md) | Negócio, glossário, RF com aceite, RNF mensuráveis, cálculo de capacidade do pico de 50 req/s |
| [02 — Domínio (DDD)](02-dominio-ddd.md) | Capacidades, subdomínios, context map, event storming, agregados, modelos de dados, contrato do evento |
| [03 — Arquitetura](03-arquitetura/README.md) | C4, Structurizr, estilos, fluxos e implantação AWS |
| [04 — ADRs](04-adr/README.md) | Decisões no formato MADR, de 0001 a 0031, datadas em 2026-11-10 |
| [05 — Requisitos não funcionais](05-requisitos-nao-funcionais.md) | SLI/SLO, error budget, escala, resiliência, plano k6, rastreabilidade |
| [06 — Segurança](06-seguranca.md) | STRIDE, OAuth2, multi-tenant, OWASP API, criptografia, LGPD, rotação de segredos |
| [07 — Observabilidade](07-observabilidade.md) | Trace W3C ponta a ponta, logs, RED/USE, alarmes, runbooks, Grafana e X-Ray |
| [08 — Custos](08-custos.md) | dev, hml e prod em `us-east-1`, a mesma conta em `sa-east-1`, EKS e SaaS |
| [09 — Arquitetura de transição](09-arquitetura-de-transicao.md) | Strangler Fig a partir de planilha ou ERP monolítico |
| [10 — Evoluções futuras](10-evolucoes-futuras.md) | Roadmap: multi-moeda, fechamento, Open Finance, multi-região, Kafka |
| [11 — Testes](11-testes.md) | Pirâmide, como executar smoke, resiliência e carga |
| [12 — Desempenho e dados](12-desempenho-e-dados.md) | Índices, keyset, réplica, cache, Aurora, DynamoDB, lake |
| [13 — ECS e EKS](13-ecs-vs-eks.md) | Quando o EKS passa a valer, stack open source, Karpenter, preço de observar |
| [14 — CI/CD e qualidade](14-cicd-e-qualidade.md) | Pipeline, ferramentas e o que muda com o EKS |

## Como ler se o tempo for curto

1. [README da raiz](../README.md) — como subir e o que a solução faz.
2. [Arquitetura-alvo](03-arquitetura/arquitetura-alvo.md) — estilos e o isolamento do RNF crítico.
3. [ADR 0002](04-adr/0002-microsservicos-por-bounded-context.md), [ADR 0003](04-adr/0003-cqrs-e-integracao-assincrona.md) e [ADR 0004](04-adr/0004-transactional-outbox.md) — o núcleo da decisão.
4. [Custos](08-custos.md) — o número mensal e o que realmente o compõe (NAT, endpoints, RDS), em `us-east-1`.

## Mapa dos requisitos do desafio

| Exigência | Onde está atendida |
|---|---|
| Mapeamento de domínios e capacidades | [02](02-dominio-ddd.md) |
| Refinamento de requisitos funcionais e não funcionais | [01](01-contexto-e-requisitos.md), [05](05-requisitos-nao-funcionais.md) |
| Desenho da solução (arquitetura-alvo) | [03](03-arquitetura/README.md) |
| Justificativa de ferramentas, tecnologias e estilo | [04](04-adr/README.md), [03 — arquitetura-alvo](03-arquitetura/arquitetura-alvo.md) |
| Testes | [11](11-testes.md), `tests/e2e`, `tests/load` |
| README com execução local | [README da raiz](../README.md) |
| Documentação no repositório | esta pasta |
| Arquitetura de transição (diferencial) | [09](09-arquitetura-de-transicao.md) |
| Estimativa de custos e licenças (diferencial) | [08](08-custos.md) |
| Monitoramento e observabilidade (diferencial) | [07](07-observabilidade.md) |
| Critérios de segurança para integração (diferencial) | [06](06-seguranca.md) |
| RNF crítico: lançamentos independe do consolidado; 50 req/s com perda ≤ 5% | [01 § análise de capacidade](01-contexto-e-requisitos.md#analise-do-rnf-critico), [05](05-requisitos-nao-funcionais.md) |

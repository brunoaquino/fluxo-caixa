# Documentos entregáveis do desafio (PDF)

Versão final, diagramada e pronta para leitura, de toda a documentação do desafio de Arquiteto de Soluções. Cada PDF tem capa, sumário com paginação, marcadores de navegação e links clicáveis entre documentos.

Os textos-fonte ficam em [`documentos-brutos/`](../documentos-brutos/README.md), que é a documentação bruta em Markdown, versionada junto com o código. Os PDFs desta pasta são gerados a partir dela com `make pdf`. Se houver divergência, a fonte em `documentos-brutos/` prevalece e os PDFs devem ser regerados.

## Leitura recomendada

1. [Documentação completa](fluxo-caixa-documentacao-completa.pdf): volume único com todos os documentos abaixo, separados por partes.
2. [00 — Visão geral e guia de execução](00-visao-geral-e-guia-de-execucao.pdf): o que a solução faz, como rodar e onde cada requisito está atendido.
3. [03 — Arquitetura alvo, C4 e fluxos](03-arquitetura-alvo-c4-e-fluxos.pdf) e [04 — ADRs](04-decisoes-arquiteturais-adrs.pdf): o desenho e o porquê de cada decisão.

## Documentos

| PDF | Conteúdo | Requisito do desafio |
|---|---|---|
| [00 — Visão geral e guia de execução](00-visao-geral-e-guia-de-execucao.pdf) | Resumo da solução, stack, execução local, testes, deploy e mapa dos requisitos | README com instruções |
| [01 — Contexto e requisitos](01-contexto-e-requisitos.pdf) | Negócio, glossário, RF com critérios de aceite, RNF mensuráveis e cálculo de capacidade do pico | Refinamento de requisitos funcionais e não funcionais |
| [02 — Domínio e capacidades (DDD)](02-dominio-e-capacidades-ddd.pdf) | Capacidades, subdomínios, bounded contexts, context map, event storming, agregados e modelos de dados | Mapeamento de domínios funcionais e capacidades |
| [03 — Arquitetura alvo, C4 e fluxos](03-arquitetura-alvo-c4-e-fluxos.pdf) | Estilos arquiteturais, C4 do contexto ao código, fluxos de dados, padrões de integração e implantação AWS | Desenho da solução completo (arquitetura alvo) |
| [04 — Decisões arquiteturais (ADRs)](04-decisoes-arquiteturais-adrs.pdf) | 31 ADRs com contexto, alternativas e consequências | Justificativa de ferramentas, tecnologias e arquitetura |
| [05 — Requisitos não funcionais](05-requisitos-nao-funcionais.pdf) | SLI/SLO, error budget, escala, resiliência e plano de carga | RNF: disponibilidade e 50 req/s com perda ≤ 5% |
| [06 — Segurança](06-seguranca.pdf) | STRIDE, OAuth2/OIDC, multi-tenant, OWASP API, criptografia, LGPD e rotação de segredos | Critérios de segurança para consumo de serviços (diferencial) |
| [07 — Observabilidade e monitoramento](07-observabilidade-e-monitoramento.pdf) | Trace W3C ponta a ponta, logs, métricas, alarmes e runbooks | Monitoramento e observabilidade (diferencial) |
| [08 — Estimativa de custos](08-estimativa-de-custos.pdf) | dev, hml e prod em us-east-1, comparação com sa-east-1, EKS e SaaS | Estimativa de custos de infraestrutura e licenças (diferencial) |
| [09 — Arquitetura de transição](09-arquitetura-de-transicao.pdf) | Strangler Fig a partir de planilha ou ERP legado | Arquitetura de transição (diferencial) |
| [10 — Evoluções futuras](10-evolucoes-futuras.pdf) | Roadmap e o que ficou de fora, com o motivo | Evoluções futuras (observações do desafio) |
| [11 — Estratégia de testes](11-testes.pdf) | Pirâmide de testes, smoke, resiliência, carga e evidências | Testes |
| [12 — Desempenho e dados](12-desempenho-e-dados.pdf) | Índices, keyset, réplica de leitura, cache e evolução do armazenamento | RNF de desempenho |
| [13 — ECS e EKS](13-ecs-vs-eks.pdf) | Critério de migração, stack open source, Karpenter e custo de observabilidade | Justificativa de tecnologia |
| [14 — CI/CD e qualidade](14-cicd-e-qualidade.pdf) | Pipeline, ferramentas de qualidade e segurança, GitOps | Qualidade e entrega |
| [15 — Blueprint técnico](15-blueprint-tecnico.pdf) | Contrato compartilhado: nomes, portas, evento, recursos AWS e SLOs | Referência técnica |
| [16 — Serviços e ambiente local](16-servicos-e-ambiente-local.pdf) | Guia dos dois microsserviços, compatibilidade de versões, consultas, laboratório local e k6 | Execução local e código |
| [17 — Infraestrutura (Terraform)](17-infraestrutura-terraform.pdf) | Stack, ambientes dev/hml/prod, state remoto e os 13 módulos | Infraestrutura como código |

## Como regerar

Requisitos: Python 3, [pandoc](https://pandoc.org) e Google Chrome (ou Chromium, apontado por `CHROME_BIN`).

```bash
make pdf
```

O alvo cria um ambiente virtual em `.venv-pdf` (usado para numerar o sumário) e executa `tools/pdf/build.py`. O catálogo de documentos fica em `tools/pdf/entregaveis.py` e o visual em `tools/pdf/estilo.css`.

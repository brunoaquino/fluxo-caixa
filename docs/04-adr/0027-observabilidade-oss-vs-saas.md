# Observabilidade: gerenciada agora, SaaS ou self-hosted depois

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0013](0013-observabilidade-otel.md)

## Contexto e problema

O sinal é OpenTelemetry nos dois desenhos (local e AWS). O destino é que muda o custo e o plantão. CloudWatch e X-Ray já estão no caminho do ADOT. Grafana Cloud, Datadog, New Relic, Dynatrace e uma stack self-hosted no EKS resolvem o mesmo problema com outra fatura e outra pessoa de plantão. O volume de hoje é pequeno; a escolha errada ou é um piso de SaaS ocioso ou é um Prometheus que ninguém atualiza.

## Drivers

- Um destino só para log, métrica e trace, com correlação ([ADR 0025](0025-rastreabilidade-contexto.md)).
- Custo no volume real (4 tasks, ~20 GB de log/mês, ~2.000 séries) e no volume de 30 serviços.
- Custo de gente na conta. Premissa de planejamento: USD 8.000/mês carregados por engenheiro sênior.
- Não operar Loki sem o time que já opera cluster.

## Opções consideradas

### 1. ADOT, CloudWatch e X-Ray

É o ADR 0013. Cerca de USD 45/mês na estimativa de produção.

- Positivo: sem contrato novo, sem agente extra na imagem, alarme no mesmo SNS operacional. O trace já tem caminho para o X-Ray.
- Negativo: PromQL não é a língua do CloudWatch. Métrica customizada a USD 0,30 por métrica/mês pune cardinalidade. Log acima de ~100 GB/mês, a USD 0,50/GB de ingestão em `us-east-1`, deixa de ser “incluso no ruído”.

### 2. SaaS (Grafana Cloud, Datadog, New Relic, Dynatrace)

No volume deste projeto, a lista de 2026-11-10 fica entre cerca de USD 19 (Grafana Cloud Pro, dentro da franquia) e cerca de USD 52 (Datadog no SKU serverless). Números e premissas no [documento 13](../13-ecs-vs-eks.md) e no [documento 08](../08-custos.md).

- Positivo: alguém atualiza o armazenamento. Correlação de log e trace é o produto. Grafana Cloud é o salto menor, porque o local já é Grafana.
- Negativo: dado de telemetria (com `comercianteId`) sai para outro operador. É outra transferência, com o DPA daquele fornecedor, não coberto pelo DPA da AWS. O contrato precede o agente.

### 3. Stack self-hosted (Prometheus ou Mimir, Loki, Tempo, Grafana)

Cerca de USD 136/mês de nó e disco no cenário do documento 08, mais fração de pessoa.

- Positivo: o dado de telemetria fica na conta. A língua é a mesma do laboratório.
- Negativo: USD 2.000 de um quarto de engenheiro superam USD 136 de nó e superam o SaaS. Sem o plantão, a stack cai e o caixa fica cego — pior do que um CloudWatch sem PromQL.

## Decisão

Opção 1, agora.

Grafana Cloud é a primeira alternativa no dia em que o time quiser PromQL ou o log passar de ~100 GB/mês, **e** o DPA do fornecedor estiver aceito. Datadog, New Relic e Dynatrace entram na mesma conversa quando o valor pedido for APM com perfil ou análise de causa, não quando o valor pedido for “ver o trace”: isso o X-Ray e o Tempo local já fazem. A comparação de 30 serviços está no documento 13 para essa conversa não usar o preço de 4 tasks.

Self-hosted entra junto com o EKS, quando o [ADR 0026](0026-ecs-agora-eks-evolucao.md) disparar e já existir quem atualize o cluster. Instalar a stack antes do time é adotá-la duas vezes: uma agora, outra quando o cluster existir.

## Consequências

### Positivas

- Um destino, já instrumentado. O dinheiro de observabilidade neste volume é ruído perto de NAT e RDS.
- A troca futura não mexe no código de negócio: o SDK é OpenTelemetry. Muda o exporter.

### Negativas

- Investigação rica (PromQL, exemplars) fica mais pobre no CloudWatch do que no Grafana. Aceito no tamanho atual.
- A estimativa de USD 45 é ordem de grandeza. A primeira fatura calibra. Se vier o dobro, a conversa de Grafana Cloud antecipa, sem esperar os 30 serviços.

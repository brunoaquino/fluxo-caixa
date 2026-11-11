# ECS agora, EKS como evolução

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0009](0009-ecs-fargate.md), que permanece aceito

## Contexto e problema

O ADR 0009 colocou os dois serviços em ECS Fargate. A pergunta volta porque a comparação com Kubernetes é o que uma banca espera ver respondida com número, não com preferência. EKS tem control plane, ecossistema e uma stack que o ECS não pede. Também tem um piso.

## Drivers

- Dois serviços, um time, pico que cabe em quatro tasks.
- Custo de control plane explícito: USD 0,10/hora, **USD 73/mês** por cluster, nas duas regiões (Price List, 2026-11-10).
- Operação que o time consegue fazer sem um time de plataforma.
- Porta de saída no dia em que o número de serviços ou de times mudar o desenho.

## Comparação

| Eixo | ECS Fargate | EKS |
|---|---|---|
| Control plane | incluso no preço da task | USD 73/mês por cluster, mesmo vazio |
| Onde o processo roda | task, sem nó | nó (ou Fargate profile, que devolve o preço de Fargate e tira o bin packing) |
| Produção deste projeto | USD 778/mês na conta base | USD 950/mês com 3 × `m7g.large`, mesma rede e mesmos dados ([custos](../08-custos.md)) |
| Operação | service, task definition, target group | nó, NodePool, add-on, versão do control plane, upgrade com extended support se atrasar |
| Curva | o time que já escreve o Terraform de ECS opera o deploy | pede quem já operou Kubernetes, ou tempo para aprender no sistema de caixa |
| Ecossistema | o da AWS (autoscaling de service, circuit breaker de deploy) | o da CNCF, listado no [documento 13](../13-ecs-vs-eks.md) |
| Portabilidade | imagem OCI, que também roda no EKS | manifesto Kubernetes, que não roda no ECS |
| Bin packing | a task reserva 0,5 ou 1 vCPU o mês inteiro, ociosa ou não | vários processos no mesmo nó, com o desperdício só da folga do nó |
| Autoscaling | CPU, request do ALB, backlog da fila, nativo | HPA, e KEDA para a fila; Karpenter para o nó |
| Service mesh | não há, e não falta: os serviços não se chamam | Istio ou Linkerd quando houver chamada síncrona interna |
| GitOps | o pipeline faz push do novo task definition | ArgoCD puxa o manifesto ([ADR 0028](0028-gitops-argocd-evolucao.md)) |

A densidade só paga o control plane quando há o que empacotar. Quatro tasks pequenas em três nós `m7g.large` deixam nó sobrando e ainda pagam USD 73. O bin packing começa a favorescer o EKS quando dezenas de processos dividem o nó. A conta de 30 serviços está no documento 13.

## Opções consideradas

### 1. EKS agora

- Positivo: o manifesto nasce no formato da evolução. Não há migração depois.
- Negativo: USD 73 mais três nós (USD 179) no lugar de USD 87 de Fargate, mais a stack se alguém “já que tem cluster” instalar Istio e Prometheus. Dois serviços não usam o que o cluster oferece. O risco é operar a plataforma em vez do caixa.

### 2. ECS agora, EKS quando o gatilho disparar

- Positivo: a fatura e a operação cabem no time. A imagem OCI não se joga fora na migração: o que muda é o scheduler e o deploy.
- Negativo: o dia da migração existe. Task definition não vira Deployment sozinha. O [documento 13](../13-ecs-vs-eks.md) é o mapa dessa migração, para ela não ser inventada na pressa.

### 3. ECS em todo o horizonte

- Positivo: nunca se paga control plane.
- Negativo: trava a resposta. Com muitos times, o ECS sem uma plataforma interna vira um Terraform por time e um pipeline por serviço, que é uma plataforma pior, só que sem o nome.

## Decisão

Opção 2. ECS Fargate ARM, como no ADR 0009, para os dois serviços.

**EKS passa a valer** quando a maioria destes for verdadeira:

- mais de **8 a 10** serviços deployáveis, ou mais de um time publicando com calendário próprio;
- necessidade de plataforma interna (template de serviço, política de admissão, GitOps) que o time aceite operar;
- bin packing ou escala por fila (KEDA) que o autoscaling do ECS já não expresse sem contorção.

Um desses sozinho não basta. Dez serviços de um time só ainda podem viver em ECS. Dois serviços com vontade de Istio não justificam o cluster.

Até o gatilho, não se cria “um EKS pequeno para ir aprendendo” em prod. Aprender é cluster efêmero, destruído, fora da conta do caixa.

## Consequências

### Positivas

- A decisão de hoje tem preço (USD 778 contra USD 950) e um número de serviços que a reabre.
- A stack open source fica especificada antes de alguém instalar o chart da moda. Especificada não é instalada.

### Negativas

- Quem comparar só o ecossistema, sem a fatura e sem o tamanho do time, vai achar a decisão tímida. O documento 13 existe para essa conversa ter os dois lados.

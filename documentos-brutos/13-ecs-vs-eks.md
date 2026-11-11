# 13 — ECS agora, EKS quando couber

Decisão: ECS Fargate para os dois serviços. EKS é evolução com gatilho, não uma preferência adiada por falta de opinião. O [ADR 0026](04-adr/0026-ecs-agora-eks-evolucao.md) registra a escolha. Este documento é a comparação que o ADR não cabe, a stack que só existe se o EKS existir, e o preço de observar o sistema de quatro jeitos.

Números de infra repetem o [documento 08](08-custos.md). Se divergirem, vale o 08 e este texto se corrige.

## 1. O critério

ECS permanece enquanto forem dois serviços e um time. EKS passa a valer quando a maioria for verdadeira:

- mais de **8 a 10** serviços, ou mais de um time com calendário de deploy próprio;
- uma plataforma interna que alguém aceite operar (template, política, GitOps);
- bin packing ou escala pela fila que o autoscaling do service ECS não expresse sem contorção.

Produção deste projeto em ECS: **USD 778/mês**. O equivalente em EKS, com a mesma rede e os mesmos dados, três nós `m7g.large` e o control plane: **USD 950/mês**. A diferença de USD 172 compra um control plane (USD 73) e nós que ficam folgados para quatro tasks. Não compra capacidade que o Fargate não tenha.

## 2. Comparação

| Eixo | ECS Fargate | EKS |
|---|---|---|
| Control plane | incluso | USD 0,10/hora = **USD 73/mês** por cluster, `us-east-1` e `sa-east-1` (Price List, vigência 2026-10-01) |
| Operação do nó | não há nó | AMI, disco, daemonset, CVE do nó, versão do Kubernetes, extended support se a versão atrasar |
| Curva | service e task definition | o acima, mais o ecossistema da seção 3 |
| Portabilidade | imagem OCI | manifesto. A imagem continua valendo; o deploy não |
| Densidade | a task paga 0,5 ou 1 vCPU o mês inteiro | vários pods por nó. Com 4 tasks, o nó é que fica ocioso |
| Autoscaling da aplicação | target tracking de CPU, request do ALB e backlog da fila | HPA, KEDA na fila, Karpenter no nó |
| Autoscaling da capacidade | a AWS provisiona o Fargate | Cluster Autoscaler ou Karpenter, ou Managed Node Group com tamanho fixo |
| Mesh | sem uso: os serviços não se chamam | Istio ou Linkerd quando houver chamada síncrona interna |
| GitOps | push do Actions ([ADR 0028](04-adr/0028-gitops-argocd-evolucao.md)) | ArgoCD em pull |
| Custo deste porte | USD 87 de Fargate dentro dos USD 778 | USD 73 + USD 179 de nó + USD 7 de disco, no lugar dos USD 87 |
| Falha de zona | task na outra AZ, sem o nó para drenar | o nó da zona sai, o pod reagenda, o volume pode não ir junto |

O que não entra na tabela como vantagem do EKS neste porte: service mesh, porque não há tráfego leste-oeste; Karpenter, porque três nós estáveis não se consolidam; Argo Rollouts, porque o circuit breaker do ECS já cobre o rollback do rolling.

## 3. Stack open source, se o EKS existir

Nada desta lista é instalado no ECS. A lista existe para o dia do gatilho não virar um conjunto de charts escolhidos pelo tutorial da semana. Cada linha diz a função e por que está aqui e não o vizinho.

| Peça | Função | Por que esta |
|---|---|---|
| Karpenter | Sobe e consolida nó | Reage em segundos e empacota por NodePool. Ver seção 5: não entra com três nós fixos. |
| ArgoCD | GitOps, app-of-apps, ApplicationSet | Um Application por serviço gerado pelo ApplicationSet, e um app-of-apps para a própria plataforma. Pull, com drift. [ADR 0028](04-adr/0028-gitops-argocd-evolucao.md). |
| Argo Rollouts | Canary e blue/green com análise | O passo além do rolling do ECS, quando houver métrica para promover (erro, lag). Sem a análise, canary é só tráfego partido. |
| KEDA | Escala o consumidor pela profundidade da fila SQS | O autoscaling do ECS já olha a fila. No EKS, o HPA de CPU não vê mensagem. KEDA vê. |
| External Secrets Operator | Materializa Secrets Manager em Secret | A rotação continua no Secrets Manager ([ADR 0024](04-adr/0024-rotacao-de-segredos.md)). O cluster não vira outro cofre. |
| cert-manager | Certificado dentro do cluster | O certificado público continua no ACM, na frente do CloudFront. cert-manager é para o que o ACM não cobre (webhook interno, mesh). |
| AWS Load Balancer Controller | ALB ou NLB a partir de Ingress | Sem ele, o Service do tipo LoadBalancer vira um balanceador por serviço. Com a borda no CloudFront, o alvo interno continua um ALB só. |
| ExternalDNS | Registro no Route 53 a partir do Ingress | Tira o registro da mão. Sem dono de DNS no time, é mais um reconciliador para vigiar. Entra com o cluster, não antes. |
| Kyverno ou OPA Gatekeeper | Política de admissão | Imagem só do ECR, sem `:latest` em prod, sem privileged, recurso com request e limit. Kyverno escreve a política em YAML, mais perto do manifesto. Gatekeeper escreve em Rego, mais expressivo e mais distante. Um dos dois, não os dois. |
| Istio ou Linkerd | mTLS e política de tráfego | Quando existir chamada síncrona entre serviços. Linkerd é o menor dos dois. Istio é o que se escolhe se a política de tráfego for o requisito, não só a identidade. Antes disso, security group e IAM na fila. Mesh em dois serviços que não se chamam é latência e um control plane a mais. |
| OpenTelemetry Collector | Recebe OTLP e exporta | O sidecar ADOT de hoje vira Collector no nó (DaemonSet) ou um gateway. O SDK da aplicação não muda. |
| Prometheus e Mimir, ou AMP | Métrica | Prometheus cabe no volume de 2.000 séries. Mimir (ou AMP) quando a retenção e a quantidade de séries passarem do que um Prometheus único aguenta. Não se opera os dois. |
| Grafana | Painel | O mesmo desenho do laboratório. |
| Loki | Log | Em S3, não em disco do nó, para o nó poder morrer. |
| Tempo | Trace | O equivalente self-hosted do X-Ray e do Tempo do `otel-lgtm`. |
| Alertmanager | Roteamento de alarme | O SNS continua sendo o destino que acorda gente. Alertmanager decide rota e silêncio; não substitui o tópico. |
| Velero | Backup de manifesto e, se houver, volume | O dado de negócio está no RDS, com PITR. Velero protege o cluster (manifesto, CRD), não o saldo. Sem ele, reconstruir o cluster é reinstalar a stack de memória. |
| Trivy Operator | CVE de imagem dentro do cluster | O CI já escaneia a imagem. O operator pega o que foi implantado fora do CI e o que envelheceu depois do scan. |
| Falco | Comportamento em runtime | Valor real quando há nó compartilhado por times. Com um time e dois serviços, é ruído. Entra com o multi-time, não no primeiro dia do cluster. |

## 4. Custos de observar

Fontes consultadas em **2026-11-10**:

- Grafana Cloud, grafana.com/pricing: plataforma Pro USD 19/mês; métrica USD 6,50 por mil séries ativas no primeiro degrau pago (10.001–100.000), com 10.000 inclusas; log e trace com escrita a partir de USD 0,40/GB, e 50 GB inclusos na plataforma.
- Datadog, datadoghq.com/pricing/list: Infrastructure Pro USD 15/host/mês no anual (USD 18 mês a mês); Container Monitoring USD 1/container/mês no anual; log ingerido USD 0,10/GB; log indexado, 15 dias, USD 1,70 por milhão no anual; métrica customizada USD 5 por 100/mês; Serverless Workload USD 3 e Serverless APM USD 6 por instância ativa/mês no anual.
- Dynatrace, dynatrace.com/pricing: full-stack USD 58 por 8 GiB/mês. Rate card: ingestão de log USD 0,20/GiB.
- AMP: USD 0,90 por 10 milhões de amostras no primeiro degrau, preço de lista público da página do Amazon Managed Service for Prometheus, não revalidado na Price List API nesta passagem. Amazon Managed Grafana: USD 9 por editor/mês, preço de lista público.
- CloudWatch Logs: USD 0,50/GB de ingestão em `us-east-1`, preço de lista público, também não revalidado na API nesta passagem.
- New Relic: a célula exata não foi recortada nesta consulta. A faixa pública recente de ingestão fica entre USD 0,30 e USD 0,55 por GB. A linha abaixo usa USD 0,40 e está marcada como a confirmar antes de contrato. Usuário full-platform, quando a conta cobra um, muda a conta mais do que o GB neste volume.

**Gente.** USD 8.000/mês carregados por engenheiro sênior, premissa de planejamento, não pesquisa salarial.

### Este projeto (produção)

Premissas: 4 tasks, 6 GiB de memória de task, 2.000 séries, 20 GB de log/mês, 5 GB de trace/mês, amostragem de 10%.

| Opção | USD/mês | Conta |
|---|---:|---|
| CloudWatch + X-Ray (já na fatura) | 45 | estimativa do documento 08 |
| Grafana Cloud Pro | 19 | plataforma; o uso cabe na franquia |
| AMP + 2 editores | ~34 | 172,8 milhões de amostras × 0,90 / 10 milhões = 15,55; editores 18. Log segue no CloudWatch |
| Datadog serverless, anual | ~52 | 4 × 3 + 4 × 6 + 20 × 0,10 + 5 milhões indexados × 1,70 |
| Dynatrace full-stack | ~48 | 6 GiB × (58/8) = 43,50, mais 20 × 0,20 |
| Self-hosted no EKS | 136 de nó | 2 × `m7g.large` (119,14) + 200 GB gp3 (16) + 50 GB S3 (1,15), e tira os 45 do CloudWatch se substituir de verdade |
| New Relic, ordem de grandeza | < 20 de ingestão | 25 GB × 0,40. O usuário full-platform, se cobrado, domina. A confirmar |

Um quarto de engenheiro (USD 2.000) é maior do que todas as linhas. Self-hosted a USD 136 de nó custa USD 2.136 com essa fração de pessoa. Grafana Cloud a USD 19 custa USD 19 se ninguém a mais for contratado para operá-la. Por isso o destino de agora é CloudWatch. Grafana Cloud é a troca quando o PromQL fizer falta e o DPA do fornecedor estiver aceito. Datadog e Dynatrace entram se o pedido for APM com o produto deles, não para “ter trace”.

### Trinta serviços

Premissas, de propósito redondas, para a comparação não depender de um serviço que não existe:

- 30 serviços, 60 pods, 12 nós `m7g.xlarge` (4 vCPU, 16 GiB) se o bin packing couber cerca de cinco pods por nó com folga.
- 45.000 séries ativas, scrape de 30 s.
- 30 GB de log por dia = 900 GB/mês. Trace: 50 GB/mês.
- Indexação de log no Datadog: 20 milhões de eventos/mês (erro e amostra), não o log inteiro.
- Métrica customizada faturável no Datadog: 5.000, depois da franquia por host. Emitir as 45.000 séries como custom metric seria outra ordem (centenas de dólares só nessa linha) e é exatamente o que não se faz.

| Opção | USD/mês | Leitura |
|---|---:|---|
| Grafana Cloud | ~590 | plataforma 19 + 35 mil séries × 6,50/mil = 227,50 + log acima de 50 GB × 0,40 = 340. Se processar e reter incidirem em cima da escrita, o teto do log sobe e o total vai para a casa de USD 700. A página lista os três medidores; a conta usa a escrita como piso. |
| Datadog, anual, sem APM de host | ~610 | 12 × 15 de infra + 60 × 1 de container + 900 × 0,10 de ingestão + 20 × 1,70 de índice + 50 × 5 de custom metric. APM por host está na mesma tabela e não foi recortado nesta consulta: soma por cima. No Fargate o SKU serverless (USD 6 de APM) não se aplica a nó EC2. |
| Dynatrace, host inteiro | ~1.570 | 12 × 16 GiB = 192 GiB, 192/8 × 58 = 1.392, mais 900 × 0,20 = 180. Se a cobrança fosse só a memória do pod (60 GiB), cairia para cerca de 435 + 180. A página cobra o host monitorado; a conta usa o host. |
| New Relic, só ingestão a 0,40 | ~380 | 950 GB × 0,40, mais usuário se a conta exigir. A confirmar. |
| AMP, teto no primeiro degrau | ~380 | 45.000 × 86.400 = 3,89 bilhões de amostras. No primeiro degrau, 388,8 × 0,90 ≈ 350, mais USD 27 de três editores. O degrau seguinte da página histórica é menor; o teto conservador é este. Log não está no AMP: 900 GB no CloudWatch a 0,50/GB = USD 450, e essa é a linha que dói. |
| Self-hosted | ~410 de infra | 3 × `m7g.xlarge` (3 × 0,1632 × 730 ≈ 357) + S3 na casa de USD 50 para o log do mês. Mais **0,5 engenheiro (USD 4.000)** se o time ainda não existir. |

Com o time de plataforma já pago por causa do EKS, o custo marginal da stack self-hosted é o nó (USD 410), e ela ganha do Dynatrace e compete com o Grafana Cloud. Sem esse time, os USD 4.000 de gente fazem o SaaS ganhar em todas as linhas. A escolha é pelo custo total, não pelo preço do GB.

| Situação | Escolha |
|---|---|
| Dois serviços, sem time de plataforma | CloudWatch e X-Ray |
| O mesmo time quer PromQL e aceita o DPA | Grafana Cloud |
| Trinta serviços, time de plataforma já existe por causa do EKS | self-hosted no cluster, log em S3 |
| O contrato pede APM com análise de causa pronta e há orçamento de plataforma | Dynatrace ou Datadog, com a linha de APM de host reconfirmada na tabela antes da assinatura |
| O critério é ingestão barata e o time aceita o modelo de usuário da New Relic | New Relic, depois de fechar a célula que esta consulta deixou em aberto |

## 5. Karpenter

**Quando adota.** Cluster com dezenas de pods, tamanho de nó que muda ao longo do dia, mistura de Spot e on-demand, e alguém para olhar disruption. Aí o Karpenter substitui o Cluster Autoscaler com Managed Node Group: provisiona o formato certo em vez de escalar um grupo homogêneo, e devolve nó ocioso.

**NodePool deste produto, no dia em que existir.**

- Um pool on-demand Graviton (`m7g`, `c7g`) com limite de CPU, para o mínimo de N−1 não depender de Spot.
- Um pool Spot Graviton, com tipos compatíveis com o pod (ARM64, o mesmo da imagem de hoje), para o consumidor e para dev.
- Consolidação ligada no pool Spot e conservadora no on-demand.
- Disruption budget: não esvaziar mais de um nó por vez, e não esvaziar durante a janela de fechamento. Sem o budget, a consolidação reinicia o consumidor no meio do catch-up.
- Pod de lançamentos e de consolidado com `topologySpreadConstraints` por zona, como o `spread` do ECS faz hoje.

**Quando não adota.** Poucos nós estáveis — o caso de três `m7g.large` o mês inteiro. Managed Node Group com tamanho fixo é mais simples e não fica consolidando à toa. Fargate profile também dispensa Karpenter, e dispensa junto o bin packing: volta o preço de Fargate, que é o que o ECS já cobra sem o control plane. Karpenter em cima de Fargate profile não tem nó para provisionar.

Spot no consumidor aceita interrupção porque a mensagem volta quando a visibility expira e o receiver é idempotente. Spot no único pod de lançamentos de um ambiente que se diz de produção não aceita: o SLO de 99,9% não combina com reclamação de capacidade. Por isso o pool on-demand existe, pequeno, e o Spot não é o único pool.

# ECS Fargate

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Os dois processos Java precisam rodar em `sa-east-1`, em pelo menos duas zonas, com deploy repetível, escala horizontal e sem uma frota de máquina para patchar. O pico de CPU é baixo (ρ ≈ 17% com duas tasks no consolidado).

## Drivers

- Mínimo de duas tasks em AZs distintas por serviço.
- Imagem imutável vinda do ECR, scan no push.
- Rolling com circuit breaker e rollback.
- Sidecar ADOT no mesmo task.
- Custo proporcional a task pequena, arquitetura ARM.
- Tempo de plataforma que sobre para o domínio, não para o control plane.

## Opções consideradas

### 1. EKS

- Positivo: padrão de mercado, HPA, ecossistema, portabilidade de manifesto.
- Negativo: control plane pago o mês inteiro (na casa de USD 70 só de API do cluster, mais nós ou Fargate profile). Para quatro tasks, o Kubernetes é o sistema maior da conta. A portabilidade não é requisito: o alvo declarado é AWS.

### 2. Lambda

- Positivo: sem task ociosa.
- Negativo: já recusado no [ADR 0002](0002-microsservicos-por-bounded-context.md) por conexão com RDS, cold start e relay de outbox. Não se reabre aqui.

### 3. EC2 com AMI e autoscaling group

- Positivo: preço de compute menor que Fargate no uso constante, controle do SO.
- Negativo: patch de AMI, capacidade ociosa do nó, user data, e um ASG que ainda precisa de load balancer. Com ρ de 17%, o desconto do EC2 não paga a operação. Reservar instância para dois serviços pequenos é otimização prematura; Savings Plan de Fargate, se o uso estabilizar, vem antes.

### 4. ECS Fargate, graviton (ARM)

Cluster único, um service por microsserviço. Em produção o lançamentos sobe com 0,5 vCPU e 1 GiB (mínimo 2, máximo 6) e o consolidado com 1 vCPU e 2 GiB (mínimo 2, máximo 10). Aplicação e sidecar ADOT dividem esse tamanho. Escala por target tracking de CPU (60%) e de `ALBRequestCountPerTarget` nos dois. O consolidado soma target tracking do backlog da fila (`mensagens visíveis / tasks em execução`, alvo de 20). Deployment circuit breaker com rollback. Container Insights. Dev usa tasks menores e uma task por serviço. Os números estão em `infra/terraform/envs/*/terraform.tfvars.example`.

- Positivo: sem nó para administrar, task spread nas AZs, encaixe direto com ALB target group, IAM por task role, imagem OCI padrão. ARM no Fargate de `sa-east-1` custa USD 0,0557 por vCPU-hora e USD 0,00612 por GB-hora (tabela de outubro de 2026), abaixo da hora x86 da mesma família.
- Negativo: menos controle de kernel e de daemon. Sidecar come parte da CPU contratada — por isso o consolidado, que segura o pico de 50 req/s, fica em 1 vCPU e não em 0,25. O lançamentos, com 20 escritas/s, cabe em 0,5 vCPU. Cold start de task nova é da ordem de dezenas de segundos; o mínimo de 2 tasks em prod existe para não depender de scale-from-zero no pico. Fargate não fica de graça parado. O exemplo de dev não desliga a task sozinho.

## Decisão

Opção 4. Um cluster, dois services, dois target groups. Não há service mesh. A task não recebe IP público; egress por NAT ou por VPC endpoint, como no [deployment](../03-arquitetura/deployment-aws.md).

## Consequências

### Positivas

- O N−1 do RNF (uma task morta e o pico de 50 req/s segue) é configuração de desired count e de spread, não um script.
- A mesma imagem sobe no Docker local e no ECR.

### Negativas

- Debug de produção é log, trace e task replacement, não SSH. Quem precisar de shell usa ECS Exec pontual, com IAM, não uma chave na AMI.
- Teto de 6 tasks no lançamentos e de 10 no consolidado é guarda-corpo de custo. Subir o teto sem olhar RDS e Redis só multiplica pool de conexão.

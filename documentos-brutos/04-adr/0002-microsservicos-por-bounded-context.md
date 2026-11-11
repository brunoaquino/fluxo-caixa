# Microsserviços por bounded context

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O enunciado pede dois serviços — lançamentos e consolidado diário — e exige que o primeiro continue disponível se o segundo cair. Há três desenhos capazes de expressar essa separação, com custos operacionais muito diferentes para um time pequeno e um pico de 50 req/s.

## Drivers

- Falha do consolidado não pode derrubar o POST de lançamento (processo, banco e deploy).
- O volume não justifica uma plataforma de dezenas de serviços.
- Dois modelos de dados com ciclos de mudança diferentes: trilha imutável e projeção recomputável.
- Operação que caiba em ECS, dois pipelines e um time só.

## Opções consideradas

### 1. Monolito modular

Dois módulos, um processo, um banco ou dois schemas no mesmo Postgres.

- Positivo: uma transação, um deploy, depuração simples, custo de uma instância. A fronteira hexagonal cabe dentro do monolito.
- Negativo: o processo é um só. Um crash, um pool esgotado ou um deploy ruim leva os dois módulos juntos. Isso falha o RNF crítico mesmo com o código “separado”. Isolar de verdade exigiria dois processos — e aí o monolito já deixou de ser a opção.

### 2. Funções serverless (Lambda por caso de uso)

- Positivo: escala a zero no dev, sem gerir task.
- Negativo: conexão com RDS em rajada (o pico de 50 req/s abre conexões mais rápido do que o pool de um processo quente), cold start na frente do p95 de 100 ms, e VPC attachment para falar com o banco. O relay de outbox pede um processo com relógio, não uma invocação por linha. Virtual threads e o modelo de programação Spring que escolhemos vivem melhor num processo longo. Ver [ADR 0007](0007-java-25-spring-boot-4.md) e [ADR 0009](0009-ecs-fargate.md).

### 3. Dois microsserviços, um por bounded context

`lancamentos-service` e `consolidado-service`, cada um com banco, task definition e pipeline.

- Positivo: a queda do consumidor não compartilha processo nem `max_connections` com a escrita. O deploy de um relatório não reinicia o caixa. O mapa de contextos em [domínio](../02-dominio-ddd.md) cabe inteiro nessa fronteira — não há terceiro serviço “porque microsserviço”.
- Negativo: dois deploys, dois RDS em produção, consistência eventual, rastreio distribuído obrigatório. Latência do saldo deixa de ser a latência do POST.

## Decisão

Opção 3. A fronteira para no par pedido pelo negócio. Não se extrai “estorno”, “categoria” ou “comerciante” para outro serviço. Identidade fica no Cognito/Keycloak, que já é um serviço genérico.

## Consequências

### Positivas

- O teste de resiliência consegue derrubar só o consolidado e ainda receber 201.
- O schema da projeção muda sem migrar a tabela de auditoria.

### Negativas

- Custo fixo de duas instâncias RDS e de dois serviços no ALB. Aceito e quantificado em [custos](../08-custos.md).
- Todo bug de saldo agora tem dois logs. Sem o correlation id do [ADR 0013](0013-observabilidade-otel.md), a opção 3 fica pior que o monolito para operar.

# CQRS e integração assíncrona orientada a eventos

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O comerciante escreve fatos (lançamento) e lê um agregado derivado (saldo do dia e acumulado). Dá para servir as duas coisas da mesma tabela, com o serviço de leitura chamando o de escrita, ou separar o modelo de leitura e empurrar o fato por evento.

## Drivers

- O POST não pode depender do consolidado estar de pé.
- O pico de 50 req/s é de leitura do saldo, não de varredura do extrato.
- Estorno retroativo altera o dia de competência, não “cria um ajuste hoje”.
- Atraso aceitável: p95 &lt; 5 s ([blueprint](../blueprint-tecnico.md), meta de consolidação).

## Opções consideradas

### 1. Chamada síncrona REST de lançamentos para o consolidado, no meio do POST

- Positivo: o 201 só sai quando o saldo já bate. Um modelo mental.
- Negativo: o RNF crítico fica impossível. Timeout, circuit breaker ou retry no meio do POST ou atrasam o caixa ou mentem sobre o saldo. Acoplamento de disponibilidade e de deploy.

### 2. Banco compartilhado, consolidado lendo a tabela de lançamentos

- Positivo: sem evento, sem lag, `SUM` sempre fresco.
- Negativo: o consolidado depende do banco de lançamentos. Carga de leitura de pico e um relatório pesado competem com o INSERT do caixa. Não é database-per-service. Um lock ou um disco cheio no relatório afeta a escrita.

### 3. CQRS com evento `LancamentoRegistrado`

A escrita grava o fato e publica o evento. A leitura mantém `saldos_diarios` e responde o GET sem consultar o outro serviço. A soma é comutativa; ordem não é requisito.

- Positivo: disponibilidade desacoplada, esquema de leitura feito para o GET, espaço para cache, novos consumidores sem mudar o POST.
- Negativo: janela em que o GET ainda não vê o lançamento. O cliente precisa entender consistência eventual. Operação ganha fila, DLQ e lag.

## Decisão

Opção 3. O contrato publicado é o JSON versão 1 do blueprint, no tópico `fluxo-caixa-lancamentos-events`. Não há API interna síncrona entre os dois serviços.

O GET de lançamento (extrato) continua no serviço de escrita: é leitura do próprio agregado, não do read model de saldo. CQRS aqui é entre contextos, não uma proibição de ler o que se acabou de escrever no mesmo serviço.

## Consequências

### Positivas

- O dimensionamento do consolidado (cache, pool, tasks) não entra no SLO do POST.
- Estorno não precisa de endpoint no consolidado: o mesmo evento, com tipo oposto, corrige o dia.

### Negativas

- Tela que gravar e reler o saldo no mesmo clique pode ver o valor antigo por até alguns segundos. O contrato da API deixa isso explícito; não se bloqueia o POST até a projeção.
- Teste de ponta a ponta precisa esperar a consolidação ou consultar a projeção com retry. O smoke faz isso com prazo, não com sleep cego infinito.

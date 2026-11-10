# Cache Redis/ElastiCache no consolidado

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O SLO de leitura é p95 &lt; 100 ms no GET do saldo, com 50 req/s. O Postgres aguenta a taxa mesmo sem cache (a conta de conexões ativas no miss total é ~2), mas não garante o percentil quando o `SUM` do acumulado e a validação do token disputam a mesma latência. O serviço de lançamentos não tem este SLO de leitura derivada e não ganha cache.

## Drivers

- Acelerar o hit sem tornar o Redis fonte da verdade.
- Invalidar dia, acumulado e relatório de período quando um evento de qualquer data daquele comerciante chega (estorno retroativo muda o acumulado dos dias seguintes).
- Perder o cache não pode perder saldo nem derrubar a leitura.
- Produção com failover. Local com um container Redis 7.

## Opções consideradas

### 1. Sem cache

- Positivo: um componente a menos, leitura sempre tão fresca quanto a projeção.
- Negativo: o p95 de 100 ms fica inteiro nas costas do RDS e do cálculo do acumulado. Possível neste volume, folgado demais para tratar como arquitetura — o primeiro relatório de período de 366 dias no meio do fechamento come o percentil.

### 2. Cache embutido na task (Caffeine)

- Positivo: sem rede extra, latência mínima.
- Negativo: duas tasks, duas memórias. Invalidar a geração no evento só limpa a task que consumiu a mensagem. A outra serve saldo velho até o TTL. Para um read model que acabou de mudar, isso é o bug que o comerciante vê.

### 3. Redis no local e Valkey no ElastiCache, cache-aside com geração

Chaves `consolidado:versao:{comercianteId}`, `consolidado:dia:{comercianteId}:{versao}:{data}` e `consolidado:relatorio:{comercianteId}:{versao}:{inicio}:{fim}`. Cada evento aplicado faz `INCR` na versão daquele comerciante depois do commit. TTL de 30 s nas entradas (`app.cache.ttl`). ElastiCache Valkey 8.2, nó `cache.t4g.small` com réplica e failover, TLS (`REDIS_SSL=true`) e sem senha. No Docker local o cache é Redis 7 sem TLS. A aplicação fala protocolo Redis; Valkey é a escolha de produção por compatibilidade e por preço de tabela inferior ao Redis equivalente na AWS.

- Positivo: as duas tasks veem a mesma geração. Estorno antigo não exige apagar um conjunto desconhecido de chaves. Queda do cache: o caso de uso lê o Postgres. Queda do primário: a réplica assume.
- Negativo: mais um serviço gerenciado (cerca de USD 71/mês os dois nós small em `sa-east-1`, [custos](../08-custos.md)). Invalidação por versão deixa lixo até o TTL (aceito). Se o `INCR` falhar depois do commit do saldo, a versão antiga continua válida até o TTL — janela de leitura obsoleta de no máximo 30 s. Não se finge que o cache é linearizável com o banco.

## Decisão

Opção 3, somente no `consolidado-service`. Variáveis `REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL`. Timeout curto no cliente e fallback para o banco quando o Redis não responde. Não se cacheia lançamento: o GET por id já é uma chave primária e não é o pico dos 50 req/s.

## Consequências

### Positivas

- O SLO de 100 ms tem um caminho de hit que não abre transação.
- O teste de carga consegue um cenário com cache quente e outro com Redis parado, e os dois têm critério de sucesso diferente (latência versus só disponibilidade).

### Negativas

- Mais um security group. Não há `REDIS_PASSWORD`: o Terraform liga TLS e só o SG do consolidado alcança a porta 6379.
- Resposta cacheada de dia zerado existe. Um bug na versão serviria zero por até 30 s depois do primeiro lançamento do dia. O `INCR` depois do commit do evento é o que impede isso; o teste de integração cobre “evento aplicado, GET seguinte vê o valor”, com a versão nova.

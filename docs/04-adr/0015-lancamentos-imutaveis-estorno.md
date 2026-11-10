# Lançamentos imutáveis, correção por estorno

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Caixa editável apaga a história. Um `UPDATE` do valor ou um `DELETE` do lançamento deixa o saldo sem explicação e a auditoria dependente de trigger que alguém pode desligar. O negócio precisa corrigir erro de digitação sem reescrever o fato original.

## Drivers

- Trilha reconstruível só com inserts.
- Estorno único. Estorno de estorno proibido — senão a história vira uma corrente ambígua.
- A correção atinge a data de competência original, porque é lá que o saldo do dia ficou errado.
- O consolidado não deve aprender uma segunda linguagem para “desfazer”.

## Opções consideradas

### 1. Update e delete lógicos (`cancelado = true`)

- Positivo: uma linha só, listagem simples.
- Negativo: o fato muda. O evento de integração precisa de um tipo “cancelado” ou o consumidor não fica sabendo. Dois caminhos (criar e cancelar) para um domínio que cabe em “sempre nasce um fato”.

### 2. Lançamento de ajuste em data de hoje, original intocado sem vínculo

- Positivo: append-only.
- Negativo: o dia original continua errado para sempre. O acumulado até hoje fecha, o relatório do dia do erro não. O comerciante olha o dia, não o acumulado, quando confere o caixa.

### 3. Novo lançamento de tipo oposto, mesma data, mesmo valor, vínculo `lancamentoEstornadoId`

O original permanece. Índice único parcial impede o segundo estorno. Lançamento que já tem `lancamentoEstornadoId` preenchido não pode ser alvo. O evento publicado é o mesmo `LancamentoRegistrado`, e o consolidado soma o tipo oposto no dia.

O blueprint não tem campo `motivo` no agregado e o POST de estorno não envia `descricao`, embora aceite `{ "motivo": "..." }`. Interpretação desta decisão: a descrição do novo lançamento é o motivo, limitado a 255 caracteres, ou `Estorno do lançamento {id}` se o motivo vier vazio. O motivo não entra no evento v1.

- Positivo: a tabela é a auditoria. O saldo do dia original fecha. Um consumidor só. A proibição de estorno duplo é restrição de banco, então duas requisições paralelas não furam o `if`.
- Negativo: a listagem do dia mostra o original e o estorno, e o saldo “parece” ter mais movimento. É o comportamento honesto; esconder o par na UI é problema de apresentação, fora desta API. Corrigir um estorno feito por engano exige um lançamento novo deliberado, não um meta-estorno — o operador precisa entender isso.

## Decisão

Opção 3. Não há endpoint de update nem de delete. Não há evento `LancamentoEstornado`.

## Consequências

### Positivas

- PITR e backup carregam a história completa sem um log paralelo obrigatório.
- O RF-18 (estorno corrige o dia original) cai direto do modelo, sem job noturno.

### Negativas

- Lançamento na competência errada e valor certo se corrige com estorno mais um lançamento novo no dia certo. São dois fatos. O sistema não oferece “mover de data” porque mover seria update.
- A descrição sintética do estorno é uma interpretação documentada aqui, não um campo do blueprint. Se a implementação gravar outra frase, o contrato de API ainda se sustenta; o que não pode ocorrer é persistir o motivo só em log.

# C4 — Nível 4: registrar lançamento

Nível de código do caso de uso que segura o RNF de independência. As classes abaixo são o alvo da implementação em `services/lancamentos-service`; os nomes de pacote são os do blueprint. Não há framework no miolo.

![Classes do registrar lançamento por pacote: em adapters.in.web, o LancamentoController usa o RegistrarLancamentoService e se liga de forma indireta ao ProblemDetailsHandler; em application, o serviço usa Dinheiro e Lancamento do domain e as portas LancamentoRepository, IdempotenciaRepository e Outbox, implementadas pelo LancamentoJpaAdapter em adapters.out.persistence](../assets/diagramas/d12-c4-nivel4-classes.svg)

Sequência interna, já com a transação no lugar certo:

![Sequência interna do registrar lançamento: o controller envia o comando com o comercianteId do token, o serviço cria Dinheiro e, se a chave se repete com o mesmo corpo, devolve 200 com a resposta armazenada; com corpo diferente, devolve 409; na primeira vez, registra o Lancamento, insere no LancamentoRepository e na Outbox e grava a chave no mesmo commit, devolvendo 201](../assets/diagramas/d13-seq-registrar-lancamento.svg)

Pontos que o código não pode “simplificar”:

- O controller lê `comerciante_id` do `Jwt` e não de um campo do JSON. Se o JSON trouxer o campo, ele é ignorado.
- `Dinheiro.of` rejeita escala e sinal antes de qualquer `INSERT`.
- `Outbox.inserir` participa da transação do repositório. Publicar SNS dentro desse método quebraria o desenho: o SDK é chamado só pelo relay, depois do commit.
- A resposta 201 devolve `valor` como string de duas casas, alinhada ao evento e ao [ADR 0016](../04-adr/0016-representacao-monetaria.md). O request continua aceitando número JSON, como no exemplo do blueprint.

O estorno segue o mesmo desenho, com `Lancamento.estornar(original)` no domínio devolvendo ou uma falha de invariante ou um novo `Lancamento`. A checagem “já estornado” é repetida pelo índice único: duas requisições paralelas não criam dois estornos.

# C4 — Nível 3: componentes do lancamentos-service

Arquitetura hexagonal no pacote `br.com.fluxocaixa.lancamentos`. Regra verificada por ArchUnit: `domain` não depende de Spring nem de `adapters`; `application` não depende de `adapters`.

![C4 nível 3 do lancamentos-service: o LancamentoController exige token pela SegurancaConfig (que busca o JWKS no Cognito ou Keycloak) e chama os serviços de registro, estorno e consulta; registro e estorno usam o domínio Lancamento e Dinheiro e todos persistem pelos Repositorios JPA no Postgres; o RelayOutbox lê a outbox não publicada e publica pelo SnsPublicador no tópico SNS](../assets/diagramas/d11-c4-componentes-lancamentos.svg)

| Porta | Implementação | Observação |
|---|---|---|
| Entrada HTTP | `adapters/in/web` | Única porta síncrona. Não há listener de mensagem neste serviço. |
| Entrada de aplicação | `application.port.in` implementado em `application.servico` | O controller não calcula estorno. |
| Saída de persistência | JPA em `adapters.out.persistence` | Uma transação cobre lançamento, idempotência e outbox. |
| Saída de mensagem | `RelayOutbox` e `SnsPublicador` | Fora da transação do POST. Falha aqui não desfaz o 201. O nome do tópico não está no código. |

O domínio não importa `software.amazon.awssdk` nem `org.springframework`. O teste de arquitetura quebra o build se isso acontecer.

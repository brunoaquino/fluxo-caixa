# C4 — Nível 3: componentes do lancamentos-service

Arquitetura hexagonal no pacote `br.com.fluxocaixa.lancamentos`. Regra verificada por ArchUnit: `domain` não depende de Spring nem de `adapters`; `application` não depende de `adapters`.

```mermaid
C4Component
title Componentes do lancamentos-service
Container_Boundary(svc, "lancamentos-service") {
  Component(api, "LancamentoController", "adapters.in.web", "DTOs e RFC 9457. Extrai comerciante_id do JWT")
  Component(sec, "SegurancaConfig", "config", "Valida issuer, assinatura e escopo SCOPE_")
  Component(ucReg, "RegistrarLancamentoServico", "application.servico", "Idempotencia por impressao_digital")
  Component(ucEst, "EstornarLancamentoServico", "application.servico", "Estorno unico. Idempotency-Key no POST")
  Component(ucGet, "ConsultarLancamentoServico e ListarLancamentosServico", "application.servico", "Detalhe e pagina conteudo/pagina/tamanho/totalElementos/totalPaginas")
  Component(dom, "Lancamento e Dinheiro", "domain.lancamento e domain.dinheiro", "Java puro. Sem Spring")
  Component(jpa, "Repositorios JPA", "adapters.out.persistence", "lancamentos, idempotencia, outbox_eventos")
  Component(relay, "RelayOutbox", "adapters.out.messaging", "SKIP LOCKED e proxima_tentativa_em")
  Component(sns, "SnsPublicador", "adapters.out.messaging", "Publica em LANCAMENTOS_TOPIC_ARN")
}
ContainerDb(db, "Postgres lancamentos", "PostgreSQL 17", "Banco do servico")
Container_Ext(idp, "Cognito ou Keycloak", "OIDC", "JWKS")
ContainerQueue(topic, "SNS", "fluxo-caixa-lancamentos-events", "Evento v1")
Rel(api, sec, "Exige token antes do caso de uso")
Rel(api, ucReg, "POST /api/v1/lancamentos")
Rel(api, ucEst, "POST /api/v1/lancamentos/id/estornos")
Rel(api, ucGet, "GET /api/v1/lancamentos")
Rel(ucReg, dom, "Constroi o agregado")
Rel(ucEst, dom, "Decide se o estorno e valido")
Rel(ucReg, jpa, "Persiste agregado outbox e idempotencia")
Rel(ucEst, jpa, "Persiste o novo lancamento e a outbox")
Rel(ucGet, jpa, "Le pelo comerciante do token")
Rel(jpa, db, "JDBC")
Rel(relay, jpa, "Le outbox nao publicada")
Rel(relay, sns, "Publica e marca publicado_em")
Rel(sns, topic, "Publish no ARN configurado")
Rel(sec, idp, "Busca JWKS")
```

| Porta | Implementação | Observação |
|---|---|---|
| Entrada HTTP | `adapters/in/web` | Única porta síncrona. Não há listener de mensagem neste serviço. |
| Entrada de aplicação | `application.port.in` implementado em `application.servico` | O controller não calcula estorno. |
| Saída de persistência | JPA em `adapters.out.persistence` | Uma transação cobre lançamento, idempotência e outbox. |
| Saída de mensagem | `RelayOutbox` e `SnsPublicador` | Fora da transação do POST. Falha aqui não desfaz o 201. O nome do tópico não está no código. |

O domínio não importa `software.amazon.awssdk` nem `org.springframework`. O teste de arquitetura quebra o build se isso acontecer.

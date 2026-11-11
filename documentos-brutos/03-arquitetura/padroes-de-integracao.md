# Padrões de integração

Catálogo aplicado, não um glossário de [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/). Cada padrão abaixo existe num trecho concreto do caminho comerciante → borda → serviço, ou do caminho lançamentos → consolidado. O contrato (JSON, paths, atributos) continua no [blueprint](../blueprint-tecnico.md). O porquê da fila está no [ADR 0005](../04-adr/0005-sns-sqs-mensageria.md); o porquê da outbox, no [ADR 0004](../04-adr/0004-transactional-outbox.md).

Dois canais, e só dois:

1. **Síncrono**, comerciante para cada serviço, REST com OAuth2.
2. **Assíncrono**, lançamentos para consolidado, SNS e SQS. Não há HTTP entre os dois serviços. Essa ausência é o RNF crítico, não uma omissão.

![Os dois canais de integração: no síncrono, o comerciante usa REST com OAuth2 através de CloudFront, API Gateway REST e ALB interno até o lancamentos-service, que grava no Postgres de lançamentos na mesma transação; no assíncrono, o relay faz Publish-Subscribe no SNS, que entrega na SQS consumida pelo consolidado-service, com Dead Letter Channel para a DLQ](../assets/diagramas/d23-padroes-integracao.svg)

## 1. Request-Reply síncrono com OAuth2

O comerciante chama `POST /api/v1/lancamentos` ou `GET /api/v1/consolidados/...` e espera a resposta na mesma conexão. Na AWS o caminho é CloudFront → API Gateway REST regional → VPC Link v2 → ALB → serviço ([ADR 0031](../04-adr/0031-api-gateway-rest-regional.md)). O token é Bearer JWT, emitido pelo Cognito (ou pelo Keycloak no laboratório). O authorizer do gateway recusa token inválido ou escopo errado antes da VPC. O serviço valida o mesmo JWT de novo, com o JWKS do issuer, e lê `comerciante_id` do claim. O laboratório não tem gateway: o binário continua sendo o resource server ([ADR 0011](../04-adr/0011-oauth2-oidc-cognito.md)).

O gateway é o API Gateway pattern desta borda: um contrato `/api/v1`, throttle por método (o GET do consolidado acima de 50 req/s em hml e prod) e, para consumidor de integração, usage plan com API key. Cliente final não leva API key. `api_key_required` nasce falso em toda rota e só liga na rota que o tfvars marcar. A cota do usage plan é o teto daquele consumidor, distinto do rate-based por IP do WAF.

O reply de erro do serviço é RFC 9457 (`application/problem+json`). O 401 do authorizer e o 400 do request validator são a resposta do gateway, antes do serviço. O reply de sucesso devolve o recurso e o header `X-Correlation-Id`. Cliente que não recebe resposta por timeout trata o POST como incerto e repete com a mesma `Idempotency-Key`. Sem essa chave, repetir o POST cria outro lançamento: o canal síncrono não deduplica sozinho.

Não há Request-Reply entre os serviços. Um GET do consolidado de volta ao lançamentos, no meio da consolidação, recolocaria o relatório no caminho crítico da escrita.

## 2. Publish-Subscribe Channel

O relay publica em `LANCAMENTOS_TOPIC_ARN`. O tópico é o canal. A assinatura que existe hoje entrega numa fila. Uma segunda assinatura (outro contexto, um data lake) ganha outra fila e outra role, sem o publicador saber o nome do consumidor. É o motivo de SNS na frente de SQS, e não uma fila em que o lançamentos escreve direto: o publicador conhece o tópico, não os interessados.

O corpo, com raw message delivery, é o JSON da [linguagem publicada](#8-published-language-e-anticorruption-layer). O tópico não transforma payload.

## 3. Point-to-Point e Competing Consumers

A fila `CONSOLIDADO_QUEUE_URL` é point-to-point: uma mensagem é processada por um consumidor lógico (o serviço de consolidado). Várias tasks fazem long polling ao mesmo tempo. O SQS entrega cada mensagem a uma task por vez (visibility timeout de 60 s). Isso é Competing Consumers: a escala de drenagem é o número de tasks, não um consumidor único com thread pool interno.

O bulkhead do Resilience4j ([ADR 0023](../04-adr/0023-resilience4j-e-versoes.md)) limita quantas mensagens uma task processa em paralelo, para o pool JDBC não virar o concorrente real. Sem esse teto, Competing Consumers “funcionando bem” esgota `max_connections` no pico de catch-up.

## 4. Transactional Outbox

O caso de uso grava o lançamento, a linha de idempotência e a linha de `outbox_eventos` na mesma transação. O 201 sai quando esse commit volta. O relay, noutro agendamento, lê com `SELECT … FOR UPDATE SKIP LOCKED` e publica. Dual-write (commit e `Publish` em sequência sem outbox) perde o evento quando o processo morre no meio, ou segura o 201 até o SNS responder. Os dois resultados violam o desenho.

O relay não é um terceiro serviço. É um agendador dentro do processo de lançamentos. Duas tasks não levam a mesma linha por causa do `SKIP LOCKED`.

Colunas de contexto, gravadas no POST e relidas pelo relay: `trace_parent`, `trace_state`, `correlation_id`. O detalhe de propagação está no [documento 07](../07-observabilidade.md).

## 5. Idempotent Receiver

Entrega at-least-once produz duplicata. O consumidor insere `eventId` em `eventos_processados` na mesma transação do `UPSERT` do saldo. Chave duplicada: a transação termina sem somar de novo e a mensagem é apagada. Reprocessar a DLQ é seguro por causa disso.

O POST tem o mesmo padrão no canal síncrono, com outra chave: `Idempotency-Key` mais a impressão digital do corpo. Chave igual e corpo diferente responde 409. São dois receivers idempotentes, com chaves que não se misturam. `eventId` não substitui `Idempotency-Key`, e o contrário também não.

## 6. Dead Letter Channel

Depois de `maxReceiveCount` 5, a mensagem vai para `consolidado-lancamentos-dlq`. A fila principal segue. Sem esse canal, uma mensagem inválida volta para sempre, queima receive e esconde o erro atrás de um backlog que não drena.

A DLQ não é reprocessada por código. O runbook está no [documento 07](../07-observabilidade.md): corrigir a causa, redrive, deixar o receiver idempotente absorver a repetição. Versão de evento desconhecida vai para a DLQ de propósito. Alargar o consumidor “para não falhar” engoliria uma quebra de contrato.

## 7. Message Expiration e retenção

A fila principal retém a mensagem por **14 dias**. Esse é o prazo em que o consolidado pode ficar fora sem perder evento. Não é um TTL de negócio do lançamento: o fato já está no Postgres de lançamentos. Passados 14 dias sem consumidor, a mensagem expira e o saldo daquele evento só volta por reconciliação, não pela fila. O alarme de idade existe para ninguém descobrir a expiração no décimo quinto dia.

Visibility de 60 s é o prazo de “esta tentativa ainda está com um consumidor”. Processar leva milissegundos. Sessenta segundos cobrem um GC ruim sem devolver a mensagem cedo, o que duplicaria trabalho (o receiver aguenta a duplicata, mas o alarme de lag mente).

A outbox tem outra retenção, operacional: linha com `publicado_em` preenchido é apagada depois de 7 dias. Linha não publicada não expira por job. O documento de [desempenho](../12-desempenho-e-dados.md) fixa essa limpeza.

## 8. Published Language e Anticorruption Layer

A linguagem publicada é o JSON `LancamentoRegistrado` versão 1: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `lancamentoId`, `comercianteId`, `tipo`, `valor` (string decimal), `moeda`, `dataLancamento`, `lancamentoEstornadoId`. Sem descrição e sem categoria. Isso é minimização, não economia de bytes: a descrição pode carregar dado pessoal, e o consolidado não precisa dela para somar.

O consumidor não desserializa esse JSON direto num agregado. O `TradutorEventoLancamento` é a camada anticorrupção: traduz o JSON para o comando interno `AplicarLancamento`. Campo desconhecido é ignorado. `eventVersion` diferente de 1 falha a mensagem, que cai na DLQ depois das tentativas. Assim o modelo de saldo não importa tipo do produtor, e o produtor não importa tabela do consumidor.

## 9. Event-Carried State Transfer, não Event Notification

Há duas formas de contar o fato ao consolidado.

**Event Notification.** A mensagem leva só `eventId` (e talvez `lancamentoId`). O consumidor chama o lançamentos para buscar o valor. O relatório passa a depender da API de escrita estar no ar e do contrato HTTP. O RNF crítico deixa de valer no caminho inverso: o consolidado, ao voltar de uma queda, não drena a fila se o lançamentos também estiver degradado. Além disso cria o acoplamento que a outbox existe para evitar.

**Event-Carried State Transfer do agregado inteiro.** A mensagem leva descrição, categoria, tudo. O consumidor vira uma segunda cópia do lançamento, com dado pessoal na fila, no disco do SQS e em qualquer assinante futuro. A fila deixa de ser um fato contábil mínimo e passa a ser um extrato.

**O que foi escolhido.** Transferência do **recorte que a projeção precisa para somar**, e nada mais. O consolidado aplica o saldo sem callback. Um estorno viaja como outro `LancamentoRegistrado`, com tipo oposto e `lancamentoEstornadoId` preenchido: o consumidor não tem regra especial de estorno. Se um dia o relatório precisar da descrição, a decisão se reabre com consciência de que isso copia dado pessoal — não se “acrescenta o campo porque já estava no banco”.

## 10. Correlation Identifier

Três identificadores, com papéis que não se substituem:

| Id | Quem cria | Para que serve |
|---|---|---|
| `correlationId` | A borda HTTP, a partir de `X-Correlation-Id` ou um UUID novo | Achar a jornada nos logs, inclusive depois da fila |
| `traceId` / `traceparent` | O tracer W3C no POST | Achar os spans no Tempo ou no X-Ray |
| `eventId` | O caso de uso, ao gravar a outbox | Idempotência de negócio. Não é id de trace |

O `correlationId` vai no MDC, na coluna `correlation_id` da outbox e no atributo de mensagem `correlationId`. O consumidor reabre o MDC com ele. Um suporte que recebe o id do comerciante (o header volta na resposta) filtra log dos dois serviços sem precisar do trace.

## 11. Message Envelope e atributos

Raw message delivery está ligado: o corpo no SQS é o JSON da linguagem publicada, não o envelope SNS (`Message`, `MessageId`, `TopicArn` em volta). Um envelope em volta quebraria o consumidor e a ACL, que esperam o JSON da seção 6 do blueprint.

Metadado que não é fato de negócio vai em **message attribute**, tipo `String`:

| Atributo | Conteúdo |
|---|---|
| `eventType` | `LancamentoRegistrado` |
| `traceparent` | W3C, gravado na outbox no POST |
| `tracestate` | Quando houver |
| `correlationId` | O mesmo UUID da requisição |

Atributo permite filtro de assinatura no futuro (`eventType`) sem parsear o corpo, e tira o contexto de trace de dentro do contrato versionado. Colocar `traceparent` dentro do JSON obrigaria todo consumidor, inclusive um de BI, a conhecer telemetria. Atributo deixa o JSON estável.

O relay publica com esses atributos relendo a linha. O consumidor pede `MessageAttributeNames=All` e `MessageSystemAttributeNames=All`. Sem o `All`, o SQS devolve o corpo e omite o contexto, e o trace quebra no meio mesmo com a coluna preenchida.

## 12. Retry com backoff e jitter

Dois retries, em camadas diferentes.

**Publicação.** Resilience4j no cliente SNS do relay: Retry com backoff exponencial e jitter, TimeLimiter, e CircuitBreaker. O jitter existe para duas tasks não martelarem o SNS no mesmo milissegundo quando ele volta. Quando o circuito abre, o relay adia o lote: as linhas ficam na outbox com `proxima_tentativa_em` empurrado, até 60 s. Nada se perde, e o POST não participa.

**Consumo.** Falha transitória de banco no consumidor: Retry com backoff, dentro do visibility. Falha de domínio (versão desconhecida, JSON inválido) não retenta no aplicativo: devolve a falha, o SQS conta o receive, e a DLQ decide. Retentar 4xx de negócio só adia a DLQ.

O cliente do caixa, do lado de fora, não retenta 4xx. 409 de idempotência não melhora na segunda tentativa com outro corpo. 401 não melhora sem token novo.

## 13. Circuit Breaker

Não há circuito entre lançamentos e consolidado, porque não há chamada entre eles. Os circuitos que existem:

| Onde | O que abre | O que acontece aberto |
|---|---|---|
| Relay, cliente SNS | Falha ou lentidão do publish | O lote fica na outbox. O 201 já foi embora. |
| Consolidado, cliente Redis | Falha ou lentidão do cache | A leitura cai no Postgres. O saldo continua correto, mais lento. |
| Deploy ECS | Task nova sem readiness | Rollback. É outro mecanismo, com o mesmo nome. [ADR 0020](../04-adr/0020-estrategia-de-deploy.md). |

O estado do circuito entra em `/actuator/health` sem derrubar readiness. Redis aberto não pode tirar a task do ALB: isso transformaria uma degradação de latência em indisponibilidade.

## 14. Garantia de entrega e ordenação

A garantia é **at-least-once**. O SNS pode entregar duas vezes. O relay pode publicar e morrer antes de gravar `publicado_em`, e publicar de novo. O SQS Standard pode entregar fora da ordem em que o SNS recebeu.

**Por que não FIFO.** A aplicação do saldo é uma soma. Soma é comutativa: crédito de 10 e débito de 4 no mesmo dia produzem o mesmo saldo em qualquer ordem. A duplicata é resolvida por `eventId`, não por ordenação. FIFO cobraria mais, imporia limite de throughput por message group e exigiria escolher a chave do grupo (`comercianteId` serializaria o caixa de um comerciante atrás de uma mensagem lenta). Nada disso melhora o saldo. FIFO voltaria a ser argumento se um evento futuro não fosse comutativo (saldo que depende do evento anterior, máquina de estados). Esse evento não existe. O [ADR 0005](../04-adr/0005-sns-sqs-mensageria.md) registra a mesma escolha pelo preço; aqui o motivo é a álgebra do domínio.

O que FIFO não consertaria: a janela entre o commit do lançamento e o publish. Essa janela é a consistência eventual, com meta p95 de 5 s, e é aceita.

## 15. Versionamento de contrato

API HTTP: prefixo `/api/v1`. Campo novo na resposta é aditivo; cliente ignora o que não conhece. Quebra (tirar campo, mudar tipo de `valor`) é `/api/v2` com os dois prefixos no ar até o último cliente antigo sair. O ALB roteia os dois prefixos para o mesmo target group enquanto a convivência durar.

Evento: `eventVersion`. Campo novo entra na versão 1 se for aditivo e o consumidor antigo puder ignorá-lo. Mudança que o consumidor precisaria entender (outro significado de `tipo`, valor em centavos inteiros) é versão 2, publicada em paralelo durante a convivência, com a ACL aceitando as duas e a versão desconhecida indo para a DLQ. Não se reescreve o histórico: evento já publicado permanece na versão em que nasceu.

## 16. Contract testing

O produtor e o consumidor moram em repositórios Maven separados. Um teste que só sobe os dois no Compose pega divergência tarde. O contrato a travar é o JSON da linguagem publicada mais os atributos obrigatórios (`eventType`, e a presença de `correlationId` / `traceparent` quando o publish sai do relay).

Pact (consumer-driven) cabe: o consolidado publica a expectativa do JSON v1 e dos atributos; o lançamentos verifica essa expectativa no CI, sem subir o outro processo. Até o Pact entrar no pipeline ([documento 14](../14-cicd-e-qualidade.md)), o mínimo que já segura o contrato é o teste de integração do produtor, que serializa o evento, e o teste da ACL, que recusa versão desconhecida. Os dois usam o mesmo fixture. Fixture copiado à mão nos dois projetos é o risco: uma mudança num lado só passa no teste local e quebra na fila. O fixture deve ter uma origem só, ou o Pact substitui a cópia.

## 17. Critérios de segurança para consumir

O detalhe de STRIDE e OWASP está no [documento 06](../06-seguranca.md). O mínimo para um integrador novo:

| Controle | HTTP | Mensagem |
|---|---|---|
| Identidade | Bearer JWT do issuer combinado. API key só no consumidor de integração cadastrado no usage plan; o cliente final não leva chave. | IAM. Não existe endpoint “injeta evento”. |
| Autorização | Escopo no authorizer do gateway e de novo no serviço. Leitura não recebe write. | `sns:Publish` só na role de lançamentos, no ARN do tópico. `sqs:ReceiveMessage` e `DeleteMessage` só na role de consolidado, no ARN da fila. Segundo consumidor ganha fila e role novas. |
| Política de recurso | O ALB não é a autorização. | Policy do tópico e da fila recusa principal que não seja a role nomeada. `Resource` é o ARN, não `*`. |
| Tenant | `comercianteId` no corpo é ignorado. O claim do token manda. | Só o produtor preenche `comercianteId`. O consumidor não “corrige” tenant. |
| KMS | Segredo de banco na CMK do serviço. | SSE-KMS na fila, no tópico e na DLQ. A role de consumo tem `kms:Decrypt` nessa chave, não na chave do outro serviço. |
| TLS | TLS 1.2+ no CloudFront. JDBC com `sslmode=require`. Redis com TLS na AWS. | API da AWS em HTTPS, pelo VPC endpoint em hml e prod. |
| Validação | Bean validation na borda HTTP e invariante de novo no domínio. | ACL valida versão, tipo fechado, valor decimal positivo, UUID. JSON inválido não é aplicado pela metade. |
| Segredo no payload | Token não vai para log nem query string. | Senha, descrição e token não entram no evento nem no atributo. |

Assinatura HMAC do corpo não está ligada. A policy da fila, o raw delivery dentro da conta e a rede fechada são o controle. HMAC passa a ser necessário se um segundo produtor puder publicar no mesmo tópico ou se a fila deixar de ser privada. Até lá, seria mais uma chave para girar, sem um agressor no caminho que a chave impedisse.

![Sequência de correlação: o comerciante envia POST com JWT e X-Correlation-Id ao CloudFront, que repassa ao API Gateway e, pelo VPC Link, ao lancamentos-service com Authorization e traceparent; o serviço faz COMMIT do fato e da outbox com trace_parent e correlation_id e responde 201 com o mesmo correlationId; o relay reserva a linha com SKIP LOCKED e publica no SNS com atributos traceparent e correlationId; o SNS entrega em raw delivery na SQS; o consolidado-service recebe a mensagem com atributos e abre um span CONSUMER filho do traceparent](../assets/diagramas/d24-seq-correlacao.svg)

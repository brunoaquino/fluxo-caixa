# API REST versionada e problemas RFC 9457

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O canal do comerciante (e qualquer integração) precisa de um contrato estável para escrever lançamento e ler saldo. Erro hoje, se vier como `{ "message": "erro" }` com HTTP 200 ou com 500 genérico, obriga o cliente a ler texto. A evolução não pode quebrar quem já manda o JSON v1.

## Drivers

- Prefixo de versão visível na URL.
- Erro com tipo, título, status e detalhe, `Content-Type: application/problem+json`.
- Documentação gerada do código (springdoc em `/v3/api-docs` e `/swagger-ui.html`).
- O mesmo estilo nos dois serviços.
- Evento com versão própria, independente da versão HTTP.

## Opções consideradas

### 1. Sem versão na URL, evolução “compatível sempre”

- Positivo: URL curta.
- Negativo: a primeira quebra não tem para onde mandar o cliente antigo. Campo opcional resolve adição, não remoção nem mudança de semântica de estorno.

### 2. Versionamento só por header (`Accept: application/vnd.fluxocaixa.v1+json`)

- Positivo: URL estável, versão rica.
- Negativo: invisível no access log padrão e mais trabalhoso no curl e no WAF por path. Para dois recursos, é cerimônia.

### 3. `/api/v1` e RFC 9457

Recursos como no blueprint. Erros com `type` relativo (`/problems/valor-invalido`, `/problems/estorno-invalido`, `/problems/idempotencia-conflito`, `/problems/recurso-nao-encontrado`), `title`, `status`, `detail`, `instance`. Corpo de sucesso em `application/json`. OpenAPI via springdoc.

Códigos usados de forma estável:

| Situação | HTTP |
|---|---|
| Token ausente ou inválido, claim ausente | 401 |
| Escopo insuficiente | 403 |
| Payload inválido, período maior que 366 dias | 400 |
| Recurso de outro tenant ou inexistente | 404 |
| Estorno repetido, estorno de estorno, mesma `Idempotency-Key` com outro corpo | 409 |
| Falha inesperada | 500, sem stack no corpo |

- Positivo: o cliente trata 409 de forma diferente de 400. O path no ALB casa com a versão. O Swagger é a referência viva do que o código faz.
- Negativo: `type` relativo exige que o cliente não presuma um host fixo. Quebrar o v1 exige manter v1 e v2 juntos por um tempo — custo real de código, e é exatamente o que a versão compra. Problema genérico demais (`about:blank` para tudo) desperdiça o RFC; por isso os `type` acima são fechados e poucos.

## Decisão

Opção 3. A versão do evento (`eventVersion`) não segue a versão da URL. Dá para evoluir o JSON do SNS sem abrir `/api/v2`, e o contrário também.

Paginação da lista: `page` a partir de 0, `size` limitado a 50. Resposta com `content`, `page`, `size`, `totalElements`, `totalPages`.

## Consequências

### Positivas

- O teste de contrato e o smoke assertam status e `content-type`, não uma frase em português.
- WAF e métrica RED separam os dois serviços pelo path, que já carrega o recurso.

### Negativas

- Mensagem em `detail` pode mudar sem ser quebra; `type` e `status` não podem. Isso precisa estar dito no OpenAPI para o consumidor não parsear `detail`.
- Dois Swaggers (8081 e 8082). Um portal unificado seria o BFF que [não adotamos](../03-arquitetura/arquitetura-alvo.md). Até lá, são duas UIs.

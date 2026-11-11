# API Gateway REST regional atrás do CloudFront

- **Status:** Aceito
- **Data:** 2026-11-10
- **Atualiza:** [ADR 0022](0022-cloudfront-vpc-origin.md). Não substitui o CloudFront. O [ADR 0018](0018-borda-alb-waf.md) continua substituído pelo 0022; este ADR é o passo seguinte da mesma borda.
- **Implementa:** seção 20 do [blueprint](../blueprint-tecnico.md)

## Contexto e problema

O ADR 0022 deixou o CloudFront como única porta pública e o ALB interno, sem IP público. O JWT era validado só na task. Isso cumpre o “o mesmo binário funciona no laboratório”, e também deixa três controles para depois: rejeitar token inválido antes de gastar task, cota por consumidor de integração, e um contrato de borda que não muda quando o runtime atrás do ALB mudar.

O desafio pede critério de segurança para consumo de integração. Cliente final e PDV não são o mesmo consumidor. O primeiro autentica com o usuário do Cognito. O segundo precisa de teto próprio, identificável, sem abrir uma segunda API.

## Drivers

- Token inválido não deve abrir conexão até o ECS. Authorizer na borda economiza vCPU e reduz a superfície que um token ruim enxerga.
- Throttle e quota por consumidor, não só rate-based por IP. NAT de shopping soma comerciantes no mesmo IP; o WAF não distingue o PDV do caixa.
- Validação do corpo antes da VPC. `tipo`, `valor` e data fora do contrato morrem com 400 no gateway.
- mTLS para parceiro B2B fica disponível no domínio customizado da REST API, sem virar requisito do comerciante no app.
- WAF regional no stage, porque o hostname `execute-api` continua alcançável. CloudFront não é a única URL que a AWS publica.
- Um catálogo. O stage `v1` é a versão. O export OpenAPI sai da própria API.
- Canary de stage e access log por rota, com `requestId` e o trace do X-Ray.
- Trocar ECS por EKS ou Lambda não muda o path público `/api/v1`.

O flag `enable_api_gateway = false` devolve o desenho do ADR 0022. O default dos três ambientes é `true`.

## Opções consideradas

### 1. Sem gateway: CloudFront → VPC Origin → ALB

É o ADR 0022, e continua no código quando o flag está desligado.

- Positivo: um hop a menos. Sem cobrança por request. O laboratório e a AWS validam o JWT no mesmo lugar.
- Negativo: token inválido paga task, ALB e pool JDBC. Não há usage plan. Não há validação de JSON na borda. Não há canary de contrato. O único teto fino é o WAF por IP.

### 2. HTTP API (API Gateway v2)

- Positivo: cerca de USD 1,00 por milhão de requests em `us-east-1`, contra USD 3,50 da REST. JWT authorizer nativo, VPC Link v2 para o ALB, menos recurso para declarar. É a escolha certa quando não há usage plan, não há validação de body no gateway, não há canary e o volume passa de dezenas de milhões por mês — aí a diferença de USD 2,50 por milhão deixa de ser troco.
- Negativo, para este contrato: usage plan e API key são da REST API. Request validator com JSON Schema é da REST API. Canary de stage é da REST API. Cache de stage, se um dia for ligado com chave que inclua o token, é da REST API. mTLS de domínio customizado para parceiro B2B é da REST API. WAF a HTTP API passou a aceitar em parte do portfólio, mas o conjunto que o desafio pede (cota por consumidor + validação + canary + WAF no stage) não cabe na HTTP API sem completar o que falta na aplicação.

### 3. REST API regional

Região da API igual à do ALB (`us-east-1`). Não é edge-optimized: o anycast já é o CloudFront. Endpoint configuration `REGIONAL`.

- Positivo: usage plan, API key opcional por rota, request validator, modelo JSON Schema, canary, cache desligável, WAF no stage, mTLS no domínio customizado, access log com os campos da seção 20, authorizer Cognito com `authorization_scopes`.
- Negativo: USD 3,50 por milhão. Mais recurso no Terraform. O stage entra no path da integração privada (`/v1/api/v1/...`). O hostname do ALB interno não cabe em certificado público, então o hop até o ALB continua HTTP dentro da VPC, como já era no ADR 0022.

### 4. ALB com Lambda authorizer

- Positivo: um hop a menos que o gateway. O authorizer vê o JWT antes da task.
- Negativo: Lambda no caminho crítico, cold start, e nada de usage plan, validator ou canary. Outro runtime para operar. O ADR 0022 já recusou complicar o ALB.

### 5. Kong ou Apigee self-hosted

- Positivo: portal, transformação e política ricas.
- Negativo: cluster para dois paths estáveis. O custo fixo e a operação engolem a diferença de USD 12,50/mês entre REST e HTTP API em cinco milhões de requests. Entra na conversa se houver dezenas de APIs de produto, não neste caixa.

## Decisão

Opção 3, na frente do que o ADR 0022 já construiu.

```
Cliente → CloudFront (+ WAF CLOUDFRONT)
       → API Gateway REST regional (+ WAF REGIONAL)
       → VPC Link v2 → ALB interno → ECS
```

Provider AWS 6.72 confirma o caminho. `aws_api_gateway_integration` tem `integration_target`. O `connection_id` é um `aws_apigatewayv2_vpc_link` (VPC Link v2: subnets e security group). O alvo é o ARN do ALB. Não há NLB. A documentação da AWS de novembro de 2025 descreve essa integração; o VPC Link v1 (`aws_api_gateway_vpc_link`) continua existindo e só aceita NLB. Não foi usado.

O que cada peça faz:

- **Authorizer Cognito** em todo método, com escopo `fluxo-caixa/lancamentos:write`, `fluxo-caixa/lancamentos:read` ou `fluxo-caixa/consolidado:read`. Token inválido ou escopo errado responde 401/403 no gateway. A task **continua** validando o mesmo JWT e lendo `comerciante_id`. Defesa em profundidade: o gateway não é o resource server, é a primeira peneira. O laboratório, sem gateway, não muda de código.
- **Throttle** de stage e de método. Em hml e prod o GET do consolidado fica acima de 50 req/s (80 e 100). Em dev fica em 20, de propósito.
- **Usage plan + API key** só para o mapa `api_consumers`. Vazio por default. `api_key_required` é por rota e nasce `false`: o comerciante não carrega API key.
- **Validator** de corpo e parâmetros no POST de lançamento. O POST de estorno valida só parâmetros: o corpo é opcional, como a seção 7. O model `EstornarLancamento` documenta `motivo` (até 255, opcional), e o serviço aplica esse limite quando o JSON vem. Ligar a validação de corpo nesse método rejeitaria a ausência.
- **WAF regional** com Common, Known Bad Inputs, SQLi e IP Reputation, mais a regra que bloqueia o request cujo header `X-Origin-Verify` não é o valor `current` nem o `previous`. O CloudFront grava o valor `current` num custom header da origem `api-gateway` e usa `AllViewerExceptHostHeader`: o `Host` tem de ser o do `execute-api`, senão a REST API responde 403; o header customizado da origem substitui qualquer valor que o cliente tenha mandado com o mesmo nome.
- **Rotação** do segredo no Secrets Manager, CMK própria, Lambda no protocolo `createSecret` / `setSecret` / `testSecret` / `finishSecret`. A Lambda gera o valor novo, grava `previous` com o current antigo, atualiza o custom header do CloudFront (distribuição achada pela tag `Name`) e os dois byte matches da regra. O valor anterior continua válido até a rotação seguinte, que é a janela em que a distribuição ainda propaga. `rotate_immediately = false` para o apply não girar o header. Dev 90 dias, hml e prod 30, no mesmo ritmo da senha do RDS.
- **Stage `v1`**: access log JSON, X-Ray, métrica detalhada, cache desligado, canary parametrizável. O canary aponta para o mesmo deployment enquanto não houver um segundo deployment; `percent_traffic` 0 deixa o bloco pronto sem desviar tráfego.
- **Path do stage.** A integração privada inclui o nome do stage no path que o ALB vê (`/v1/api/v1/...`). O listener reescreve `^/v1(/.*)$` para `$1` só quando `enable_api_gateway` está ligado. Sem o prefixo, a regra não casa e o path do ADR 0022 segue inteiro. O timeout de integração é 10 s. A URI leva o path e os path params; os headers `Authorization`, `traceparent`, `tracestate`, `X-Correlation-Id` e `Idempotency-Key` são mapeados. `X-Origin-Verify` é limpo antes do ALB para não cair no log da aplicação.

mTLS de parceiro não é criado nesta entrega. Não há domínio de parceiro nem truststore. A REST API é o lugar onde isso entra, com domínio customizado e S3 truststore, sem redesenhar o ECS.

## Consequências

- Hop a mais. A ordem de grandeza publicada pela AWS para o gateway regional fica em 10–30 ms. O SLO de latência deste sistema continua medido no serviço (p95 do POST &lt; 200 ms, p99 &lt; 500 ms; GET de consolidado p95 &lt; 100 ms com cache). O comerciante percebe o gateway em cima do RTT já discutido no ADR 0021. Não se aperta o SLO do processo para “absorver” o gateway: os dois números são séries diferentes. O alarme de `Latency` p99 do gateway está em 1 s; o de `IntegrationLatency` p99, em 800 ms.
- Custo variável. Com a premissa já usada no CloudFront (0,2 / 1 / 5 milhões de requests por mês), a REST API custa USD 0,70 / 3,50 / 17,50. A HTTP API, na mesma premissa, custaria USD 0,20 / 1,00 / 5,00. A diferença em produção é USD 12,50/mês. O WAF regional acrescenta cerca de USD 10 de piso mais USD 0,60 por milhão. A conta fechada está no [documento de custos](../08-custos.md).
- O bootstrap do header é `secret_string_wo` com senha efêmera. O resource da versão não guarda o valor. CloudFront e o WAF leem `AWSCURRENT` via data source, então o apply depois da rotação acompanha o par vigente em vez de reverter ao sorteio inicial. O header continua no state da distribuição, o par no state do data source e nas strings da regra. `secret_string_wo_version` permanece 1; incrementá-lo regrava o segredo e deve ser uma decisão explícita, não um efeito colateral do plan.
- `ListDistributions` da Lambda é `Resource: "*"`. A API da AWS não aceita ARN nesse action. O update da distribuição está limitado a `distribution/*` da conta, e o código recusa qualquer distribuição cuja tag `Name` não seja a do ambiente.
- Cliente browser em outra origem continua sem CORS, como já era no ALB. Não foi pedido.
- OpenAPI do gateway e OpenAPI do springdoc podem divergir se alguém alterar o DTO e esquecer o model. O model é a peneira da borda; o Bean Validation continua sendo a regra do domínio.

## Custos que esta decisão assume

Preço de lista em `us-east-1`, o mesmo já usado no documento de custos para WAF (USD 5 por Web ACL, USD 1 por regra, USD 0,60 por milhão) e o preço que a seção 20 fixa para a API (REST USD 3,50/milhão, HTTP USD 1,00/milhão). VPC Link v2 não entra com linha horária: o custo que se evita é o NLB que a integração antiga exigia. Lambda de rotação: uma invocação por período, abaixo de um centavo. Segredo extra: USD 0,40/mês. CMK extra: USD 1/mês.

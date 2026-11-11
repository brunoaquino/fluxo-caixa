# Borda CloudFront, WAF e VPC Origin

- **Status:** Aceito, ver [ADR 0031](0031-api-gateway-rest-regional.md)
- **Data:** 2026-11-10
- **Atualiza:** [ADR 0018](0018-borda-alb-waf.md), que fica substituído

O texto abaixo é a borda CloudFront + WAF + VPC Origin + ALB interno. O ADR 0031 coloca, por padrão, uma REST API regional entre o CloudFront e o ALB. Com `enable_api_gateway = false`, vale este desenho sem alteração.

## Contexto e problema

O ADR 0018 escolheu ALB público com WAF regional. A revisão 2 quer uma única porta de entrada, TLS perto do comerciante no Brasil, o ALB sem IP público, e um lugar onde um front estático futuro caiba sem abrir outra borda. A autorização continua no serviço.

## Drivers

- Dois target groups e readiness em `/actuator/health/readiness`. Falha do consolidado não tira o lançamento do balanceador.
- TLS 1.2+ com ACM. O certificado de domínio alternativo do CloudFront mora em `us-east-1`.
- WAF antes da task, sem cortar o pico legítimo de 50 req/s.
- O binário local valida JWT sozinho. A borda não pode ser o único authorizer.
- ALB sem rota a partir da internet.
- Custo fixo previsível. Neste volume a borda inteira fica perto de USD 47/mês em `us-east-1` ([custos](../08-custos.md)).

## Opções consideradas

### 1. ALB público e WAF regional (ADR 0018)

- Positivo: um hop a menos, encaixe direto com o target group, WAF na mesma região do ALB.
- Negativo: o ALB tem endereço alcançável. Quem descobre o DNS do balanceador fala com ele sem passar no WAF, a menos que o security group seja tão estreito que na prática já é uma allowlist — e a allowlist de “a internet inteira” não é allowlist. TLS termina na Virgínia (ou em São Paulo, no desenho antigo), então o handshake paga o RTT inteiro. Não há onde pendurar um front estático depois.

### 2. API Gateway HTTP API, VPC Link até as tasks

- Positivo: throttle por rota, authorizer JWT, plano de uso no dia em que existir parceiro com API key.
- Negativo: cobrança por request, hop a mais, e um authorizer na borda que, se for o único controle, contradiz o ADR 0011. Não há portal de desenvolvedor nem transformação de payload. Para dois paths estáveis, o Gateway compra recurso que esta API não usa. Continua sendo a opção se um dia houver API key de parceiro e cota por consumidor. Não é o dia 1.

### 3. CloudFront com WAF de escopo CLOUDFRONT, VPC Origin e ALB interno

Distribuição com `PriceClass_All`, HTTP/2 e HTTP/3, política TLS `TLSv1.2_2021`, certificado ACM em `us-east-1`. WAF: AWSManagedRulesCommonRuleSet, KnownBadInputs, SQLiRuleSet, AmazonIpReputationList, rate-based por IP; AnonymousIpList em modo count no dev. Origem: VPC Origin para o ALB interno, nas subnets privadas. O security group do ALB aceita só o prefix list `com.amazonaws.global.cloudfront.origin-facing`. Sem IP público no ALB.

Comportamento de `/api/*`: `CachingDisabled` e origin request policy `AllViewerExceptHostHeader`.

- Positivo: a internet vê o CloudFront. O ALB não tem rota pública. Shield Standard vem com o CloudFront, sem linha de preço. TLS termina em GRU. A mesma distribuição recebe, depois, um behavior para S3 com Origin Access Control, sem segundo domínio de borda. O WAF de escopo CloudFront é o que a região `us-east-1` já exige, alinhado ao [ADR 0021](0021-regiao-us-east-1.md).
- Negativo: mais um componente. O request de API não cacheia, então o CloudFront não reduz carga no ALB; reduz handshake e superfície. O corpo do POST é inspecionado na borda (ver LGPD no ADR 0021). Rate-based continua instrumento grosso, por IP, janela de 5 minutos. Cliente atrás de NAT compartilhado soma vários comerciantes no mesmo IP. O limite começa folgado (a ordem de 30.000 requests por IP a cada 5 minutos, herdada do ADR 0018) e se calibra com access log, não com o número 50 do pico do serviço.

## Decisão

Opção 3. O ADR 0018 fica substituído.

Cache desligado em `/api/*` porque a resposta é do comerciante autenticado. Cachear isso na CDN erra de dois jeitos: serve saldo de um tenant a outro se a chave de cache não incluir o token (e incluir o token na chave é um vazamento do token no edge), ou não ganha hit nenhum porque cada token é uma chave. O Redis, dentro da VPC, já é o cache do saldo. A CDN não é o segundo cache dessa API.

Headers de resposta, via Response Headers Policy: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`. Não se cacheia, então HSTS é o header que importa de verdade: empurra o cliente para HTTPS nas visitas seguintes.

`/actuator` que não seja liveness/readiness não é regra do ALB. Prometheus fica fora do listener. HTTP na distribuição redireciona para HTTPS. Não há API em claro.

Shield Standard fica ligado pelo fato de a entrada ser CloudFront. Shield Advanced não entra: o piso mensal dele é outra ordem de grandeza, sem um evento de negócio que o peça.

## Consequências

### Positivas

- Varrer o intervalo público do ALB deixa de ser um caminho. O prefix list é a allowlist.
- Falha de readiness do consolidado continua local ao target group dele. O RNF crítico sobrevive à borda nova.
- O serviço local não muda: valida o JWT com o mesmo código.

### Negativas

- Dois lugares podem dizer 403: o WAF e o serviço. 403 de WAF não é 403 de escopo. O access log da distribuição e o log do serviço precisam ser distinguíveis no runbook, senão o erro de budget vira caça ao fantasma.
- Regra rate-based apertada demais é a forma mais fácil de violar o teto de 5% de perda com tráfego legítimo.
- Log de WAF com o corpo do POST guarda descrição. O log redige corpo e o header `Authorization`. Sem essa redação, a minimização do ADR 0021 fura na borda.

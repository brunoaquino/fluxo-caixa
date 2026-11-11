# Borda com ALB e AWS WAF

- **Status:** Substituído (Superseded) pelo [ADR 0022](0022-cloudfront-vpc-origin.md)
- **Data:** 2026-11-10

O texto abaixo é o registro histórico do ALB público com WAF regional. A borda seguinte é o [ADR 0022](0022-cloudfront-vpc-origin.md); o [ADR 0031](0031-api-gateway-rest-regional.md) acrescenta a REST API regional na frente do ALB interno.

## Contexto e problema

Os dois serviços precisam de um ponto HTTPS único, com TLS, roteamento por path, health check independente e uma camada que barre abuso antes da task. A autorização de negócio continua no serviço ([ADR 0011](0011-oauth2-oidc-cognito.md)).

## Drivers

- Dois target groups, readiness em `/actuator/health/readiness`.
- TLS com ACM.
- Proteção contra volume abusivo e payload obviamente malicioso, sem cortar o pico legítimo de 50 req/s.
- Mesma aplicação no local, sem a borda AWS — logo a borda não pode ser o único lugar que valida JWT.
- Custo fixo previsível.

## Opções consideradas

### 1. Amazon API Gateway HTTP API na frente, VPC Link até as tasks

- Positivo: throttling por rota, JWT authorizer, plano de uso, se um dia houver API key de parceiro.
- Negativo: hop a mais e cobrança por requisição. O VPC Link não é de graça. O authorizer na borda, se for o único controle, contradiz o ADR 0011. Não temos portal de desenvolvedor nem transformação de payload. Para dois paths estáveis, o Gateway compra recurso que não usamos e cobra por request que o ALB já cobre com LCU.

### 2. CloudFront + WAF na frente do ALB

- Positivo: borda anycast, cache de GET, WAF no edge.
- Negativo: o GET de saldo é por tenant e não é anônimo; cache na CDN é fácil de errar (vazamento entre comerciantes) e o ganho é pequeno com o Redis já na região. Mais um componente. Pode entrar depois, na frente, sem mudar o serviço, se a latência de clientes distantes de São Paulo doer.

### 3. ALB público com ACM e AWS WAF

Regras de path:

- `/api/v1/lancamentos*` → lançamentos
- `/api/v1/consolidados*` → consolidado

WAF: managed rules Common, Known Bad Inputs e Amazon IP Reputation, mais uma regra rate-based calibrada acima do pico legítimo de um único cliente. O pico de 50 req/s é do serviço; a regra por IP não pode usar 50 como teto de bloqueio sem medir o fechamento real.

- Positivo: encaixe nativo com ECS, health check por target group (o consolidado doente não tira o lançamento do balanceador), TLS, WAF no mesmo recurso, custo dominado pela hora do ALB (USD 0,034/h em `sa-east-1`) e não pelo request. O serviço local ignora a borda e continua correto porque valida o JWT sozinho.
- Negativo: o ALB não versiona API, não transforma body e não emite API key. Rate-based é instrumento grosso (por IP, janela de 5 minutos). Cliente atrás de NAT compartilhado pode somar vários comerciantes no mesmo IP. Por isso o limite começa folgado e o abuso fino, se aparecer, volta a ser argumento para a opção 1.

## Decisão

Opção 3. Actuator que não seja liveness/readiness não é publicado no listener público. HTTP 80 redireciona para 443 ou fica fechado; não há API em claro.

## Consequências

### Positivas

- Falha de readiness do consolidado não remove as tasks de lançamentos. Isso é o RNF crítico na borda, não só na fila.
- WAF managed rule pega uma classe de abuso sem código na aplicação.

### Negativas

- Dois lugares podem dizer 401: nenhum hoje na borda, só o serviço. Se no futuro um authorizer for ligado no ALB, os dois precisam concordar no issuer e nos escopos, senão o diagnóstico vira “funciona no local e não na AWS”.
- Regra rate-based mal calibrada é a forma mais fácil de violar o teto de 5% de perda com tráfego legítimo. Mudança nessa regra exige olhar o k6 e o access log, não só o console.

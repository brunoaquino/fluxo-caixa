# OAuth2/OIDC com Cognito e JWT validado no serviço

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Cada requisição pertence a um comerciante e a um escopo. O identificador do comerciante não pode vir do corpo nem da URL. O ambiente local precisa emitir token sem conta AWS. O ambiente AWS precisa de um provedor gerenciado.

## Drivers

- Access token JWT, validado por assinatura e issuer.
- Escopos `lancamentos:write`, `lancamentos:read`, `consolidado:read`.
- Claim `comerciante_id`. Na AWS o prefixo de escopo é `fluxo-caixa/`; no local é vazio. A aplicação lê `app.security.scope-prefix` e `app.security.comerciante-claim`.
- Mesmo código de resource server nos dois ambientes.
- Sem sessão de servidor. A API é stateless.

## Opções consideradas

### 1. API key estática por comerciante

- Positivo: trivial de testar.
- Negativo: sem expiração decente, sem escopo, sem rotação fácil, e o tenant volta a ser um header que o cliente escolhe. Não distingue leitura de escrita.

### 2. Authorizer só na borda (API Gateway ou ALB authenticate-oidc), serviço confia na rede

- Positivo: o serviço não fala com JWKS.
- Negativo: qualquer caminho que alcance a task (SG folgado, outro serviço no cluster) é chamada anônima com poder de escrita. O comportamento local (sem ALB) divergiria do comportamento AWS. A borda escolhida no [ADR 0018](0018-borda-alb-waf.md) não substitui autorização de aplicação.

### 3. Cognito na AWS, Keycloak no Docker, resource server em cada serviço

User Pool Essentials com resource server `fluxo-caixa` e os três escopos. Pre Token Generation Lambda v2 coloca `comerciante_id` no access token e, via `TOKEN_SCOPES`, os escopos `fluxo-caixa/lancamentos:write`, `fluxo-caixa/lancamentos:read` e `fluxo-caixa/consolidado:read`. O pool não tem outro papel além de comerciante, então todo token recebe os três. O domínio hospedado (`fluxo-caixa-<env>-<account_id>`) deixa o authorization code disponível. No local, realm `fluxo-caixa`, client público `fluxo-caixa-app` com direct access grants, usuário `comerciante1` / `comerciante1` e o mesmo claim.

O serviço valida o JWT (Spring Security Resource Server). Dentro do Compose, `JWT_ISSUER_URI` permanece `http://localhost:8080/realms/fluxo-caixa` para coincidir com o `iss` do token obtido no host, e `JWT_JWK_SET_URI` aponta para `http://keycloak:8080/realms/fluxo-caixa/protocol/openid-connect/certs`, porque de dentro da rede o `localhost` do issuer não é o Keycloak.

- Positivo: padrão OIDC, escopo de verdade, tenant fora do alcance do cliente, paridade de código entre local e AWS, Keycloak sem custo de licença para o laboratório.
- Negativo: a Lambda de pre-token é um pedaço de infra a testar — se ela falhar, o token sai sem claim e o serviço responde 401, o que é o comportamento seguro e também um incidente de login. Cognito tem modelo de preço por MAU (dentro da franquia neste porte; ver custos). O arranjo de issuer versus JWK no Compose é fácil de “corrigir” errado: unificar os dois URLs quebra ou a validação do `iss` ou a busca da chave.

## Decisão

Opção 3. Sem claim, 401. Escopo ausente, 403. `comerciante_id` de outro formato que não UUID, 401. Não existe comerciante default.

Autorização de objeto é o filtro por esse claim em toda query. Não se cria papel de administrador nesta versão.

## Consequências

### Positivas

- O teste local (`make token`) exercita o mesmo filtro que a produção.
- Vazar um token não entrega outro comerciante. O estrago fica no tenant e na validade do token.

### Negativas

- Direct access grant no client local é senha no POST do token. O app client da AWS também aceita `USER_PASSWORD_AUTH` para o laboratório: a Lambda completa os escopos que o `InitiateAuth` não emite. O authorization code com PKCE continua no domínio hospedado para um canal interativo.
- Relógio das tasks precisa estar são. Skew grande demais rejeita token bom. NTP da plataforma Fargate cuida disso; o sintoma, se aparecer, é 401 com token recém-emitido.

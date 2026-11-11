# Estratégia de deploy

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Há dois serviços com SLOs diferentes. Um deploy do consolidado não pode reiniciar o lançamento, e um deploy ruim do lançamento não pode deixar as duas tasks novas no ar recebendo tráfego enquanto as velhas já morreram. O volume não justifica, no primeiro dia, a maquinaria de blue/green com teste sintético no target group espelho.

## Drivers

- Rolling com saúde verificada antes de seguir para a próxima task.
- Rollback automático se a task nova não fica pronta.
- Deploy independente por serviço.
- Migração de banco compatível com duas versões da aplicação ao mesmo tempo (a task antiga e a nova convivem durante o rolling).
- Caminho de evolução para blue/green quando o custo de um rollback lento for maior que o custo da ferramenta.

## Opções consideradas

### 1. Recreate (sobe o novo, derruba o velho)

- Positivo: simples, sem duas versões convivendo.
- Negativo: furo de disponibilidade em todo deploy. Incompatível com 99,9% se o deploy for rotina. Também impede expandir o mínimo de duas tasks com segurança.

### 2. Blue/green com CodeDeploy desde a primeira versão

- Positivo: troca de target group, teste na versão verde, retorno instantâneo para a azul.
- Negativo: duas frotas durante a janela, mais código de pipeline, e o ganho só aparece quando o rollback de rolling (que já existe) for lento demais ou quando quisermos desviar 5% do tráfego. Para o pico atual, é a evolução, não o mínimo.

### 3. Rolling do ECS com deployment circuit breaker e rollback

`minimumHealthyPercent` 100 e `maximumPercent` 200: a task nova sobe e passa no readiness antes de a antiga sair. Circuit breaker do ECS reverte a deployment se as tasks novas falham. Cada service tem o seu. Flyway só aplica mudança compatível com a versão anterior (coluna nova nullable, sem renomear no mesmo passo). O passo destrutivo espera todas as tasks estarem na versão nova.

- Positivo: nativo do ECS, sem CodeDeploy para operar no dia 1, rollback sozinho, zero downtime no caso feliz, e o consolidado pode ser implantado sem tocar no service de lançamentos.
- Negativo: o rollback de rolling não é instantâneo: ele inicia outra deployment. Bug lógico que passa no readiness (a task sobe e responde 200 no health, mas calcula saldo errado) não é pego pelo circuit breaker. Isso se pega com teste antes do deploy e com alarme depois, não com o mecanismo de rollout.

## Decisão

Opção 3 agora. A opção 2 fica como evolução explícita: quando houver suite sintética pós-deploy (smoke contra o target group verde) e a necessidade de desvio gradual, troca-se o controller de deployment para CodeDeploy blue/green sem mudar a imagem nem o contrato. O gancho é o alarme de 5xx nos cinco minutos seguintes ao deploy, que já existe no [documento 07](../07-observabilidade.md) — hoje ele chama gente; no blue/green ele pode travar a troca.

Migração incompatível não anda no mesmo release que o código que a exige. Dois releases: expande, depois contrai.

## Consequências

### Positivas

- Deploy vira rotina, não janela. O error budget não é gasto em release.
- O circuit breaker cobre imagem que não sobe, porta errada e readiness que não fica verde.

### Negativas

- Readiness que sempre retorna 200 anula a decisão. O readiness do lançamentos verifica o banco; o do consolidado verifica o banco e, de forma degradada e explícita, não transforma Redis fora em “task morta” — senão uma falha de cache derrubaria todas as tasks em sequência. Redis fora é degradação, task segue pronta, métrica de erro do cache dispara.
- Duas versões no ar durante minutos exigem disciplina de Flyway. Um `DROP COLUMN` no mesmo release do código que para de usá-la quebra a task antiga no meio do rolling.

GitOps com ArgoCD, e blue/green com análise, ficam para o dia do EKS ([ADR 0028](0028-gitops-argocd-evolucao.md)). Não mudam o rolling deste ADR enquanto o scheduler for ECS.

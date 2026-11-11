# Rotação de segredos

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Senha de banco em variável de ambiente, gravada na task definition, não gira. Quando gira à mão, o pool JDBC segue com a senha velha até alguém reiniciar a task, e o intervalo entre as duas ações é uma janela de 500. O blueprint exige rotação automática no RDS, releitura pelo Advanced JDBC Wrapper, e o mesmo tratamento para KMS, ACM, JWKS e a credencial de CI.

## Drivers

- Segredo nenhum no Git e nenhum na imagem.
- Rotação que a aplicação sobrevive sem deploy.
- CI sem access key de longa duração.
- Alarme quando a rotação falha, não quando o cliente já está tomando 500.
- Lista fechada do que é segredo, com dono e frequência. O que não está na lista não é “segredo informal”.

## Opções consideradas

### 1. Senha no Secrets Manager, lida só na subida da task

- Positivo: sai do Git. A task definition aponta o ARN.
- Negativo: rotação deixa a task com a senha velha na memória do pool. O conserto é redeploy, e quem gira o segredo precisa lembrar do redeploy. Esqueceu, o RDS aceita a senha nova e a task usa a antiga.

### 2. RDS IAM authentication

- Positivo: token curto, sem senha de longa duração.
- Negativo: o token dura 15 minutos e o pool precisa de um plugin que o renove. É outro mecanismo, válido, e não é o que o blueprint padronizou. Fica como alternativa se o wrapper não se sustentar. Não é a decisão.

### 3. Senha gerenciada pelo RDS, rotação no Secrets Manager, wrapper que rebusca

`manage_master_user_password` com rotação automática. O contrato da task é `DB_URL=jdbc:postgresql://...`. Com `DB_SECRET_ARN` o serviço normaliza para um único `jdbc:aws-wrapper:postgresql://...` e liga `failover,awsSecretsManager`. No endpoint do RDS Proxy (hostname com `.proxy-`) o `failover` sai e fica só `awsSecretsManager`: o plugin iria na instância e furaria o proxy. Ausente o segredo (local e teste), o driver PostgreSQL comum usa `DB_USERNAME` e `DB_PASSWORD`, mesmo que a URL tenha vindo com o prefixo do wrapper.

Hikari `maxLifetime` de 15 minutos, menor que o intervalo de rotação. A role da task tem `secretsmanager:GetSecretValue` só no ARN do próprio segredo e `kms:Decrypt` na CMK dele.

- Positivo: a rotação não pede deploy. Falha de autenticação faz o wrapper ler o segredo de novo. O failover de Multi-AZ usa o mesmo wrapper, em vez de um pool cego ao DNS novo.
- Negativo: mais uma dependência no classpath. Local não exerce esse caminho; o teste que importa é de integração com o wrapper apontando para um segredo de mentira, mais o ensaio de rotação em dev. Sem esse ensaio, o plugin é configuração não verificada.

## Decisão

Opção 3. Frequência: **30 dias** em hml e prod, **90 dias** em dev. Dev gira menos porque o ambiente é descartável e a rotação semanal só acrescenta ruído; 90 dias ainda impede senha eterna.

O restante gira pelo mecanismo do serviço, não por um cron nosso:

| Segredo ou credencial | Dono | Mecanismo | Frequência |
|---|---|---|---|
| Senha do RDS de lançamentos | RDS + Secrets Manager | Rotação gerenciada, wrapper rebusca | 30 dias (prod, hml); 90 (dev) |
| Senha do RDS de consolidado | idem | idem | idem |
| CMK (RDS, fila, log, segredo) | KMS | Rotação automática da CMK | anual |
| Certificado ACM do CloudFront | ACM | Renovação gerenciada, certificado em `us-east-1` | gerenciada; alarme se faltar menos de 14 dias e a renovação não tiver ocorrido |
| Chave de assinatura do JWT (Cognito) | Cognito | JWKS buscado pelo serviço, cache curto | quando o Cognito girar; o serviço não guarda PEM |
| Credencial de CI | GitHub OIDC → role IAM | Sem access key. Trust policy com o repositório e o environment | a role não tem segredo para girar |
| Token do Redis | não existe nesta versão | TLS sem AUTH | — |
| Senha do Grafana local | imagem `otel-lgtm` | `admin`/`admin`, só no Compose | nunca em produção, porque esse Grafana não vai para a AWS |
| Usuário do Keycloak local | realm de laboratório | senha de laboratório no Compose | não tem correspondente na AWS |

AUTH token do ElastiCache fica desligado. Ligar cria o terceiro segredo (USD 0,40/mês) e um segundo wrapper de credencial no caminho do GET. Vale quando o security group deixar de ser a fronteira suficiente — por exemplo, subnet de dados compartilhada com outra aplicação. Até lá, TLS mais security group. A decisão de ligar é consciente, registrada aqui para não parecer esquecimento.

CI: GitHub Actions assume a role com `id-token`. Não há `AWS_ACCESS_KEY_ID` em secret do repositório. Environment `prod` exige aprovação. A role de CI de aplicação não tem `rds:DeleteDBInstance`. A role de Terraform é outra, também via OIDC, presa ao workflow de infra.

Alarme: EventBridge em `RotationFailed` do Secrets Manager, no tópico operacional. O alarme dispara na falha da rotação, não no 500 do dia seguinte.

### Runbook de rotação manual ou emergencial

Quando a rotação automática falhar, ou quando uma senha tiver vazado:

1. Confirmar o alarme e o segredo (qual ARN, qual serviço). Não girar os dois “por garantia” sem olhar o log: o segundo giro no meio do primeiro confunde o wrapper.
2. `aws secretsmanager rotate-secret` no ARN, ou a rotação imediata no console, se a lambda de rotação estiver saudável.
3. Se a lambda estiver quebrada: colocar a senha nova com `PutSecretValue`, atualizar o usuário no RDS para a mesma senha, e só então observar as tasks. O wrapper rebusca quando a autenticação falha. Se o pool não soltar a conexão velha, `maxLifetime` de 15 minutos limita a espera; forçar novo deployment do serviço ECS corta essa espera.
4. Verificar conexão nova no Performance Insights ou em `pg_stat_activity` (usuário da aplicação, timestamp recente) e o 5xx do serviço.
5. Se a senha vazou para log ou ticket, tratar como incidente: girar, invalidar o que foi exposto, e corrigir o log. Girar sem corrigir o log repete o vazamento no próximo stack trace.

KMS com rotação anual não exige esse runbook: a chave antiga continua decriptando o ciphertext já gravado. Não se desabilita a CMK “para forçar”. Desabilitar a CMK do RDS é indisponibilidade, não rotação.

## Consequências

### Positivas

- Vazamento da senha de hoje tem prazo. O prazo é 30 dias no máximo, e minutos se o runbook rodar.
- O mesmo binário sobe local sem o wrapper, porque `DB_SECRET_ARN` vazio cai no driver comum.

### Negativas

- O caminho de produção do JDBC não é o caminho do Testcontainers. O ensaio de rotação em dev é obrigatório, uma vez por trimestre, junto do game day.
- Dois mecanismos (wrapper e, se um dia ligarmos, Proxy) que ambos falam de conexão. Proxy não substitui o wrapper para segredo, e o wrapper não multiplexa conexão. Ligar os dois sem essa frase vira configuração duplicada.

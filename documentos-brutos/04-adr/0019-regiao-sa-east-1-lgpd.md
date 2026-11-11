# Região sa-east-1 e LGPD

- **Status:** Substituído (Superseded) pelo [ADR 0021](0021-regiao-us-east-1.md)
- **Data:** 2026-11-10

O texto abaixo é o registro histórico da decisão de residência no Brasil. A decisão vigente é `us-east-1`, com salvaguarda de transferência internacional. Não usar este ADR como fonte da região atual. Os USD 641 e USD 1.214 citados no corpo são da topologia anterior (RDS `db.t4g.small`, ALB público, sem ambiente de hml). A conta vigente está na revisão atual do [documento 08](../08-custos.md).

## Contexto e problema

Lançamento de caixa identifica um comerciante e descreve a atividade econômica dele. Pode conter dado pessoal na descrição livre (nome de cliente escrito pelo operador, por exemplo). A região de computação e de armazenamento define se esse dado fica no Brasil e quanto a arquitetura custa.

## Drivers

- Residência do dado no Brasil como padrão, não como projeto futuro.
- Minimização: o evento de integração não leva descrição nem categoria.
- Custo comparado com `us-east-1`, explícito, para a escolha não ser só discurso.
- Um comerciante, um claim, sem base legal “inventada” no código: a base da operação do caixa é a execução do serviço pedido pelo próprio comerciante, titular da conta.

## Opções consideradas

### 1. `us-east-1`, mais barato

A mesma topologia custa cerca de **USD 641/mês** contra cerca de **USD 1.214/mês** em São Paulo (premissas e tabela no [documento 08](../08-custos.md)), relação de 1,89 vez.

- Positivo: quase metade do custo fixo de NAT, endpoint, RDS e Fargate.
- Negativo: dado de caixa fora do Brasil. Transferência internacional, base legal adicional e um contrato de transferência que este desenho não precisa assumir. Latência maior para um operador no Brasil, secundária perto da questão legal.

### 2. Multi-região active-passive desde o início

- Positivo: queda de `sa-east-1` deixa de ser o fim do RTO.
- Negativo: replica dado pessoal para outro lugar, dobra custo e abre um desenho de conflito que o volume não pede. Está no roadmap como evolução, com o secundário também no Brasil se a região existir, ou com decisão jurídica explícita se for fora.

### 3. Tudo em `sa-east-1`

VPC, RDS, ElastiCache, SNS, SQS, Cognito, logs, traces, backups e ECR na região de São Paulo. Sem réplica cross-region. Descrição de lançamento não atravessa o evento. Log de aplicação não imprime descrição nem token.

- Positivo: o dado fica onde o titular está. O desenho é simples. A escolha de custo é consciente: pagamos o prêmio da região para não tratar LGPD como fase 2.
- Negativo: a região é mais cara e tem menos alguns lançamentos de serviço no dia do anúncio global (nada do que usamos está ausente: Fargate, RDS PostgreSQL, ElastiCache, Cognito, WAF). Uma indisponibilidade regional longa não tem failover. O RTO de 1 h do blueprint é intra-região.

## Decisão

Opção 3. Backup do RDS na mesma região. Não se copia snapshot para `us-east-1` “por segurança” sem uma análise de transferência — cópia esquecida anula a decisão.

Direitos do titular (acesso, eliminação) não são um endpoint nesta versão: eliminação de lançamento contradiz a trilha imutável. O caminho é processo operacional sobre o comerciante (encerrar a conta, reter o que a obrigação comercial exigir, apagar o que for só cadastro no Cognito). Detalhe no [documento de segurança](../06-seguranca.md). Não se promete `DELETE` de lançamento para “atender a LGPD” destruindo a contabilidade.

## Consequências

### Positivas

- A estimativa de custo já nasce na região certa. Ninguém descobre o prêmio de São Paulo na fatura.
- O evento mínimo reduz réplica de dado pessoal no consolidado, no cache e na fila.

### Negativas

- Descrição continua no banco de lançamentos e pode conter pessoal. Isso é dado a proteger (criptografia, acesso, minimização de log), não dado a replicar.
- Se a organização já padronizou `us-east-1` para tudo, esta decisão é uma exceção a registrar na governança, não um serviço escondido em outra conta sem dono.

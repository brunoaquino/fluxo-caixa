# Réplica de leitura e estratégia de dados

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0006](0006-postgresql-database-per-service.md) e [ADR 0003](0003-cqrs-e-integracao-assincrona.md)

## Contexto e problema

O consolidado lê um saldo que ele mesmo projeta. Com o cache quente, o Postgres quase não participa do GET. Com o cache frio, a leitura e a escrita da projeção dividem o mesmo primário. O blueprint deixa uma réplica parametrizável, desligada, e uma variável `DB_READER_URL` para o dia em que a leitura for o problema. Sem um gatilho, a variável vira “ligada em prod porque produção tem réplica”.

## Drivers

- O read model já é outro banco (o do consolidado). Réplica não é o primeiro passo de CQRS; CQRS já aconteceu.
- Estorno retroativo muda o passado. Estratégia que grava saldo acumulado em todas as linhas futuras paga esse estorno com contenção.
- Custo de uma réplica `db.t4g.medium`: USD 53/mês em `us-east-1`. Barato comparado ao RDS primário, caro comparado a um índice que faltava.
- Lag visível. Réplica atrasada mente o caixa.

## Opções consideradas

### 1. Réplica ligada desde o dia 1

- Positivo: o caminho de código (`readOnly` → reader) nasce exercitado.
- Negativo: USD 53/mês para um `SUM` de 1.800 linhas que o primário faz em milissegundos. E um modo de falha novo (lag) no caminho do número que o comerciante lê.

### 2. Sem caminho de réplica no código, até precisar

- Positivo: menos um data source.
- Negativo: o dia em que o primário enche, a mudança é de código e de infra no mesmo incidente. A variável opcional custa poucas classes e zero dólar enquanto o URL está vazio.

### 3. Código pronto, réplica desligada, gatilho numérico

`DB_READER_URL` vazio: todo mundo no primário. Preenchido: `@Transactional(readOnly = true)` vai ao reader, com `LazyConnectionDataSourceProxy` para a conexão nascer na hora em que a transação sabe para qual lado vai. Escrita da projeção, outbox e lançamento ficam no primário sempre.

A réplica só é criada quando o [documento 12](../12-desempenho-e-dados.md) disparar: crédito de CPU da instância T caindo por causa de leitura, com hit ratio de cache ainda alto; ou p99 de cache miss acima de 300 ms com o tempo no Postgres; ou conexões de leitura empurrando o primário para 70% de `max_connections`.

Lag acima de 5 s: alarme, e a leitura volta ao primário.

Estratégia de dado em volta disso, na ordem em que se tenta:

1. Índice e uma sentença só para o acumulado (janela), agora.
2. Réplica de leitura do consolidado, no gatilho.
3. Snapshot mensal do acumulado, se a janela passar de 300 ms no p99 com história grande.
4. Aurora com reader autoscaling, se a quantidade de readers tiver de variar e o I/O justificar I/O-Optimized.
5. DynamoDB, OpenSearch ou Athena só quando a pergunta deixar de ser a chave `(comerciante, data)`. DynamoDB não é o destino natural do relatório com estorno retroativo.

- Positivo: o incidente de leitura tem um procedimento que não é “aumenta a instância”. O código não muda naquele dia; muda o tfvar e a variável.
- Negativo: código de routing pouco exercitado até o gatilho. O teste de integração com dois Postgres (Testcontainers) cobre o routing uma vez. Sem esse teste, a opção 3 é a opção 2 com uma classe morta.

## Decisão

Opção 3. Default de reader: **zero**. RDS Proxy: **desligado**, pelo motivo do documento 12 (o wrapper já cobre segredo; na instância, também o failover; o Proxy cobre multiplexação, que ainda não falta).

Aurora não é o default. DynamoDB não substitui a projeção enquanto o produto tiver relatório de período e estorno em data antiga.

## Limitação da AWS sobre read replica

A [documentação do RDS com Secrets Manager](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html) diz que RDS for PostgreSQL com senha mestre gerenciada pelo Secrets Manager **não aceita read replica**. O módulo `rds-postgres` mantém `manage_master_user_password = true` e recusa `read_replica_count > 0` numa `validation` da variável e numa `precondition` da instância. `DB_READER_URL` não é injetada enquanto o output `reader_endpoint` for nulo.

Quando o gatilho da opção 3 disparar, a ordem compatível com a senha gerenciada é:

1. **RDS Multi-AZ DB Cluster** (recomendação). Um escritor e dois standbys legíveis, reader endpoint no `DB_READER_URL`. Não é a read replica proibida: é outro formato de implantação, e a senha continua no Secrets Manager. O custo deixa de ser os USD 53 de uma réplica e passa a ser o de três instâncias. O plugin do wrapper na instância/cluster segue `failover,awsSecretsManager`.
2. **Aurora PostgreSQL** com readers e autoscaling, se o número de readers tiver de variar no dia e o I/O justificar. Também convive com senha gerenciada.
3. **Credencial da réplica fora do Secrets Manager**, com rotação por Lambda. Aí a read replica clássica volta a existir, e o módulo teria de aceitar `manage_master_user_password = false`. É a saída que abandona a rotação nativa do RDS. Não é a recomendação.

Se `enable_rds_proxy` estiver ligado, o host de `DB_URL` contém `.proxy-`. Os serviços não usam o plugin `failover` contra esse host: o plugin abriria conexão na instância e furaria o proxy. Fica só `awsSecretsManager`. O proxy já promove o escritor.

## Consequências

### Positivas

- USD 53 ficam de fora da fatura até a evidência.
- O read model continua um só. Não se cria um terceiro banco “por simetria com CQRS”.

### Negativas

- Alguém vai ligar a réplica porque “produção pede”. O parâmetro default 0 e este ADR são a resposta. Ligar sem o gatilho é mudança de decisão, não tuning.
- O teste com dois bancos precisa existir no consolidado. Se não existir, o agente de integração anota a dívida no lugar do teste, não aqui.

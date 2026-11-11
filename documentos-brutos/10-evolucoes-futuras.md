# 10 — Evoluções futuras

Nada desta lista está no caminho do RNF crítico. Cada item diz qual dor o traz e por que não entrou agora. A ordem é a de valor para o comerciante, não a de moda.

## 1. Fechamento de caixa e período contábil

**Dor.** Hoje um lançamento retroativo é sempre aceito. O caixa “de ontem” muda depois que o operador já conferiu.

**Quando.** Assim que existir o rito de fechar o dia. Até lá, travar competência é regra sem dono.

**Como.** Um contexto novo, Fechamento, que publica “dia D fechado”. Lançamentos consulta essa política antes de aceitar competência fechada. Não se coloca a trava dentro do agregado como constante. Reabrir o dia é comando explícito, auditado, não um update.

## 2. Multi-moeda

**Dor.** Nenhuma, enquanto a premissa P1 (só BRL) for verdade.

**Como.** `Dinheiro` ganha moeda de verdade, o saldo diário ganha chave de moeda, e o evento deixa de fixar `BRL`. Conversão é outro contexto (taxa, data da taxa, fonte). Misturar conversão dentro do lançamento esconde o câmbio. Exige ADR que substitui o [0016](04-adr/0016-representacao-monetaria.md).

## 3. Conciliação bancária e Open Finance

**Dor.** O saldo do caixa interno não é o saldo do banco. O comerciante concilia no olho.

**Como.** Contexto de Conciliação, consumidor do evento e de um extrato OFX/Open Finance. Casa movimento com lançamento por valor, data e um identificador externo que o lançamento ainda não tem. Não se estende o consolidado para “virar banco”. A fila do consolidado não transporta extrato: fila nova, contrato novo.

## 4. Relatório assíncrono e exportação

**Dor.** O período de 366 dias cabe no GET. O ano inteiro em CSV, com categoria, não cabe, e não deve segurar a API do pico.

**Como.** Comando de exportação, arquivo no S3, URL pré-assinada com validade curta, notificação quando pronto. A geração lê réplica, não o primário. Categoria no arquivo exige `categoria` no evento (campo aditivo, `eventVersion` 1 ainda serve se for opcional) ou uma consulta ao serviço de lançamentos. Preferência: campo aditivo no evento, para a exportação não criar chamada síncrona entre os serviços.

## 5. Papel de contador e vários comerciantes

**Dor.** Um contador não consegue ler dez lojas sem dez tokens.

**Como.** O claim deixa de ser um único UUID e passa a ser uma autorização explícita (lista ou um serviço de permissão). O filtro “igual ao claim” vira “pertence ao conjunto autorizado”. Sem isso, qualquer atalho de query string `?comercianteId=` reabre BOLA. É mudança de segurança, não de tela.

## 6. Multi-região active-passive

**Dor.** Indisponibilidade longa de `us-east-1`. O RTO atual não cobre a região.

**Como.** Piloto com um único escritor, Postgres com réplica lógica ou snapshot replicado, fila regional (SNS não atravessa região sozinho). Active-active de lançamento é conflito de saldo. O secundário no Brasil exige a volta de residência descrita no [ADR 0021](04-adr/0021-regiao-us-east-1.md), com o delta de custo do [documento 08](08-custos.md). O ADR 0021 já recusou multi-região no dia 1.

## 7. Kafka / MSK

**Dor.** Nenhuma a 20 evt/s.

**Gatilho.** Replay histórico vira rotina, ou o throughput sustentado passa a milhares de eventos/s, ou vários times consomem o log com retenção de semanas e processamento próprio. Aí o [ADR 0005](04-adr/0005-sns-sqs-mensageria.md) se revê. Até lá, segunda fila no mesmo SNS resolve o segundo consumidor por uma fração do preço do cluster.

## 8. Event sourcing do lançamento

**Dor.** Nenhuma enquanto o estado couber numa linha e a correção for estorno.

**Como.** O log passaria a ser a fonte e a tabela, uma projeção. Útil se o agregado ganhar ciclo de vida longo (parcela, agendamento, conciliação dentro do mesmo agregado). Adotar agora duplicaria o que a tabela append-only e a outbox já contam, com a complexidade de snapshot e de evolução de evento de domínio. O evento de integração continua existindo nos dois desenhos.

## 9. Service mesh ou VPC Lattice

**Dor.** Nenhuma com dois serviços sem chamada entre si.

**Gatilho.** Várias chamadas síncronas internas, necessidade de mTLS padronizado e de política de tráfego. Até lá, security group e IAM na fila são o controle, e um mesh seria o sistema mais complexo da conta.

## 10. BFF e API Gateway

**Dor.** Nasce quando existir tela que compõe extrato e saldo e esconde os dois paths.

**Como.** BFF na frente, serviços onde estão. O Gateway, recusado no [ADR 0022](04-adr/0022-cloudfront-vpc-origin.md), volta a fazer sentido com throttling por parceiro e plano de uso. O JWT continua validado no serviço. A distribuição CloudFront já é o lugar do front estático, com S3 e Origin Access Control, sem segundo domínio de borda.

## 11. FinOps

**Dor.** A estimativa de [custos](08-custos.md) vai descolar da fatura no primeiro mês, sobretudo NAT, endpoint e log.

**Como.** Budget com alerta em 50% e 80% do previsto (USD 777 em prod, USD 356 em hml e USD 143 em dev, na conta do [documento 08](08-custos.md), ambientes 24×7). Tag obrigatória `system=fluxo-caixa` e `env`. Revisão mensal das três linhas que realmente pesam: endpoint, NAT e RDS. Rightsizing do RDS pelo credit balance, não pelo palpite. Infracost no pull request, como no [documento 14](14-cicd-e-qualidade.md). Isso é processo, e o Terraform já nasce com a tag.

## 12. Feature flags

**Dor.** A transição e o experimento de fechamento precisam de interruptor sem deploy.

**Como.** Configuração externa simples (AppConfig ou até parâmetro de ambiente no início). Flag não é lugar de esconder regra de dinheiro por cliente. Flag de “aceitar competência fechada” é de comportamento de produto e tem dono.

## 13. Contract testing (Pact ou equivalente)

**Dor.** O JSON v1 vai quebrar no silêncio quando alguém renomear campo.

**Como.** O produtor de lançamentos verifica o contrato contra o consumidor do consolidado no CI. O teste de contrato do evento vale mais que um Pact HTTP entre serviços que não se chamam. Entra assim que os dois repositórios Maven estiverem estáveis. Até lá, um teste de serialização do JSON de exemplo do blueprint já pega o grosso.

## 14. Réplica de leitura e particionamento

**Dor.** O `SUM` do acumulado ou o relatório longo começa a aparecer no p99.

**Como.** Primeiro índice e cache (já estão). Depois réplica de leitura só do consolidado. Shard por comerciante é a última etapa e exige roteamento que hoje não existe. A conta de capacidade mostra que isso está longe do requisito de 50 req/s.

## 15. O que não entra nem como evolução sem um fato novo

Reescrever em outra linguagem, trocar PostgreSQL por DynamoDB no mesmo volume, adotar EKS para dois services, e event sourcing “para ficar pronto”. São mudanças de plataforma sem dor de negócio. O gatilho para reabri-las é um ADR novo com número medido — lag, custo ou limite de throughput — e não uma preferência.

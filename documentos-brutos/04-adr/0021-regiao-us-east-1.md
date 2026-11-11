# Região us-east-1, com transferência internacional

- **Status:** Aceito
- **Data:** 2026-11-10
- **Substitui:** [ADR 0019](0019-regiao-sa-east-1-lgpd.md)

## Contexto e problema

O ADR 0019 fixou `sa-east-1` para o dado de caixa nascer no Brasil. A revisão 2 do blueprint muda a borda para CloudFront com WAF de escopo `CLOUDFRONT` e certificado ACM, e passa a operar três ambientes. WAF e ACM dessa borda são recursos de `us-east-1` mesmo que o banco fique em São Paulo. Manter a computação em `sa-east-1` e a borda em `us-east-1` parte a conta em duas regiões sem resolver residência de verdade: o corpo do POST, que pode trazer descrição com dado pessoal, é inspecionado na borda.

A pergunta deixa de ser “onde é mais barato?” e passa a ser “residência estrita ainda é o requisito, e o que ela custa agora que a borda já toca a Virgínia?”.

## Drivers

- Custo comparado com preço de lista, não com ordem de grandeza.
- WAF de escopo CloudFront e o certificado do domínio alternativo do CloudFront exigem `us-east-1`.
- Serviço novo da AWS aparece primeiro na Virgínia. VPC Origin e o ciclo do Advanced JDBC Wrapper são exemplos desta leva, não uma promessa genérica.
- Latência São Paulo → Virgínia na casa de 110–130 ms de RTT, e o que o CloudFront de fato reduz.
- LGPD art. 33: transferência internacional é permitida com salvaguarda, e é proibida se o controlador não conseguir demonstrá-la. Residência não é o único modo de cumprir a lei.
- Um caminho de volta explícito, se um contrato exigir dado só no Brasil.

## Preço unitário (Price List, consulta em 2026-11-10)

| Item | us-east-1 | sa-east-1 |
|---|---:|---:|
| Fargate ARM, vCPU-hora | 0,03238 | 0,05570 |
| Fargate ARM, GB-hora | 0,00356 | 0,00612 |
| RDS `db.t4g.medium` Multi-AZ, hora | 0,129 | 0,275 |
| RDS `db.t4g.micro` Single-AZ, hora | 0,016 | 0,034 |
| gp3 Multi-AZ, GB-mês | 0,230 | 0,438 |
| Valkey `cache.t4g.small`, hora | 0,0256 | 0,0488 |
| NAT, hora e por GB | 0,045 | 0,093 |
| VPC endpoint, hora por AZ | 0,010 | 0,021 |
| ALB, hora | 0,0225 | 0,034 |
| CloudFront, GB, edge América do Sul, primeiros 10 TB | 0,110 | 0,110 |
| CloudFront, GB, edge Estados Unidos, primeiros 10 TB | 0,085 | — |
| CloudFront HTTPS América do Sul, 10 mil requests | 0,0220 | 0,0220 |
| EKS control plane, hora | 0,10 | 0,10 |

A produção inteira, mesmas quantidades, fica em **USD 778/mês** na Virgínia e **USD 1.467/mês** em São Paulo (1,89 vez). Os três ambientes 24×7 diferem em **USD 1.098/mês**, cerca de **USD 13,2 mil/ano**. A conta está no [documento 08](../08-custos.md). CloudFront não entra nessa diferença: o comerciante no Brasil é tarifado pelo edge de São Paulo nos dois desenhos. O API Gateway desta revisão usa o mesmo unitário de request nas duas colunas, então também não entra na diferença.

Não há linha de transferência inter-região no total, porque o desenho não replica dado entre regiões. O custo que a decisão compara é o de morar numa região, não o de copiar bytes para a outra.

## Opções consideradas

### 1. Continuar em `sa-east-1` (ADR 0019)

- Positivo: o disco do RDS, o backup e a fila nascem no Brasil. O texto do ADR 0019 continua válido como descrição desse mundo.
- Negativo: paga-se o prêmio de 1,9 vez. O WAF de CloudFront e o ACM do certificado continuam em `us-east-1`. O corpo do POST atravessa a borda mesmo assim. A “residência” fica incompleta no exato ponto em que a descrição, se o operador escrever dado pessoal, está no request.

### 2. Multi-região active-passive desde já

- Positivo: queda de uma região deixa de encerrar o RTO.
- Negativo: dobra o piso, replica dado pessoal e exige um único escritor. Continua evolução, não ponto de partida. O RTO de 1 h desta entrega é intra-região.

### 3. Tudo em `us-east-1`, com salvaguarda de transferência

VPC, RDS, ElastiCache, SNS, SQS, Cognito, logs, traces, backups, ECR, KMS e o ALB interno na Virgínia. CloudFront global, com WAF e ACM em `us-east-1` e PoP em GRU para o comerciante. Sem cópia para `sa-east-1`.

- Positivo: um lugar só. O prêmio de São Paulo sai da fatura. WAF, ACM e dado ficam sob a mesma decisão de transferência, em vez de uma residência no papel e uma borda fora dela. Serviço novo não espera a região de São Paulo habilitar.
- Negativo: dado de caixa fora do Brasil. O RTT de backbone entra no tempo que o comerciante percebe. Uma indisponibilidade de `us-east-1` não tem failover. A salvaguarda do art. 33 precisa existir de verdade (contrato, minimização, criptografia), não como frase neste ADR.

## Decisão

Opção 3. O ADR 0019 fica substituído. Backup e snapshot permanecem em `us-east-1`. Não se copia snapshot para `sa-east-1` “para ter uma cópia no Brasil”: essa cópia reabre a transferência no sentido inverso e finge uma residência que o sistema não opera.

### Latência

A ordem de grandeza do RTT entre São Paulo e a Virgínia, no backbone, é **110–130 ms**. Não é SLA da AWS; é a faixa usada para a decisão. O CloudFront mitiga o que é handshake, não o que é física:

- TLS 1.2+ termina no edge de GRU. O handshake (dois RTT ou mais) não vai até a Virgínia.
- HTTP/2 e HTTP/3 com conexão persistente: o request seguinte, na conexão já quente, paga **um** RTT de backbone mais o processamento.
- O que não some: esse um RTT. Um POST cujo processamento no ALB leva 80 ms chega ao cliente no Brasil perto de 200 ms com a conexão quente, e bem acima disso na primeira conexão se alguém medir incluindo o TLS até a Virgínia sem edge.

O SLO de p95 &lt; 200 ms do POST continua sendo o tempo **no ALB**, dentro da região, como no [documento 05](../05-requisitos-nao-funcionais.md). Redefinir esse SLO como tempo no navegador em São Paulo, com o processamento atual, não fecharia. O caixa aceita a diferença. Um pregão não aceitaria; este produto não é um pregão.

### LGPD

O art. 33 da Lei 13.709/2018 permite transferir dado pessoal para fora do Brasil quando o controlador oferece e comprova garantias de cumprimento dos princípios e dos direitos do titular. O mecanismo desta entrega:

- Cláusulas-padrão contratuais e o Data Processing Addendum da AWS, aceitos na conta. AWS é o operador de infraestrutura; o controlador do caixa é o comerciante, e esta solução é operadora perante ele.
- Minimização que já estava no ADR 0019 e continua: o evento não leva descrição nem categoria; o log não leva descrição nem token.
- Criptografia em trânsito e em repouso, CMK, acesso por IAM.
- Registro desta transferência neste ADR e no [documento 06](../06-seguranca.md).

Dado envolvido: identificador do comerciante, descrição livre (pode conter pessoal por uso, não por contrato), logs de acesso, backups. Não há CPF no contrato.

**Se a residência passar a ser exigência** (cláusula contratual, norma setorial, ou leitura da ANPD que esta salvaguarda não satisfaça):

1. A computação e o armazenamento voltam para `sa-east-1`. O delta é o da tabela do documento 08: cerca de **USD 690/mês** a mais na produção, cerca de **USD 1.098/mês** nos três ambientes.
2. O WAF de escopo CloudFront continua vendo o corpo do POST. Residência estrita também questiona essa inspeção. A alternativa é WAF regional no ALB em São Paulo e abrir mão do desenho de VPC Origin, de volta à família do ADR 0018, com a borda pública.
3. Snapshot esquecido em `us-east-1` anula a volta. O runbook de saída apaga backup, log e segredo na Virgínia depois da migração, com CloudTrail dessa exclusão.
4. Esta volta é um ADR novo. Não se “reativa” o 0019 por configuração.

Direitos do titular continuam como no ADR 0019: não há `DELETE` de lançamento. Encerrar a conta apaga o cadastro no Cognito e retém a trilha pelo prazo comercial. A transferência internacional não cria um direito de apagar a contabilidade.

## Consequências

### Positivas

- A estimativa de custo nasce na região em que a fatura vai chegar.
- Borda, segredo e banco obedecem à mesma decisão de lugar.
- O control plane do CloudFront deixa de ser uma exceção escondida numa conta que se dizia “só São Paulo”.

### Negativas

- O tempo percebido no Brasil inclui o RTT de backbone. Quem prometer 200 ms no PDV em São Paulo estará prometendo o SLO errado.
- A salvaguarda é contratual e operacional. Sem o DPA aceito na conta e sem a minimização no log, este ADR vira texto.
- Indisponibilidade regional de `us-east-1` continua fora do RTO de 1 h.

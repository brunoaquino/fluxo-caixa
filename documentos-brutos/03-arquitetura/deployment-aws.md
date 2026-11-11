# Deployment na AWS

Região `us-east-1` ([ADR 0021](../04-adr/0021-regiao-us-east-1.md)). Três ambientes, `dev`, `hml` e `prod`, com o dimensionamento da seção 15 do blueprint. A topologia abaixo é a de **produção** com API Gateway ligado ([ADR 0031](../04-adr/0031-api-gateway-rest-regional.md)): VPC em três zonas, NAT por zona, endpoints de interface, CloudFront com WAF, REST API regional, VPC Link v2, ALB interno, ECS Fargate, dois RDS Multi-AZ, ElastiCache com réplica, SNS/SQS/DLQ, Cognito, Secrets Manager com rotação e ADOT. Hml usa duas zonas, um NAT e endpoints. Dev usa um NAT, sem endpoint, e Fargate Spot. O dia a dia do desenvolvedor é o Docker local, com custo de nuvem zero.

## 1. Diagrama

![Deployment de produção na AWS: comerciante no Brasil acessa o WAF e o CloudFront no PoP GRU, que chega ao API Gateway REST regional com WAF regional e Cognito User Pool em us-east-1; pelo VPC Link v2 o tráfego entra no ALB interno das subnets privadas de aplicação e segue para as tasks de lançamentos e de consolidado no cluster ECS Fargate com sidecar ADOT e VPC endpoints; as subnets privadas de dados têm dois RDS PostgreSQL Multi-AZ e o ElastiCache Valkey; fora da VPC ficam SNS, SQS, DLQ, Secrets Manager, KMS, CloudWatch, X-Ray e ECR; o NAT fica nas subnets públicas](../assets/diagramas/d14-deployment-aws.svg)

S3 entra como gateway endpoint (sem custo horário) para o pull de camadas via ECR. A lista de interface é: ECR api, ECR dkr, CloudWatch Logs, SQS, SNS, Secrets Manager, STS, KMS e X-Ray. KMS e X-Ray não estão na lista mínima da seção 12 do blueprint; existem para a CMK e o sidecar ADOT não saírem por NAT. O agente de integração confere o Terraform.

CloudFront é global. O desenho o coloca ao lado da região porque o Web ACL de escopo `CLOUDFRONT` e o certificado ACM moram em `us-east-1`, e o PoP que o comerciante usa é GRU.

## 2. Rede

| Camada | Quem mora | Regra |
|---|---|---|
| Borda | CloudFront, WAF CLOUDFRONT, API Gateway, WAF regional | Única URL pública é o CloudFront. `/api/*` sem cache. O `execute-api` existe e o WAF regional o fecha. |
| Pública da VPC | NAT | Sem rota de entrada. O ALB não está aqui. |
| Aplicação | ALB interno, VPC Link, tasks, endpoints | O security group do ALB aceita o prefix list do CloudFront e o security group do VPC Link. Task sem IP público. Egress para RDS, Redis, endpoints e NAT. |
| Dados | RDS, ElastiCache | 5432 só do security group do serviço dono do banco. 6379 só do security group do consolidado. |

Três AZs em prod permitem perder uma zona e ainda agendar task nas outras duas. O mínimo de duas tasks usa `spread` por AZ. NAT por AZ evita que a queda de uma zona leve o egress das outras. O preço está no [custo](../08-custos.md): três NAT em `us-east-1` são USD 99/mês; os 27 endpoints são USD 197/mês.

Dev não cria endpoint: o tráfego AWS sai pelo único NAT. Hml cria os endpoints em duas zonas, para o ensaio da rede privada não acontecer pela primeira vez em prod.

## 3. Borda

[ADR 0022](../04-adr/0022-cloudfront-vpc-origin.md) e [ADR 0031](../04-adr/0031-api-gateway-rest-regional.md).

- `PriceClass_All`, HTTP/2 e HTTP/3, TLS `TLSv1.2_2021`, ACM em `us-east-1`.
- WAF de escopo CLOUDFRONT: Common, Known Bad Inputs, SQLi, IP Reputation, rate-based por IP. AnonymousIpList em count no dev. Log do WAF com corpo e `Authorization` redigidos.
- Com `enable_api_gateway = true`, `/api/v1/lancamentos*` e `/api/v1/consolidados*` usam a origem HTTPS do API Gateway (`origin_path` `/v1`, header `X-Origin-Verify`, origin request policy `AllViewerExceptHostHeader`). O comportamento default continua 404 na função, sem chamar origem.
- REST API regional, stage `v1`, authorizer Cognito por escopo, VPC Link v2 no ALB. WAF regional no stage exige o header. Rotação do valor em 30 dias (90 em dev), mantendo o anterior.
- ALB interno, listener HTTP 80. O TLS da internet termina no CloudFront; o trecho VPC Link → ALB é HTTP na rede da AWS. Regras `/api/v1/lancamentos*` e `/api/v1/consolidados*`, mais o prefixo `/v1` reescrito quando o gateway está ligado. Health check: `GET /actuator/health/readiness`, direto no target, sem passar pelo listener. O access log vai para S3 com SSE-S3: o ELB não aceita CMK nesse bucket.
- O limite rate-based fica **acima** do pico legítimo de um IP. 50 req/s é o pico do serviço, não de um cliente. A ordem de partida é 30.000 requests por IP a cada 5 minutos. O throttle do gateway no GET do consolidado fica acima desses 50 em hml e prod.
- Shield Standard acompanha o CloudFront. Shield Advanced fica de fora.
- `/actuator` e o Swagger não são rota do gateway nem do ALB.
- `enable_api_gateway = false` tira o gateway e devolve a VPC Origin do ADR 0022.

## 4. Computação

| Serviço ECS | Task em prod | Mínimo | Máximo | Capacidade |
|---|---|---|---|---|
| `lancamentos` | 0,5 vCPU, 1 GiB, ARM64, sidecar ADOT incluso, on-demand | 2 | 10 | CPU 60% e request por target |
| `consolidado` | 1 vCPU, 2 GiB, ARM64, sidecar incluso, on-demand | 2 | 10 | CPU 60%, request por target e backlog da fila |

O blueprint (seção 15) fixa o máximo de prod em 10 para os dois. Dev: 0,5 vCPU / 1 GiB, mínimo 1, máximo 2, Fargate Spot, nos dois serviços. Hml: lançamentos 0,5/1 e consolidado 1/2, mínimo 1, máximo 3, on-demand.

Deployment circuit breaker com rollback ([ADR 0020](../04-adr/0020-estrategia-de-deploy.md)).

A task de 1 vCPU do consolidado é maior do que o pico de 50 req/s exige (ρ de cerca de 17% com duas tasks). O tamanho existe para caber o sidecar e o JIT. O lançamentos fica em 0,5 vCPU porque o caminho de escrita é mais curto.

A raiz do container é somente leitura. O `/tmp` é um volume do Fargate e nasce como root. O entrypoint da imagem faz `chmod 1777` e desce para o usuário `app`; sem isso o Tomcat não cria o diretório temporário e a task morre no start.

## 5. Dados

| Recurso | Produção | Papel na falha |
|---|---|---|
| RDS `lancamentos` | PostgreSQL 17, `db.t4g.medium`, Multi-AZ, gp3 50 GB, KMS, PITR, senha gerenciada | Standby noutra AZ. Falha do consolidado não o alcança. |
| RDS `consolidado` | Igual, instância separada. Réplica de leitura parametrizável, default 0 | Restaura sem restaurar a trilha de lançamentos. |
| RDS Proxy | Parametrizável, default desligado | Não substitui o wrapper de segredo. [Documento 12](../12-desempenho-e-dados.md). |
| ElastiCache | Valkey, `cache.t4g.small`, uma réplica, failover, TLS, sem AUTH token | Perda do primário promove a réplica. Perda do cache degrada para o RDS. |
| Secrets Manager | Um segredo por RDS, rotação de 30 dias em prod e hml, 90 em dev | O wrapper rebusca. A senha não está na imagem nem no Git. |

RPO ≤ 5 min vem do PITR. RTO ≤ 1 h é o tempo de restaurar dentro da região, ensaiado em game day. Não é failover de região.

`DB_READER_URL`, quando a réplica existir, manda só transação `readOnly` para o reader. O consumidor da projeção permanece no primário ([ADR 0030](../04-adr/0030-replica-leitura-estrategia-de-dados.md)).

## 6. Mensageria e identidade

- SNS, SQS e DLQ com SSE-KMS. Policy: a role de lançamentos publica; a role de consolidado consome. A policy do tópico não inclui `sns:Unsubscribe`: o SNS rejeita essa action. Prefixo `fluxo-caixa-prod-` (e `dev` / `hml` nos outros ambientes). O serviço lê ARN e URL, não o nome.
- Cognito User Pool no plano Essentials, resource server `fluxo-caixa`, escopos `lancamentos:write`, `lancamentos:read`, `consolidado:read`. Prefixo de autoridade `fluxo-caixa/`.
- Pre Token Generation Lambda (v2, plano Essentials) insere `comerciante_id` e os três escopos do resource server no access token. O `InitiateAuth` sozinho não traz escopo customizado; sem o acréscimo o API Gateway responde 403. Sem o claim o serviço responde 401. O domínio hospedado é `fluxo-caixa-<env>-<account_id>`.
- JWKS buscado pelo serviço. Não há PEM na imagem.

## 7. Observabilidade na implantação

Sidecar ADOT: traces para X-Ray, métricas para CloudWatch. Log JSON com `traceId`, `spanId` e `correlationId`. O relay republica o `traceparent` guardado na outbox. Detalhe e as queries no [documento 07](../07-observabilidade.md).

Alarmes (5xx, p99, profundidade e idade da fila, DLQ, outbox, circuito aberto, `RotationFailed`) publicam num tópico SNS de operação. Container Insights desligado em dev, ligado em hml, enhanced em prod. Retenção de log: 7, 14 e 30 dias.

## 8. Escalabilidade

Escala horizontal até 10 tasks por serviço em prod. O consolidado também escala pelo backlog da fila. O próximo gargalo, se o pico deixar de ser 50 req/s, é o `SUM` do acumulado e o primário do Valkey, não o CloudFront. A resposta está no [documento 12](../12-desempenho-e-dados.md): janela SQL, depois réplica, bem depois outro read store. Nada disso é necessário para o requisito atual.

## 9. Disaster recovery

| Cenário | Comportamento | RTO esperado |
|---|---|---|
| Task morta | ECS substitui. ALB tira do target group no readiness. | minutos |
| AZ morta | Task na outra AZ segue. RDS Multi-AZ promove. ElastiCache promove a réplica. NAT das outras AZs segue. | minutos para dado gerenciado |
| Mensagem poison | DLQ, serviço continua | operação humana para aquele evento |
| Corrupção lógica | PITR, no máximo 5 min atrás | até 1 h |
| Rotação de senha falha | Alarme `RotationFailed`. Wrapper rebusca. Runbook do [ADR 0024](../04-adr/0024-rotacao-de-segredos.md). | minutos se o runbook rodar |
| Região `us-east-1` indisponível | Sem active-passive. O RTO de região não é o RTO de 1 h. | evolução, com decisão jurídica se o secundário sair dos Estados Unidos |

Restauração é para uma instância nova. Não se restaura por cima da instância que ainda recebe escrita.

## 10. Ambientes

| | dev | hml | prod |
|---|---|---|---|
| AZs | 2 | 2 | 3 |
| NAT | 1 | 1 | 3 |
| Endpoint de interface | não | 2 AZs | 3 AZs |
| Fargate | Spot | on-demand | on-demand |
| RDS | micro, Single-AZ, 20 GB | small, Single-AZ, 20 GB | medium, Multi-AZ, 50 GB |
| Backup | 1 dia, sem deletion protection | 7 dias | 14 dias, PITR, deletion protection |

State Terraform em S3, um prefixo por ambiente, lock por lockfile, em `us-east-1` ([ADR 0029](../04-adr/0029-tres-ambientes-state-remoto.md)).

## 11. O que o diagrama não inclui de propósito

Conta separada por ambiente, Transit Gateway, Shield Advanced, multi-região e EKS. São camadas de uma organização com vários produtos ou com o gatilho do [documento 13](../13-ecs-vs-eks.md). Para dois serviços, aumentam o piso sem mudar o RNF. A comparação com `sa-east-1` está no documento de custos: a mesma produção sairia 1,92 vez mais cara, e a região vigente é a Virgínia com a salvaguarda do art. 33, não por desconhecimento do preço de São Paulo.

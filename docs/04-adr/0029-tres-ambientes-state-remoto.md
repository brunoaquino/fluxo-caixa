# Três ambientes e state remoto do Terraform

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0010](0010-terraform-iac.md), que permanece aceito

## Contexto e problema

O ADR 0010 escolheu Terraform com ambientes `dev` e `prod` e state remoto. A revisão 2 acrescenta `hml`, com tamanho próprio (seção 15 do blueprint): hml ensaia a produção sem pagar três NAT e sem Multi-AZ. Dois ambientes obrigavam o ensaio a acontecer em dev (barato demais, rede diferente) ou em prod (tarde demais).

## Drivers

- Um state por ambiente, lock, sem `tfstate` no Git.
- Promoção de imagem dev → hml → prod, com a infra de cada um já existente. Promover infra “junto com a imagem” no mesmo apply é como o dev de um experimento derruba a rede de prod.
- Hml parecido com prod no caminho da aplicação (endpoints, Container Insights, on-demand) e menor na rede e no banco.
- Apply de prod só pelo pipeline, com aprovação.

## Opções consideradas

### 1. Dev e prod, hml “quando precisar”

- Positivo: uma conta a menos de NAT e de RDS.
- Negativo: o primeiro ensaio de migração Flyway com duas versões da aplicação acontece em prod. O custo de hml (USD 356/mês em `us-east-1` se ficar ligado o mês inteiro) é o preço de não ensaiar em prod.

### 2. Três contas AWS, uma por ambiente

- Positivo: explosão de raio. Uma credencial de dev não vê prod.
- Negativo: para dois serviços, o custo de operar três contas (trail, usuário, limite) compete com o benefício. A separação que importa neste porte é state, role e approval, não a conta. Conta separada continua evolução se o produto ganhar outro sistema na mesma organização.

### 3. Três ambientes, três states, na mesma conta, em S3

Prefixo por ambiente no bucket de state, lock nativo do S3 (`use_lockfile`), criptografia com KMS, versionamento do bucket. Um módulo, três `tfvars`. Nomes de fila e tópico com o prefixo do ambiente, como o blueprint já exige (`fluxo-caixa-dev-`, `fluxo-caixa-hml-`, `fluxo-caixa-prod-`). A aplicação continua lendo ARN e URL, não o nome.

- Positivo: `plan` de hml não é `plan` de prod. O lock impede dois applies no mesmo state. O versionamento do bucket é o rollback do state, não do RDS.
- Negativo: a mesma conta significa que uma role mal escrita enxerga os dois RDS. A fronteira é IAM, e IAM errado não tem a rede de segurança da conta separada. A role de dev não tem permissão no state de prod. Isso é política, testada no pipeline, não só escrita.

## Decisão

Opção 3. Backend S3 em `us-east-1`, junto da infra. Não há state em `sa-east-1`.

Dimensionamento é o da seção 15 do blueprint, repetido nos tfvars, não copiado para este ADR para não virar uma terceira fonte. O que este ADR fixa é a existência dos três e a regra de cada um:

| Ambiente | Para quê | O que não é |
|---|---|---|
| dev | Integração na AWS, Spot, Single-AZ, sem endpoint | Produção pequena |
| hml | Ensaio de migração, de rotação de segredo e de deploy, com endpoints e sem Multi-AZ | Cópia fiel da fatura de prod |
| prod | Tráfego do comerciante, Multi-AZ, deletion protection, três NAT | Lugar de experimentar tfvars |

Destroy de prod não é alvo do dia a dia. Destroy de dev é permitido e é o jeito certo de parar a fatura, melhor do que “desligar task” e deixar NAT.

O pipeline de Terraform (`fmt`, `validate`, `tflint`, Checkov, e o que o [documento 14](../14-cicd-e-qualidade.md) acrescenta) roda nos três. Apply automático, se existir, para em dev. Hml e prod exigem aprovação do environment.

## Consequências

### Positivas

- O ensaio de Flyway com rolling tem um lugar que não é o caixa do comerciante.
- A fatura de dev (USD 143) e a de hml (USD 356) estão na estimativa antes de alguém criar “rapidinho”.

### Negativas

- Três ambientes ligados 24×7 custam USD 1.216/mês antes de qualquer otimização de horário. Hml desligado à noite economiza task, não endpoint. A decisão de manter hml ligado é operacional e se revê na primeira fatura, com o número na mesa.
- State remoto é agora um componente crítico. Perder o bucket sem versionamento é perder a capacidade de mudar a infra com segurança. Versionamento e a CMK fazem parte do ambiente, não do “depois”.

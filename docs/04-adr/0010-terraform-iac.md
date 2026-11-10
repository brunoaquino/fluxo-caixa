# Terraform como IaC

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

VPC, ALB, WAF, ECS, dois RDS, ElastiCache, SNS, SQS, Cognito, alarmes e IAM precisam ser recriáveis em `dev` e `prod`, revisáveis em pull request e destruíveis. A região e os nomes de fila já estão fixados no blueprint.

## Drivers

- Estado declarativo e plan visível antes do apply.
- Módulos reutilizáveis entre ambientes, com tamanho diferente (NAT único no dev, NAT por AZ na prod).
- Ferramenta que o repositório expressa em HCL, sem gerar JSON ilegível à mão.
- Sem custo de licença para o porte deste sistema. A licença da ferramenta em si está tratada no [documento 08](../08-custos.md): Terraform é BSL, uso gratuito neste contexto; o runtime não embute a CLI.

## Opções consideradas

### 1. AWS CDK

- Positivo: tipos, loops e testes na linguagem do time (TypeScript ou Java), construct de alto nível.
- Negativo: a unidade de review deixa de ser a infraestrutura e passa a ser o programa que a gera. Diff de CloudFormation sintetizado é mais difícil de ler do que diff de HCL. Mais uma cadeia de build para a infra.

### 2. CloudFormation puro

- Positivo: nativo, sem estado externo além do stack, drift detection da AWS.
- Negativo: verbosidade, módulos fracos comparados ao que os dois ambientes precisam, e templates grandes de VPC/ECS que a comunidade já resolveu em módulo Terraform. Rollbacks de stack têm arestas conhecidas com recurso que não volta atrás (RDS).

### 3. Terraform (provider AWS 6.x), módulos e ambientes `dev` e `prod`

Estado remoto (S3 + lock), `make tf-validate` no CI, um módulo por preocupação (rede, borda, serviço ECS, dados, mensageria, identidade, observabilidade). Nomes de fila e tópico iguais aos do blueprint, para a aplicação e a infra não divergirem.

- Positivo: plan legível, ecossistema de módulos, o mesmo fluxo nos dois ambientes, provider 6.x alinhado ao blueprint. Validate e fmt cabem no pipeline sem aplicar nada.
- Negativo: estado é um artefato crítico (criptografado, lock, sem commit do `tfstate`). Provider 6.x muda argumento em relação à linha 5; o código não copia exemplo antigo de blog. HCL não impede um `apply` de prod a partir da máquina errada — isso é política de CI, não de sintaxe.

## Decisão

Opção 3. Apply de produção só pelo pipeline, com o ambiente `prod` protegido. Apply manual fica restrito a `dev`. O README de `infra/terraform` é o passo a passo; este ADR só registra a escolha da ferramenta.

OpenTofu permanece como saída se a licença BSL se tornar impeditiva para a organização. O HCL escrito para este porte é portável o bastante para essa troca não ser uma reescrita.

## Consequências

### Positivas

- A revisão da infra é um diff, não um clique no console.
- Desviar o console sem refletir no código aparece como drift a corrigir, não como “ajuste que ficou”.

### Negativas

- Dois lugares definem nome de fila: Terraform e configuração da aplicação. O blueprint é a lista canônica; os dois lados copiam dela. Um teste de fumaça na AWS de dev que publica e consome pega divergência que o `fmt` não pega.
- Recurso com dado (RDS) não entra em destroy casual. O ambiente `prod` não tem destroy no alvo do dia a dia do Makefile.

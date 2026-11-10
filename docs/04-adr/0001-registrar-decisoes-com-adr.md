# Registrar decisões de arquitetura em ADR

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O desafio pede justificativa das escolhas, e o sistema vai ser lido por quem não participou da discussão. Sem um lugar estável para o “por quê”, a decisão migra para o comentário do pull request e se perde. O [blueprint](../blueprint-tecnico.md) fixa o contrato; ele não substitui o registro da alternativa rejeitada.

## Drivers

- A banca e o time futuro precisam ver opções, não só o resultado.
- A decisão tem de ser curta o bastante para ser lida, e estável o bastante para ser citada.
- O formato precisa caber em Markdown no repositório, sem ferramenta extra.

## Opções consideradas

### 1. Só o README e o blueprint

- Positivo: um arquivo só, sem processo.
- Negativo: o README vira depósito. Trade-off antigo some na próxima edição. Não dá para apontar “a decisão X” numa revisão.

### 2. Wiki ou página fora do Git

- Positivo: fácil de comentar.
- Negativo: diverge do código. O desafio exige a documentação no repositório. A página externa não viaja com o commit que mudou a fila.

### 3. ADR no formato MADR, versionado em `docs/04-adr`

- Positivo: contexto, drivers, opções com prós e contras, decisão e consequências no mesmo texto. O diff do Git é o histórico. Dá para marcar uma decisão como substituída sem apagar o passado.
- Negativo: mais um documento para atualizar quando a decisão muda. ADR esquecido vira mentira educada.

## Decisão

Opção 3. Cada decisão relevante desta entrega tem um ADR numerado, status **Aceito** e data **2026-11-10**. Decisão nova não reescreve a antiga: acrescenta um ADR que a substitui e aponta o número anterior.

O blueprint continua sendo a fonte do contrato (nomes, portas, JSON). O ADR é a fonte do porquê. Se os dois discordarem no contrato, vale o blueprint e o ADR é corrigido.

## Consequências

### Positivas

- Trade-off fica explícito (SNS contra Kafka, ALB contra API Gateway, região contra custo).
- Dá para revisar uma decisão sem reler a arquitetura inteira.

### Negativas

- Vinte arquivos para manter alinhados ao código. Uma decisão implementada diferente do ADR é defeito de documentação, tratado como defeito.

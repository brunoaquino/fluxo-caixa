# Arquitetura hexagonal

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O domínio de lançamento (dinheiro, estorno, imutabilidade) é a parte que a banca e o negócio vão questionar. Esse código não pode depender de anotação de framework, de cliente AWS nem de tabela. Ao mesmo tempo, a aplicação precisa de HTTP, JPA, SNS e Redis de verdade.

## Drivers

- Teste de invariante sem subir Spring.
- Troca de LocalStack por AWS sem editar regra de estorno.
- Regra de dependência que o build verifica, não uma convenção de pasta que alguém fura na pressa.
- Pacotes já nomeados no blueprint.

## Opções consideradas

### 1. Camadas técnicas clássicas (`controller`, `service`, `repository`) com domínio anêmico

- Positivo: familiar, menos arquivos.
- Negativo: a invariante de estorno acaba no service que já importa o SDK, e o teste vira `@SpringBootTest` para qualquer regra. Em seis meses o “service” conhece fila, token e SQL.

### 2. Pacotes por feature, sem restrição de compilação

- Positivo: navegação fácil.
- Negativo: a fronteira é social. Não impede `domain` de importar `software.amazon.awssdk`.

### 3. Portas e adaptadores, com ArchUnit

```text
domain/          entidades, value objects, eventos, exceções — sem Spring
application/     casos de uso e portas de saída
adapters/in      web e, no consolidado, messaging
adapters/out     persistence, messaging, cache
config/
```

ArchUnit falha o build se `domain` depender de Spring ou de `adapters`, e se `application` depender de `adapters`.

- Positivo: a regra de estorno mora em `Lancamento` e roda em teste de unidade. O JSON do evento mora no adaptador de mensagem (ACL). O mesmo caso de uso serve ao teste de contrato e ao controller.
- Negativo: mais interfaces e mapeamentos DTO ↔ domínio ↔ entidade JPA. Para dois agregados pequenos, parte dessa estrutura parece cerimônia. É o preço de manter a regra legível quando o adaptador AWS crescer.

## Decisão

Opção 3, exatamente nos pacotes do blueprint. O controller não é lugar de regra: extrai o `comerciante_id` do JWT, monta o comando e traduz falha de domínio em problema RFC 9457.

Não se cria porta de saída “por precaução” para um barramento genérico. As portas que existem são as que têm adaptador: repositório, outbox, publish, cache, fila.

## Consequências

### Positivas

- O nível 4 do C4 é legível porque o domínio não está emaranhado no filtro de segurança.
- Teste de arquitetura pega o atalho antes do code review.

### Negativas

- ArchUnit mal escrito vira ruído (falso positivo em `config`). O teste cobre os três pacotes de regra e deixa `config` como composição, que por natureza conhece todo mundo.
- Mapear `Dinheiro` ↔ `NUMERIC` ↔ string do evento em três bordas é repetição consciente. Compartilhar um módulo de domínio entre os serviços recriaria um kernel compartilhado que o context map recusou.

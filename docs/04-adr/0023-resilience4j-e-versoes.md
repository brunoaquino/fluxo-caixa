# Java 25, Spring Boot 4.0.9 e Resilience4j 2.4.0

- **Status:** Aceito
- **Data:** 2026-11-10
- **Atualiza:** [ADR 0007](0007-java-25-spring-boot-4.md), que fica substituído

## Contexto e problema

O ADR 0007 escolheu Java 25 e Spring Boot 4 sem pinar o patch, e registrou que biblioteca incompatível com a linha 4 exigiria outro ADR. Na prática o código chegou a andar em Boot 4.1.x, e o starter `resilience4j-spring-boot4` disponível não entrava nesse classpath: ele compila contra `spring-boot-autoconfigure` 4.0.0. O relay ficou só com `proxima_tentativa_em`, sem jitter e sem circuito. O blueprint, na revisão 2, fixa o patch e exige o Resilience4j.

## Drivers

- Um patch só, nos dois serviços. Boot 4.0 num e 4.1 no outro não é opção.
- O starter oficial, não um auto-config copiado para o repositório.
- Circuito e retry onde há chamada externa (SNS, Redis), sem colocar essa chamada no caminho do POST.
- Suporte do Boot explícito, com gatilho de saída no dia em que o Resilience4j declarar 4.1.

## Matriz

Versões alvo. O agente de integração confere o que o `pom` resolveu (`mvn dependency:tree`). O que esta decisão trava é a linha, não um patch transitivo que o BOM ainda pode mover dentro da linha.

| Peça | Linha |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | **4.0.9**, última 4.0.x desta decisão |
| Spring Framework | 7.0, trazido pelo BOM do Boot 4.0 |
| Resilience4j | **2.4.0**, artefato `resilience4j-spring-boot4`, mais `spring-boot-starter-aop` (AspectJ), como o Boot 4.0 exige para o starter |
| Jackson | 3, trazido pelo Boot 4.0 (`tools.jackson`) |
| Hibernate | 7, no serviço que usa JPA (lançamentos) |
| Testcontainers | 2, artefatos `testcontainers-junit-jupiter` e `testcontainers-postgresql` |
| AWS SDK for Java | v2 (pin observado no pom em 2026-11-10: 2.57.12; o patch pode ter sido ajustado pelo agente de serviço) |
| AWS Advanced JDBC Wrapper | linha compatível com o driver PostgreSQL usado pelo Boot 4.0; o plugin `awsSecretsManager` é o requisito, a versão exata é a do pom |

O único release do `resilience4j-spring-boot4` na 2.4.0 compila contra autoconfigure 4.0.0 / `resilience4j-spring6`. Boot 4.1 não tem suporte declarado. Subir o parent para 4.1 com esse starter no classpath é build quebrado ou, pior, auto-config que não dispara. Copiar o auto-config para dentro do serviço seria um fork. Os dois foram recusados.

## Onde cada padrão entra

| Padrão | Onde | Para quê |
|---|---|---|
| CircuitBreaker + Retry (backoff exponencial com jitter) + TimeLimiter | Relay, cliente SNS | Falha do publish não vira loop. Circuito aberto adia o lote; o evento fica na outbox. |
| CircuitBreaker + timeout, fallback no Postgres | Consolidado, cliente Redis | Cache lento ou morto não segura o GET nem derruba a readiness. |
| Bulkhead (semaphore) | Consumidor SQS | Limita o paralelismo da task para o pool JDBC continuar sendo o teto de propósito, não de acidente. |
| Retry com backoff | Consumidor, falha transitória de banco | Dentro do visibility. Não retenta erro de domínio. |
| RateLimiter | Consultas do consolidado, **opcional** e desligado | Só se um cliente legítimo, atrás de um IP que o WAF não separa, passar a ameaçar o p99. Ligado cedo demais, vira a causa da perda de 5%. |

Métricas do Resilience4j saem no Micrometer e no `/actuator/prometheus`. O estado do circuito aparece em `/actuator/health` sem mudar a readiness para `DOWN`. Não existe circuito entre os dois serviços: não existe chamada entre eles.

O backoff da coluna `proxima_tentativa_em` continua. O Resilience4j não substitui a outbox; ele impede que o relay, com o SNS doente, publique em rajada no instante em que o SNS volta.

## Ciclo de suporte e gatilho de upgrade

A política publicada pela Spring para o Boot dá à minor uma janela de suporte open source e, depois, suporte comercial. A página de suporte do projeto é a fonte da data: esta decisão não inventa o dia do GA. Se a janela open source da 4.0 estiver no fim, isso **não** autoriza pular para 4.1 enquanto o starter não declarar compatibilidade. Ficar na última patch 4.0.9, com o CVE acompanhado, é preferível a um classpath que o Resilience4j não suporta.

**Gatilho.** O primeiro artefato `resilience4j-spring-boot4` cuja POM compila contra `spring-boot-autoconfigure` 4.1.x. No dia em que isso existir:

1. Os dois poms sobem juntos, na mesma pull request.
2. Rodam ArchUnit, Testcontainers e o teste de contrato do evento.
3. Não se mantém um serviço na 4.0 e o outro na 4.1.

Até lá, dependabot que proponha Boot 4.1 é recusado com ponteiro para este ADR.

## Opções consideradas

### 1. Boot 4.1 sem Resilience4j

Era o estado que o ADR 0007 acabou descrevendo na implementação. O relay só com timestamp. Sem jitter, duas tasks acordam juntas. Sem circuito, um SNS lento ocupa o agendador até o timeout do cliente HTTP.

### 2. Boot 4.1 com auto-config forkado

- Positivo: “temos circuito” e o parent novo.
- Negativo: o fork não recebe o patch do Resilience4j. O dia em que o starter oficial sair para 4.1, o fork briga com ele.

### 3. Boot 4.0.9 e Resilience4j 2.4.0

- Positivo: o starter oficial. Os dois serviços na mesma patch. O circuito fica onde há I/O externo.
- Negativo: a linha 4.0 envelhece enquanto a 4.1 segue. Aceito, com o gatilho acima. Virtual threads e o teto do Hikari continuam como no ADR 0007: thread barata não autoriza pool infinito.

## Decisão

Opção 3. Java 25, Temurin, imagem não-root, um Maven wrapper por serviço. Pacotes `br.com.fluxocaixa.lancamentos` e `br.com.fluxocaixa.consolidado`.

## Consequências

### Positivas

- O POST continua sem SNS no caminho, e o relay ganha um limite que o timestamp sozinho não expressa.
- A matriz cabe numa página. Surpresa de versão vira diff de pom, não virada de sprint.

### Negativas

- Ficar na 4.0 é dívida com data. O gatilho precisa de um dono (a revisão mensal do release note do Resilience4j), senão a dívida vence em silêncio.
- RateLimiter opcional é uma tentação. O default é desligado.

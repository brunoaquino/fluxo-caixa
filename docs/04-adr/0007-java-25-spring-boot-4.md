# Java 25 e Spring Boot 4

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O serviço é I/O-bound: JDBC, Redis, JWKS e SNS no meio de poucas dezenas de requisições simultâneas (Little dá 2,8 em voo no pico de leitura). A linguagem precisa expressar o domínio sem ruído, rodar em container enxuto e ter suporte longo.

## Drivers

- LTS. Java 25, publicado em setembro de 2025, é a LTS seguinte à 21.
- Virtual threads para o bloqueio de JDBC e HTTP sem um pool de plataforma dimensionado no chute.
- Ecossistema que o time opera: Spring, Flyway, Testcontainers, ArchUnit.
- Imagem de container reproduzível. JDK de distribuição com licença que não cobra por núcleo.

## Opções consideradas

### 1. Java 21 LTS e Spring Boot 3.x

- Positivo: curva conhecida, mais tempo de mercado, menos surpresa de biblioteca.
- Negativo: abre mão da LTS atual e do alinhamento com Spring Boot 4, que é a linha definida no blueprint. Manter 21 “por segurança” em projeto novo em 2026 é dívida de largada.

### 2. Kotlin ou outra JVM language

- Positivo: null-safety na linguagem.
- Negativo: segundo dialeto para um domínio que cabe em Java claro. Não há requisito que Kotlin resolva e Java 25 não resolva.

### 3. Java 25 e Spring Boot 4.x na última estável do Maven Central

Runtime Eclipse Temurin 25 (licença de uso sem custo por núcleo; ver [custos](../08-custos.md)). Um Maven wrapper por serviço. Virtual threads ligadas no servidor web e nos clientes bloqueantes.

- Positivo: LTS, virtual threads maduras, um processo aguenta o pico de 50 req/s folgado porque o limite deixa de ser thread de plataforma. Spring Boot 4 é a linha que acompanha essa geração de Jakarta e do ecossistema.
- Negativo: biblioteca que ainda não subiu para a linha 4 quebra o build. A versão de patch não está pinada no blueprint de propósito (“última estável”); o código deve pinar a que resolver o build e registrar no `pom`. Este ADR não inventa número de patch.

## Decisão

Opção 3. Dois projetos Maven independentes, pacotes `br.com.fluxocaixa.lancamentos` e `br.com.fluxocaixa.consolidado`. Imagem com Temurin 25, usuário não-root, JAR em camadas.

Virtual threads não autorizam pool JDBC infinito. O Hikari continua limitado (máximo 10 por task), como calculado na capacidade. Thread barata com conexão cara só muda o lugar do acidente.

## Consequências

### Positivas

- O modelo de concorrência combina com o perfil medido: poucas requisições em voo, cada uma esperando rede.
- Um wrapper Maven tira a exigência de Maven instalado na máquina, desde que o JDK 25 exista.

### Negativas

- Se uma dependência crítica não publicar artefato compatível com Spring Boot 4, a mitigação é subir o patch ou, em último caso, registrar um ADR novo — não misturar Boot 3 num serviço e Boot 4 no outro.
- JDK comercial da Oracle fica fora da imagem. Entrar com ele criaria custo de licença que a estimativa trata como zero.

# Arquitetura

Visão de solução do Fluxo de Caixa. Os nomes de serviços, filas, portas e variáveis são os do [blueprint](../blueprint-tecnico.md).

| Documento | Conteúdo |
|---|---|
| [C4 nível 1 — contexto](c4-nivel1-contexto.md) | Pessoas e sistemas externos |
| [C4 nível 2 — containers](c4-nivel2-containers.md) | Serviços, bancos, mensageria, cache, identidade |
| [C4 nível 3 — lançamentos](c4-nivel3-lancamentos.md) | Componentes hexagonais do `lancamentos-service` |
| [C4 nível 3 — consolidado](c4-nivel3-consolidado.md) | Componentes hexagonais do `consolidado-service` |
| [C4 nível 4 — registrar lançamento](c4-nivel4-registrar-lancamento.md) | Código-alvo do caso de uso de escrita |
| [Arquitetura-alvo](arquitetura-alvo.md) | Estilos, padrões e trade-off de cada um |
| [Fluxos de dados](fluxos-de-dados.md) | Sequências: escrita, outbox, consolidação, cache, estorno, falha, DLQ |
| [Padrões de integração](padroes-de-integracao.md) | EIP entre a borda e os serviços, e entre lançamentos e consolidado |
| [Deployment AWS](deployment-aws.md) | CloudFront, API Gateway, ALB interno, VPC, três ambientes e DR em `us-east-1` |
| [Structurizr DSL](structurizr/workspace.dsl) | Workspace com context, containers, components, dynamic e deployment |

Os diagramas Mermaid usam a sintaxe C4 (`C4Context`, `C4Container`, `C4Component`), que o GitHub renderiza. O workspace Structurizr é a fonte editável. A imagem `structurizr/cli:latest` de novembro de 2026 só imprime o aviso de migração; a tag `2025.03.28` valida e exporta este arquivo:

```bash
docker run --rm \
  -v "$PWD/docs/03-arquitetura/structurizr":/usr/local/structurizr \
  structurizr/cli:2025.03.28 \
  validate -w /usr/local/structurizr/workspace.dsl

docker run --rm \
  -v "$PWD/docs/03-arquitetura/structurizr":/usr/local/structurizr \
  structurizr/cli:2025.03.28 \
  export -w /usr/local/structurizr/workspace.dsl \
  -format plantuml -o /usr/local/structurizr
```

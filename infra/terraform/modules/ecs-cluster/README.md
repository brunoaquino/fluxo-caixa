# ecs-cluster

Cluster Fargate com FARGATE e FARGATE_SPOT e Container Insights configuravel.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "ecs_cluster" {
  source = "./modules/ecs-cluster"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name` | `string` | Nome do cluster ECS. |
| `container_insights` | `string` | containerInsights do cluster: disabled em dev, enabled em hml, enhanced em prod. |

## Outputs

| Nome | Descricao |
|---|---|
| `cluster_id` | ID do cluster. |
| `cluster_name` | Nome do cluster. |
| `cluster_arn` | ARN do cluster. |

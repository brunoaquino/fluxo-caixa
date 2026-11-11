# network

VPC, subnets publicas/app/dados, NAT e VPC endpoints.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "network" {
  source = "./modules/network"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo dos recursos de rede. Ex.: fluxo-caixa-dev. |
| `region` | `string` | Regiao dos VPC endpoints. |
| `vpc_cidr` | `string` | CIDR da VPC. O desenho usa /24 derivados deste bloco. |
| `az_count` | `number` | Quantidade de AZs. Dev e hml usam 2. Prod usa 3. |
| `nat_gateway_per_az` | `bool` | true cria um NAT por AZ (prod). false cria um unico NAT (dev). |
| `interface_endpoint_az_count` | `number` | AZs com interface endpoint. 0 faz a task sair para as APIs AWS pelo NAT. Hml usa 2 e prod usa 3. |
| `logs_kms_key_arn` | `string` | CMK dos log groups, inclusive o de VPC flow logs. |
| `log_retention_days` | `number` | Retencao dos flow logs. |
| `flow_log_traffic_type` | `string` | Tipo de trafego do flow log. REJECT reduz custo; ALL e mais util em prod. |

## Outputs

| Nome | Descricao |
|---|---|
| `vpc_id` | ID da VPC. |
| `vpc_cidr` | CIDR da VPC. |
| `availability_zones` | AZs usadas, na ordem estavel do slice. |
| `public_subnet_ids` | Subnets publicas (ALB e NAT), uma por AZ. |
| `app_subnet_ids` | Subnets privadas de aplicacao (ECS). |
| `data_subnet_ids` | Subnets privadas de dados, sem rota para a internet. |
| `endpoint_security_group_id` | Security group dos interface endpoints. Nulo quando interface_endpoint_az_count e 0. |

# cognito

User pool, resource server fluxo-caixa e Lambda de pre-token.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "cognito" {
  source = "./modules/cognito"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo do user pool, do client e da Lambda. |
| `deletion_protection` | `string` | ACTIVE em prod, INACTIVE em dev. |
| `log_retention_days` | `number` | Retencao do log da Lambda de pre-token. |
| `logs_kms_key_arn` | `string` | CMK do log group da Lambda. |
| `callback_urls` | `list(string)` | Callbacks do app client. O fluxo principal dos servicos e o password grant, mas o client deixa o authorization code preparado. |
| `logout_urls` | `list(string)` | URLs de logout do app client. |

## Outputs

| Nome | Descricao |
|---|---|
| `user_pool_id` | ID do user pool. |
| `user_pool_arn` | ARN do user pool. |
| `user_pool_client_id` | App client publico do comerciante. |
| `issuer_uri` | JWT_ISSUER_URI. O prefixo de escopo na AWS e fluxo-caixa/. |
| `resource_server_identifier` | Identificador do resource server. |

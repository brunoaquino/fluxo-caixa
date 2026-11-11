# ecr

Repositorios imutaveis, scan on push, criptografia KMS.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "ecr" {
  source = "./modules/ecr"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo dos repositorios. Ex.: fluxo-caixa-dev. |
| `repository_names` | `list(string)` | Nomes logicos dos repositorios, sem o prefixo. |
| `kms_key_arn` | `string` | CMK das imagens. |
| `image_count_to_keep` | `number` | Quantidade de imagens mantidas pela lifecycle policy. |

## Outputs

| Nome | Descricao |
|---|---|
| `repository_urls` | URLs dos repositorios, indexadas pelo nome logico. |
| `repository_arns` | ARNs dos repositorios, indexados pelo nome logico. |

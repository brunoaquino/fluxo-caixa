# kms

CMKs com rotacao anual. Uma chave separa o secret da senha do storage do RDS.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "kms" {
  source = "./modules/security/kms"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `name_prefix` | `string` | Prefixo dos aliases. Ex.: fluxo-caixa-dev. |
| `region` | `string` | Regiao usada nas conditions kms:ViaService e logs. |
| `deletion_window_days` | `number` | Janela de exclusao das CMKs. 7 em dev, 30 em prod. |

## Outputs

| Nome | Descricao |
|---|---|
| `key_arns` | ARNs das CMKs, indexados por finalidade. |
| `key_ids` | IDs das CMKs, indexados por finalidade. |

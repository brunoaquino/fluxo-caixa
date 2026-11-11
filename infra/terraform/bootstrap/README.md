# bootstrap

Bucket de state com KMS, versionamento e lock nativo do S3.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "bootstrap" {
  source = "./bootstrap"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `aws_region` | `string` | Regiao do bucket de state. Um bucket por conta; a key separa dev, hml e prod. |
| `assume_role_arn` | `string` | Role opcional assumida pelo provider. Use quando o bootstrap roda a partir de outra conta. |
| `owner` | `string` | Tag Owner. |
| `cost_center` | `string` | Tag CostCenter. |
| `bucket_name` | `string` | Nome globalmente unico do bucket de state. Sugestao: fluxo-caixa-tfstate-<account_id>. |
| `state_key_deletion_window_days` | `number` | Janela de exclusao da CMK do bucket de state. |
| `noncurrent_version_expiration_days` | `number` | Dias ate expirar versoes antigas do state. A versao corrente permanece. |
| `enable_object_lock` | `bool` | Object Lock GOVERNANCE no bucket. So pode ser ligado na criacao do bucket. Padrao false. |
| `object_lock_retention_days` | `number` | Retencao GOVERNANCE quando enable_object_lock e true. GOVERNANCE ainda permite apagar com permissao explicita, entao um apply quebrado nao prende o state para sempre. |

## Outputs

| Nome | Descricao |
|---|---|
| `state_bucket` | Bucket S3 do state remoto. |
| `state_kms_key_arn` | CMK usada pelo bucket de state. |
| `backend_hcl` | backend.hcl por ambiente. A key isola o state. O bucket pode ser um por conta. |
| `backend_hcl_dev` | Conteudo sugerido para envs/dev/backend.hcl. |
| `backend_hcl_hml` | Conteudo sugerido para envs/hml/backend.hcl. |
| `backend_hcl_prod` | Conteudo sugerido para envs/prod/backend.hcl. |

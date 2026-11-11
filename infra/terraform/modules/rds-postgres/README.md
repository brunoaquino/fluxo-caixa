# rds-postgres

PostgreSQL 17, senha no Secrets Manager, parametros de observabilidade, replica e proxy opcionais.

terraform-docs nao estava instalado. Esta pagina lista as variaveis e os outputs do modulo.

## Uso

```hcl
module "rds_postgres" {
  source = "./modules/rds-postgres"
}
```

## Inputs

| Nome | Tipo | Descricao |
|---|---|---|
| `identifier` | `string` | Identificador da instancia RDS. |
| `db_name` | `string` | Database inicial. Um banco por servico. |
| `username` | `string` | Usuario mestre. A senha fica no Secrets Manager (manage_master_user_password). |
| `engine_version` | `string` | Versao menor do PostgreSQL 17 disponivel na regiao. |
| `instance_class` | `string` | Classe da instancia. Dev usa burstable pequena; prod pode subir sem trocar o modulo. |
| `allocated_storage` | `number` | Storage inicial em GiB. gp3 exige pelo menos 20. |
| `max_allocated_storage` | `number` | Teto do autoscaling de storage. |
| `multi_az` | `bool` | Multi-AZ. true em prod, false em dev. |
| `deletion_protection` | `bool` | Impede o delete da instancia. true em prod. |
| `skip_final_snapshot` | `bool` | Pula o snapshot final. true somente em dev. |
| `backup_retention_days` | `number` | Retencao de backup. PITR fica ativo com valor maior que zero. Meta de RPO: 5 minutos. |
| `apply_immediately` | `bool` | Aplica mudancas na hora. true em dev, false em prod. |
| `kms_key_arn` | `string` | CMK do storage, do Performance Insights e do secret mestre. |
| `subnet_ids` | `list(string)` | Subnets de dados. |
| `vpc_id` | `string` | VPC do security group. |
| `secret_kms_key_arn` | `string` | CMK exclusiva do secret da senha mestre. Distinta da CMK do storage. |
| `manage_master_user_password` | `bool` | RDS cria o secret e rotaciona a senha. Permanece true para a task usar DB_SECRET_ARN. |
| `master_password_rotation_days` | `number` | Intervalo da rotacao do secret mestre. Dev 90, hml e prod 30. O RDS ja rotaciona; este valor ajusta a agenda. |
| `read_replica_count` | `number` | Replicas de leitura. Padrao 0. PostgreSQL com senha gerenciada pelo RDS nao aceita replica (limitacao da AWS). |
| `replica_instance_class` | `string` | Classe da replica. Nulo reutiliza instance_class. |
| `enable_rds_proxy` | `bool` | RDS Proxy na frente da instancia. Padrao false. Nao exige replica. |
| `monitoring_interval` | `number` | Enhanced Monitoring em segundos. 0 desliga. Prod usa 60. |
| `performance_insights_retention_days` | `number` | Retencao do Performance Insights. 7 dias esta no free tier. |
| `log_min_duration_statement_ms` | `number` | log_min_duration_statement em milissegundos. Consultas mais lentas vao para o log do PostgreSQL. |
| `statement_timeout_ms` | `number` | statement_timeout padrao do parameter group, em milissegundos. |

## Outputs

| Nome | Descricao |
|---|---|
| `address` | Hostname da instancia primaria. |
| `endpoint` | Host que a aplicacao usa em DB_URL. O proxy, quando enable_rds_proxy; senao a instancia. |
| `reader_endpoint` | Host de DB_READER_URL. Nulo sem replica. |
| `client_security_group_id` | Security group que a task deve alcancar. O do proxy, se existir; senao o do banco. |
| `port` | Porta PostgreSQL. |
| `db_name` | Nome do database. |
| `master_user_secret_arn` | Secret gerenciado pelo RDS com username e password. |
| `security_group_id` | Security group do banco. O servico abre o ingress 5432. |
| `identifier` | Identificador da instancia, usado nos alarmes. |

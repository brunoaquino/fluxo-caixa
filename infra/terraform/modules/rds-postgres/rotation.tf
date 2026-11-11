# O provider 6.72 nao expoe a agenda de rotacao em aws_db_instance.
# O RDS cria o secret e ja associa a funcao de rotacao gerenciada (padrao de 7 dias).
# Este recurso so ajusta RotationRules. rotation_lambda_arn fica de fora de proposito:
# e Optional+Computed, e informa-lo substituiria a funcao que o RDS instalou.
# rotate_immediately = false evita rotacionar a senha em todo apply.
resource "aws_secretsmanager_secret_rotation" "master" {
  secret_id          = one(aws_db_instance.this.master_user_secret).secret_arn
  rotate_immediately = false

  rotation_rules {
    automatically_after_days = var.master_password_rotation_days
  }
}

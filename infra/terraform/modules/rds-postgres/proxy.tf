data "aws_region" "current" {}

data "aws_iam_policy_document" "proxy_assume" {
  count = var.enable_rds_proxy ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  name               = "${var.identifier}-rds-proxy"
  assume_role_policy = data.aws_iam_policy_document.proxy_assume[0].json
}

data "aws_iam_policy_document" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  statement {
    sid       = "LerSegredoMestre"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [one(aws_db_instance.this.master_user_secret).secret_arn]
  }

  statement {
    sid       = "DecifrarSegredo"
    actions   = ["kms:Decrypt"]
    resources = [var.secret_kms_key_arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  name   = "proxy"
  role   = aws_iam_role.proxy[0].id
  policy = data.aws_iam_policy_document.proxy[0].json
}

resource "aws_security_group" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  name        = "${var.identifier}-proxy"
  description = "RDS Proxy. As tasks entram aqui. O proxy sai para o PostgreSQL."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.identifier}-proxy"
  }
}

resource "aws_vpc_security_group_egress_rule" "proxy_to_db" {
  count = var.enable_rds_proxy ? 1 : 0

  security_group_id            = aws_security_group.proxy[0].id
  referenced_security_group_id = aws_security_group.this.id
  description                  = "Proxy para o PostgreSQL."
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "db_from_proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = aws_security_group.proxy[0].id
  description                  = "Conexoes do RDS Proxy."
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_db_proxy" "this" {
  count = var.enable_rds_proxy ? 1 : 0

  name                   = var.identifier
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.proxy[0].arn
  vpc_subnet_ids         = var.subnet_ids
  vpc_security_group_ids = [aws_security_group.proxy[0].id]
  require_tls            = true
  idle_client_timeout    = 1800
  debug_logging          = false

  auth {
    auth_scheme               = "SECRETS"
    iam_auth                  = "DISABLED"
    secret_arn                = one(aws_db_instance.this.master_user_secret).secret_arn
    client_password_auth_type = "POSTGRES_SCRAM_SHA_256"
  }

  tags = {
    Name = var.identifier
  }
}

resource "aws_db_proxy_default_target_group" "this" {
  count = var.enable_rds_proxy ? 1 : 0

  db_proxy_name = aws_db_proxy.this[0].name

  connection_pool_config {
    max_connections_percent      = 100
    max_idle_connections_percent = 50
    connection_borrow_timeout    = 120
  }
}

resource "aws_db_proxy_target" "this" {
  count = var.enable_rds_proxy ? 1 : 0

  db_proxy_name          = aws_db_proxy.this[0].name
  target_group_name      = aws_db_proxy_default_target_group.this[0].name
  db_instance_identifier = aws_db_instance.this.identifier
}

resource "aws_db_proxy_endpoint" "reader" {
  count = var.enable_rds_proxy && var.read_replica_count > 0 ? 1 : 0

  db_proxy_name          = aws_db_proxy.this[0].name
  db_proxy_endpoint_name = "${var.identifier}-reader"
  vpc_subnet_ids         = var.subnet_ids
  vpc_security_group_ids = [aws_security_group.proxy[0].id]
  target_role            = "READ_ONLY"

  tags = {
    Name = "${var.identifier}-reader"
  }
}

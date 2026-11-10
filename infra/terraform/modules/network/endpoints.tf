locals {
  # xray entra alem da lista do blueprint para o sidecar ADOT enviar traces sem egress 0.0.0.0/0.
  interface_endpoints = {
    ecr_api        = "ecr.api"
    ecr_dkr        = "ecr.dkr"
    logs           = "logs"
    sqs            = "sqs"
    sns            = "sns"
    secretsmanager = "secretsmanager"
    sts            = "sts"
    kms            = "kms"
    xray           = "xray"
  }

  endpoint_subnet_ids = slice([for az in local.azs : aws_subnet.app[az].id], 0, var.interface_endpoint_az_count)
}

resource "aws_security_group" "endpoints" {
  name        = "${var.name_prefix}-vpce"
  description = "HTTPS dos interface endpoints, apenas a partir das subnets de aplicacao."
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-vpce"
  }
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_https" {
  for_each = aws_subnet.app

  security_group_id = aws_security_group.endpoints.id
  description       = "HTTPS das tasks na subnet ${each.key}."
  cidr_ipv4         = each.value.cidr_block
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${var.region}.${each.value}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = local.endpoint_subnet_ids
  security_group_ids  = [aws_security_group.endpoints.id]
  private_dns_enabled = true

  tags = {
    Name = "${var.name_prefix}-vpce-${each.key}"
  }
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [for rt in aws_route_table.app : rt.id]

  tags = {
    Name = "${var.name_prefix}-vpce-s3"
  }
}

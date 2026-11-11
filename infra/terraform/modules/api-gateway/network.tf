resource "aws_security_group" "vpc_link" {
  name        = "${var.name_prefix}-vpc-link"
  description = "ENIs do VPC Link v2. Saida so para o ALB interno na porta 80."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-vpc-link"
  }
}

resource "aws_vpc_security_group_egress_rule" "para_alb" {
  security_group_id            = aws_security_group.vpc_link.id
  description                  = "HTTP para o ALB interno."
  referenced_security_group_id = var.alb_security_group_id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_desde_vpc_link" {
  security_group_id            = var.alb_security_group_id
  description                  = "HTTP do VPC Link v2 do API Gateway."
  referenced_security_group_id = aws_security_group.vpc_link.id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "${var.name_prefix}-rest"
  security_group_ids = [aws_security_group.vpc_link.id]
  subnet_ids         = var.subnet_ids

  tags = {
    Name = "${var.name_prefix}-vpc-link"
  }
}

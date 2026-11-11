data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region
}

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Entrada somente do prefix list do CloudFront. A saida e aberta pelo modulo do servico, so na porta do target."
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

resource "aws_vpc_security_group_ingress_rule" "cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP vindo apenas dos edges CloudFront (VPC Origin)."
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_lb" "this" {
  name                       = "${var.name_prefix}-alb"
  load_balancer_type         = "application"
  internal                   = true
  security_groups            = [aws_security_group.alb.id]
  subnets                    = var.subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = var.deletion_protection
  enable_http2               = true
  idle_timeout               = 60
  ip_address_type            = "ipv4"

  access_logs {
    bucket  = aws_s3_bucket.logs.id
    prefix  = "alb"
    enabled = true
  }

  depends_on = [aws_s3_bucket_policy.logs]

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

resource "aws_lb_target_group" "service" {
  for_each = var.services

  name                          = "${var.name_prefix}-${each.key}"
  port                          = each.value.port
  protocol                      = "HTTP"
  target_type                   = "ip"
  vpc_id                        = var.vpc_id
  deregistration_delay          = 30
  load_balancing_algorithm_type = "least_outstanding_requests"

  health_check {
    path                = each.value.health_path
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.name_prefix}-${each.key}"
  }
}

# TLS termina no CloudFront. O trecho ate o ALB interno atravessa a rede da AWS, sem IP publico.
#trivy:ignore:AVD-AWS-0054
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"title\":\"Nao encontrado\",\"status\":404}"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "service" {
  for_each = var.services

  listener_arn = aws_lb_listener.http.arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service[each.key].arn
  }

  condition {
    path_pattern {
      values = var.stage_path_prefix == "" ? each.value.path_patterns : concat(
        each.value.path_patterns,
        [for pattern in each.value.path_patterns : "/${var.stage_path_prefix}${pattern}"],
      )
    }
  }

  # A integracao privada do REST inclui o stage no path (/v1/api/v1/...).
  # Sem match, o ALB entrega o path original. Com match, so o prefixo do stage sai.
  dynamic "transform" {
    for_each = var.stage_path_prefix == "" ? [] : [var.stage_path_prefix]

    content {
      type = "url-rewrite"

      url_rewrite_config {
        rewrite {
          regex   = "^/${transform.value}(/.*)$"
          replace = "$1"
        }
      }
    }
  }
}

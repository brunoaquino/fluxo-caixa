data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

locals {
  api_origin_id = var.enable_api_gateway ? "api-gateway" : "alb"
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name = "${var.name_prefix}-security"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }
}

resource "aws_cloudfront_vpc_origin" "alb" {
  vpc_origin_endpoint_config {
    name                   = "${var.name_prefix}-alb"
    arn                    = var.alb_arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only"

    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }

  tags = {
    Name = "${var.name_prefix}-vpc-origin"
  }
}

# Caminho que nao e API devolve 404 na borda. O viewer-request encerra o pedido
# antes do ALB, entao /actuator e /swagger-ui nao atravessam a VPC Origin.
resource "aws_cloudfront_function" "nao_encontrado" {
  name    = "${var.name_prefix}-nao-encontrado"
  runtime = "cloudfront-js-2.0"
  comment = "404 para qualquer caminho fora de /api/v1/lancamentos e /api/v1/consolidados"
  publish = true
  code    = <<-EOF
    function handler(event) {
      return {
        statusCode: 404,
        statusDescription: "Not Found",
        headers: {
          "content-type": { value: "application/json" },
          "cache-control": { value: "no-store" }
        },
        body: "{\"title\":\"Nao encontrado\",\"status\":404}"
      };
    }
  EOF
}

resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = false
  http_version    = "http2and3"
  price_class     = "PriceClass_All"
  comment         = "${var.name_prefix} api"
  aliases         = var.domain_names
  web_acl_id      = aws_wafv2_web_acl.this.arn

  origin {
    domain_name = var.alb_dns_name
    origin_id   = "alb"

    vpc_origin_config {
      vpc_origin_id       = aws_cloudfront_vpc_origin.alb.id
      origin_read_timeout = 60
    }
  }

  dynamic "origin" {
    for_each = var.enable_api_gateway ? [1] : []

    content {
      domain_name = var.api_gateway_domain_name
      origin_id   = "api-gateway"
      origin_path = var.api_gateway_origin_path

      custom_header {
        name  = "X-Origin-Verify"
        value = var.origin_verify_header_value
      }

      custom_origin_config {
        http_port                = 80
        https_port               = 443
        origin_protocol_policy   = "https-only"
        origin_ssl_protocols     = ["TLSv1.2"]
        origin_read_timeout      = 60
        origin_keepalive_timeout = 5
      }
    }
  }

  default_cache_behavior {
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_disabled.id
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.nao_encontrado.arn
    }
  }

  ordered_cache_behavior {
    path_pattern               = "/api/v1/lancamentos*"
    target_origin_id           = local.api_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true
  }

  ordered_cache_behavior {
    path_pattern               = "/api/v1/consolidados*"
    target_origin_id           = local.api_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.acm_certificate_arn == null
    acm_certificate_arn            = var.acm_certificate_arn
    ssl_support_method             = var.acm_certificate_arn == null ? null : "sni-only"
    minimum_protocol_version       = "TLSv1.2_2021"
  }

  tags = {
    Name = "${var.name_prefix}-cdn"
  }
}

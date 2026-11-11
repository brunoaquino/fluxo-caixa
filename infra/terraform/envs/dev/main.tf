module "platform" {
  source = "../../stacks/fluxo-caixa"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  environment         = var.environment
  aws_region          = var.aws_region
  alert_email         = var.alert_email
  image_tag           = var.image_tag
  acm_certificate_arn = var.acm_certificate_arn
  domain_names        = var.domain_names
  sizing              = var.sizing
  enable_api_gateway  = var.enable_api_gateway
  api_cache_enabled   = var.api_cache_enabled
  api_key_required    = var.api_key_required
  api_consumers       = var.api_consumers
  api_canary          = var.api_canary
}

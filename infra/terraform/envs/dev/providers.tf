provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "fluxo-caixa"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

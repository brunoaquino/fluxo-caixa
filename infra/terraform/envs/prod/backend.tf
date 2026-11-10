terraform {
  backend "s3" {
    # Parametros reais em backend.hcl. Veja backend.hcl.example.
    # terraform init -backend-config=backend.hcl
    # bucket       = "fluxo-caixa-tfstate-ACCOUNT"
    # key          = "fluxo-caixa/<env>/terraform.tfstate"
    # region       = "sa-east-1"
    # encrypt      = true
    # use_lockfile = true # locking nativo do S3, Terraform >= 1.10
  }
}

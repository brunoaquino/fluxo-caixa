terraform {
  backend "s3" {
    # bucket, key, region, encrypt, kms_key_id e use_lockfile ficam no backend.hcl.
    # Nao ha DynamoDB: Terraform >= 1.10 trava o state com um objeto .tflock no proprio bucket.
    # terraform init -backend-config=backend.hcl
  }
}

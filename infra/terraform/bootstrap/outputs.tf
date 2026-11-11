output "state_bucket" {
  description = "Bucket S3 do state remoto."
  value       = aws_s3_bucket.state.id
}

output "state_kms_key_arn" {
  description = "CMK usada pelo bucket de state."
  value       = aws_kms_key.state.arn
}

output "backend_hcl" {
  description = "backend.hcl por ambiente. A key isola o state. O bucket pode ser um por conta."
  value = {
    for env in ["dev", "hml", "prod"] :
    env => <<-EOT
      bucket       = "${aws_s3_bucket.state.id}"
      key          = "fluxo-caixa/${env}/terraform.tfstate"
      region       = "${var.aws_region}"
      encrypt      = true
      kms_key_id   = "${aws_kms_key.state.arn}"
      use_lockfile = true
    EOT
  }
}

output "backend_hcl_dev" {
  description = "Conteudo sugerido para envs/dev/backend.hcl."
  value       = <<-EOT
    bucket       = "${aws_s3_bucket.state.id}"
    key          = "fluxo-caixa/dev/terraform.tfstate"
    region       = "${var.aws_region}"
    encrypt      = true
    use_lockfile = true
    kms_key_id   = "${aws_kms_key.state.arn}"
  EOT
}

output "backend_hcl_hml" {
  description = "Conteudo sugerido para envs/hml/backend.hcl."
  value       = <<-EOT
    bucket       = "${aws_s3_bucket.state.id}"
    key          = "fluxo-caixa/hml/terraform.tfstate"
    region       = "${var.aws_region}"
    encrypt      = true
    kms_key_id   = "${aws_kms_key.state.arn}"
    use_lockfile = true
  EOT
}

output "backend_hcl_prod" {
  description = "Conteudo sugerido para envs/prod/backend.hcl."
  value       = <<-EOT
    bucket       = "${aws_s3_bucket.state.id}"
    key          = "fluxo-caixa/prod/terraform.tfstate"
    region       = "${var.aws_region}"
    encrypt      = true
    use_lockfile = true
    kms_key_id   = "${aws_kms_key.state.arn}"
  EOT
}

output "key_arns" {
  description = "ARNs das CMKs, indexados por finalidade."
  value       = { for name, key in aws_kms_key.this : name => key.arn }
}

output "key_ids" {
  description = "IDs das CMKs, indexados por finalidade."
  value       = { for name, key in aws_kms_key.this : name => key.key_id }
}

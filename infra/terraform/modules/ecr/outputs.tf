output "repository_urls" {
  description = "URLs dos repositorios, indexadas pelo nome logico."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "repository_arns" {
  description = "ARNs dos repositorios, indexados pelo nome logico."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.arn }
}

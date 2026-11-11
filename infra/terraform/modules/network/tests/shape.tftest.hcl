mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b", "us-east-1c"]
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

run "dev_um_nat_sem_endpoint" {
  command = plan

  variables {
    name_prefix                 = "fluxo-caixa-dev"
    region                      = "us-east-1"
    vpc_cidr                    = "10.20.0.0/16"
    az_count                    = 2
    nat_gateway_per_az          = false
    interface_endpoint_az_count = 0
    logs_kms_key_arn            = "arn:aws:kms:us-east-1:123456789012:key/logs"
    log_retention_days          = 7
    flow_log_traffic_type       = "REJECT"
  }

  assert {
    condition     = length(aws_subnet.public) == 2 && length(aws_nat_gateway.this) == 1
    error_message = "Dev usa 2 AZs e um unico NAT."
  }

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 0
    error_message = "Dev nao cria interface endpoint."
  }

  assert {
    condition     = aws_vpc_endpoint.s3.service_name == "com.amazonaws.us-east-1.s3"
    error_message = "O gateway de S3 continua em dev, porque nao tem custo horario."
  }
}

run "prod_nat_por_az" {
  command = plan

  variables {
    name_prefix                 = "fluxo-caixa-prod"
    region                      = "us-east-1"
    vpc_cidr                    = "10.30.0.0/16"
    az_count                    = 3
    nat_gateway_per_az          = true
    interface_endpoint_az_count = 3
    logs_kms_key_arn            = "arn:aws:kms:us-east-1:123456789012:key/logs"
    log_retention_days          = 30
    flow_log_traffic_type       = "ALL"
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 3 && length(aws_subnet.app) == 3
    error_message = "Prod cria um NAT em cada uma das 3 AZs."
  }

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 9
    error_message = "Prod cria os 9 interface endpoints nas 3 AZs."
  }
}

mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
      arn        = "arn:aws:iam::123456789012:root"
    }
  }
}

variables {
  name                   = "fluxo-caixa-dev-lancamentos"
  container_name         = "lancamentos"
  cluster_arn            = "arn:aws:ecs:us-east-1:123456789012:cluster/fluxo-caixa-dev-cluster"
  cluster_name           = "fluxo-caixa-dev-cluster"
  vpc_id                 = "vpc-dev"
  vpc_cidr               = "10.20.0.0/16"
  subnet_ids             = ["subnet-a", "subnet-b"]
  container_image        = "123456789012.dkr.ecr.us-east-1.amazonaws.com/fluxo-caixa-dev/lancamentos:bootstrap"
  container_port         = 8081
  cpu                    = 512
  memory                 = 1024
  desired_count          = 1
  min_capacity           = 1
  max_capacity           = 2
  log_group_name         = "/ecs/fluxo-caixa-dev/lancamentos"
  log_group_arn          = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/fluxo-caixa-dev/lancamentos"
  metrics_log_group_name = "/ecs/fluxo-caixa-dev/metrics"
  metrics_log_group_arn  = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/fluxo-caixa-dev/metrics"
  aws_region             = "us-east-1"
  target_group_arn       = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/lancamentos/abc"
  alb_security_group_id  = "sg-alb"
  environment = {
    SERVER_PORT = "8081"
  }
}

run "dev_fargate_spot_e_segredo_na_task_role" {
  command = plan

  variables {
    vpc_endpoint_security_group_id = null
    capacity_provider_strategy = [
      {
        capacity_provider = "FARGATE_SPOT"
        weight            = 1
        base              = 0
      }
    ]
    task_secret_arns         = ["arn:aws:secretsmanager:us-east-1:123456789012:secret:rds!db-lancamentos-AbCdEf"]
    task_secret_kms_key_arns = ["arn:aws:kms:us-east-1:123456789012:key/rds-secret"]
  }

  assert {
    condition = alltrue([
      for strategy in aws_ecs_service.this.capacity_provider_strategy :
      strategy.capacity_provider == "FARGATE_SPOT"
    ])
    error_message = "Dev deve agendar a task em FARGATE_SPOT."
  }

  assert {
    condition     = length(aws_vpc_security_group_egress_rule.nat_https) == 1 && length(aws_vpc_security_group_egress_rule.endpoints) == 0
    error_message = "Sem interface endpoint, a task sai por HTTPS no NAT e nao aponta para um security group de endpoint."
  }
}

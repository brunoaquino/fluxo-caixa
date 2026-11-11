# State antigo tinha os modulos na raiz do ambiente. O bloco moved evita recriar
# a infra se este diretorio ja foi aplicado antes da composicao.

moved {
  from = module.kms
  to   = module.platform.module.kms
}

moved {
  from = module.network
  to   = module.platform.module.network
}

moved {
  from = module.ecr
  to   = module.platform.module.ecr
}

moved {
  from = module.cluster
  to   = module.platform.module.cluster
}

moved {
  from = module.messaging
  to   = module.platform.module.messaging
}

moved {
  from = module.rds_lancamentos
  to   = module.platform.module.rds_lancamentos
}

moved {
  from = module.rds_consolidado
  to   = module.platform.module.rds_consolidado
}

moved {
  from = module.redis
  to   = module.platform.module.redis
}

moved {
  from = module.cognito
  to   = module.platform.module.cognito
}

moved {
  from = module.alb
  to   = module.platform.module.alb
}

moved {
  from = module.observability
  to   = module.platform.module.observability
}

moved {
  from = module.ecs_lancamentos
  to   = module.platform.module.ecs_lancamentos
}

moved {
  from = module.ecs_consolidado
  to   = module.platform.module.ecs_consolidado
}

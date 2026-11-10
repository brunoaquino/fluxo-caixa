.DEFAULT_GOAL := help

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
COMPOSE := docker compose -f $(ROOT)/infra/local/docker-compose.yml --project-directory $(ROOT)/infra/local
TF_DEV := $(ROOT)/infra/terraform/envs/dev
TF_PROD := $(ROOT)/infra/terraform/envs/prod

.PHONY: help infra-up infra-down up down logs test token smoke resiliencia load tf-fmt tf-validate

help:
	@printf '%s\n' \
		'infra-up       Sobe postgres, redis, localstack, keycloak e otel (perfil infra)' \
		'infra-down     Derruba o perfil infra' \
		'up             Sobe a stack completa e faz build dos servicos' \
		'down           Derruba os perfis infra e completo' \
		'logs           Acompanha os logs da stack' \
		'test           mvnw verify nos dois servicos' \
		'token          Imprime um access token do Keycloak' \
		'smoke          Credito, debito, estorno e saldo' \
		'resiliencia    Escrita com o consolidado parado e convergencia depois' \
		'load           Pico de 50 req/s no consolidado com k6' \
		'tf-fmt         terraform fmt -recursive' \
		'tf-validate    init -backend=false e validate em dev e prod'

infra-up:
	$(COMPOSE) --profile infra up -d

infra-down:
	$(COMPOSE) --profile infra down

up:
	$(COMPOSE) --profile completo up -d --build

down:
	$(COMPOSE) --profile infra --profile completo down --remove-orphans

logs:
	$(COMPOSE) --profile infra --profile completo logs -f

test:
	cd $(ROOT)/services/lancamentos-service && ./mvnw -B verify
	cd $(ROOT)/services/consolidado-service && ./mvnw -B verify

token:
	@$(ROOT)/infra/local/get-token.sh

smoke:
	$(ROOT)/tests/e2e/smoke.sh

resiliencia:
	$(ROOT)/tests/e2e/resiliencia.sh

load:
	k6 run $(ROOT)/tests/load/consolidado-pico.js

tf-fmt:
	terraform fmt -recursive $(ROOT)/infra/terraform

tf-validate:
	terraform fmt -recursive -check $(ROOT)/infra/terraform
	cd $(TF_DEV) && terraform init -backend=false && terraform validate
	cd $(TF_PROD) && terraform init -backend=false && terraform validate

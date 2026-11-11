.DEFAULT_GOAL := help

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
COMPOSE := docker compose -f $(ROOT)/infra/local/docker-compose.yml --project-directory $(ROOT)/infra/local
ENV ?= dev
TF_ENV := $(ROOT)/infra/terraform/envs/$(ENV)
PDF_VENV := $(ROOT)/.venv-pdf

# Make 3.81 nao exporta sozinho a sobrescrita da linha de comando.
# Sem isto, `make tf-plan AWS_PROFILE=aquino-dev` pode ficar com o perfil do ambiente.
ifneq ($(AWS_PROFILE),)
export AWS_PROFILE
endif

.PHONY: help infra-up infra-down up down logs test token smoke resiliencia load tf-fmt tf-validate tf-init tf-plan tf-test pdf

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
		'tf-init        terraform init do ENV (ENV=dev|hml|prod). Respeita AWS_PROFILE do comando ou do ambiente' \
		'tf-plan        terraform plan do ENV. Respeita AWS_PROFILE (make tf-plan ENV=dev AWS_PROFILE=aquino-dev)' \
		'tf-validate    fmt -check e validate offline, sem credencial e sem ler o state' \
		'tf-test        terraform test dos modulos criticos' \
		'pdf            Regera os PDFs de documentos-entregaveis-desafio (pandoc + Chrome)'

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

tf-init:
	cd $(TF_ENV) && terraform init -backend-config=backend.hcl

tf-plan:
	cd $(TF_ENV) && terraform plan -out=$(ENV).tfplan

tf-validate:
	terraform fmt -recursive -check $(ROOT)/infra/terraform
	@set -eu; \
	cache=$$(mktemp -d); \
	trap 'rm -rf "$$cache"' EXIT; \
	for dir in bootstrap envs/dev envs/hml envs/prod; do \
		data=$$(mktemp -d "$$cache/tf.XXXXXX"); \
		echo "terraform validate $$dir"; \
		TF_PLUGIN_CACHE_DIR="$$cache" TF_DATA_DIR="$$data" terraform -chdir="$(ROOT)/infra/terraform/$$dir" init -backend=false -input=false -lockfile=readonly; \
		TF_PLUGIN_CACHE_DIR="$$cache" TF_DATA_DIR="$$data" terraform -chdir="$(ROOT)/infra/terraform/$$dir" validate; \
		rm -rf "$$data"; \
	done

tf-test:
	terraform -chdir=$(ROOT)/infra/terraform/modules/messaging test
	terraform -chdir=$(ROOT)/infra/terraform/modules/network test
	terraform -chdir=$(ROOT)/infra/terraform/modules/alb test
	terraform -chdir=$(ROOT)/infra/terraform/modules/ecs-service test
	terraform -chdir=$(ROOT)/infra/terraform/modules/rds-postgres test
	terraform -chdir=$(ROOT)/infra/terraform/modules/edge test
	terraform -chdir=$(ROOT)/infra/terraform/modules/api-gateway test
	terraform -chdir=$(ROOT)/infra/terraform/modules/cognito test

$(PDF_VENV)/bin/python:
	python3 -m venv $(PDF_VENV)
	$(PDF_VENV)/bin/pip install -q -r $(ROOT)/tools/pdf/requirements.txt

pdf: $(PDF_VENV)/bin/python
	$(PDF_VENV)/bin/python $(ROOT)/tools/pdf/build.py

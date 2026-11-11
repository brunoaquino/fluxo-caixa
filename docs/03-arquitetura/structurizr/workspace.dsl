workspace "Fluxo de Caixa" "Controle de lancamentos e saldo diario consolidado" {

    model {
        comerciante = person "Comerciante" "Registra creditos e debitos e consulta o saldo do dia." "Operador"

        legado = softwareSystem "Caixa legado" "Planilha ou ERP on-premises. Participa apenas da transicao." "Legado" {
            tags "External"
        }

        identidade = softwareSystem "Identidade e Acesso" "Cognito na AWS e Keycloak no ambiente local. Emite JWT com comerciante_id e escopos." "OAuth2/OIDC" {
            tags "External"
        }

        fluxo = softwareSystem "Fluxo de Caixa" "Fonte da verdade dos lancamentos e projecao do saldo diario." {
            lancamentos = container "lancamentos-service" "Registra creditos, debitos e estornos. Porta 8081." "Java 25, Spring Boot 4" {
                apiLanc = component "LancamentoController" "POST e GET /api/v1/lancamentos. RFC 9457. valor string na resposta." "adapters.in.web"
                segLanc = component "SegurancaConfig" "Valida JWT e escopos lancamentos:write e lancamentos:read." "config"
                registrar = component "RegistrarLancamentoServico" "Idempotencia por impressao_digital. 409 se a chave repetir com outro corpo." "application.servico"
                estornar = component "EstornarLancamentoServico" "Estorno unico. Idempotency-Key tambem no POST de estorno." "application.servico"
                consultarLanc = component "ConsultarLancamentoServico e ListarLancamentosServico" "Detalhe e pagina com conteudo, pagina, tamanho, totalElementos e totalPaginas." "application.servico"
                dominioLanc = component "Lancamento e Dinheiro" "Agregado imutavel e valor monetario. Sem framework." "domain.lancamento e domain.dinheiro"
                repoLanc = component "Repositorios JPA" "Persiste lancamentos, idempotencia e outbox_eventos na mesma transacao." "adapters.out.persistence"
                relay = component "RelayOutbox" "Lote de 100 a cada 500 ms, SKIP LOCKED e proxima_tentativa_em." "adapters.out.messaging"
                publicador = component "SnsPublicador" "Publica LancamentoRegistrado v1 em LANCAMENTOS_TOPIC_ARN." "adapters.out.messaging"

                apiLanc -> segLanc "Exige token valido"
                apiLanc -> registrar "Dispara o registro"
                apiLanc -> estornar "Dispara o estorno"
                apiLanc -> consultarLanc "Dispara a consulta"
                registrar -> dominioLanc "Constroi o agregado"
                estornar -> dominioLanc "Produz o lancamento oposto"
                registrar -> repoLanc "Grava fato, outbox e chave"
                estornar -> repoLanc "Grava o estorno e a outbox"
                consultarLanc -> repoLanc "Le pelo comerciante do token"
                relay -> repoLanc "Reserva eventos nao publicados"
                relay -> publicador "Pede a publicacao apos o commit"
            }

            dbLancamentos = container "Postgres lancamentos" "Tabelas lancamentos, idempotencia e outbox_eventos." "PostgreSQL 17" {
                tags "Database"
            }

            consolidado = container "consolidado-service" "Projecao do saldo diario e API de leitura. Porta 8082." "Java 25, Spring Boot 4" {
                apiCons = component "ConsultaConsolidadoController" "GET do dia e relatorio com inicio, fim e dias." "adapters.in.web"
                segCons = component "ConfiguracaoSeguranca" "Valida escopo consolidado:read." "config"
                consultarSaldo = component "ConsultarSaldoDiarioService e ConsultarRelatorioService" "Cache-aside, saldo do dia e saldo acumulado." "application.servico"
                aplicar = component "AplicarLancamentoService" "UPSERT atomico e idempotencia por eventId." "application.servico"
                dominioCons = component "SaldoDiario" "saldo = creditos - debitos." "domain.model"
                acl = component "TradutorEventoLancamento" "Traduz o JSON v1. Versao desconhecida falha." "adapters.in.messaging"
                listener = component "ConsumidorSqsLancamentos" "Long polling de 20 s em CONSOLIDADO_QUEUE_URL." "adapters.in.messaging"
                repoCons = component "RepositorioConsolidadoJdbc" "saldos_diarios e eventos_processados na mesma transacao." "adapters.out.persistence"
                cacheAdapter = component "CacheSaldoRedis" "Versao por comerciante. TTL de 30 s. REDIS_SSL." "adapters.out.cache"

                apiCons -> segCons "Exige token valido"
                apiCons -> consultarSaldo "Dispara a leitura"
                consultarSaldo -> cacheAdapter "Le e preenche"
                consultarSaldo -> repoCons "Miss e SUM do acumulado"
                listener -> acl "Entrega o corpo raw"
                acl -> aplicar "Comando interno"
                aplicar -> dominioCons "Aplica credito ou debito"
                aplicar -> repoCons "Atualiza saldo e marca eventId"
                aplicar -> cacheAdapter "Incrementa a geracao"
            }

            dbConsolidado = container "Postgres consolidado" "Tabelas saldos_diarios e eventos_processados." "PostgreSQL 17" {
                tags "Database"
            }

            cache = container "Cache do consolidado" "Descartavel. Perda degrada latencia e nao perde saldo." "Redis 7 / Valkey" {
                tags "Database"
            }

            topico = container "Topico de lancamentos" "Published Language. Atributo eventType = LancamentoRegistrado." "SNS fluxo-caixa-lancamentos-events" {
                tags "Queue"
            }

            fila = container "Fila do consolidado" "Retencao de 14 dias, visibility de 60 s, raw message delivery." "SQS consolidado-lancamentos-queue" {
                tags "Queue"
            }

            dlq = container "DLQ do consolidado" "Destino apos maxReceiveCount = 5." "SQS consolidado-lancamentos-dlq" {
                tags "Queue"
            }

            apiGateway = container "API Gateway" "REST regional. Authorizer Cognito, usage plan e validacao do corpo. Nao existe no laboratorio." "Amazon API Gateway" {
                tags "LoadBalancer"
            }

            lancamentos -> dbLancamentos "Le e grava" "JDBC"
            consolidado -> dbConsolidado "Le e grava a projecao" "JDBC"
            consolidado -> cache "Cache-aside" "protocolo Redis"
            lancamentos -> topico "Publica via relay" "AWS SDK v2"
            topico -> fila "Entrega" "assinatura SNS"
            fila -> dlq "Redrive apos 5 falhas" "SQS"
            fila -> consolidado "Consome" "long polling"
        }

        comerciante -> apiGateway "Chama /api/v1 na AWS" "HTTPS via CloudFront"
        apiGateway -> lancamentos "VPC Link v2" "HTTP_PROXY"
        apiGateway -> consolidado "VPC Link v2" "HTTP_PROXY"
        apiGateway -> identidade "Authorizer Cognito" "escopo por rota"
        comerciante -> lancamentos "Registra e consulta lancamentos" "HTTPS JSON /api/v1"
        comerciante -> consolidado "Consulta saldo diario e periodo" "HTTPS JSON /api/v1"
        lancamentos -> identidade "Valida o access token" "JWKS"
        consolidado -> identidade "Valida o access token" "JWKS"
        legado -> lancamentos "Cede movimentos na migracao" "carga inicial ou CDC"

        prod = deploymentEnvironment "Production" {
            deploymentNode "AWS" "Conta de producao" "Amazon Web Services" {
                tags "AWS"
                deploymentNode "us-east-1" "Regiao da Virginia. WAF e ACM do CloudFront moram aqui." "AWS Region" {
                    tags "Region"

                    deploymentNode "Borda" "Distribuicao global. PoP GRU para o comerciante." "CloudFront" {
                        infrastructureNode "CloudFront" "TLS no edge. Cache desligado em /api. Origem HTTPS do API Gateway." "Amazon CloudFront" {
                            tags "LoadBalancer"
                        }
                        infrastructureNode "WAF" "Escopo CLOUDFRONT. Managed rules e rate-based" "AWS WAF" {
                            tags "Security"
                        }
                        infrastructureNode "VPC Origin" "Caminho reserva quando enable_api_gateway e false" "CloudFront VPC Origin"
                    }

                    deploymentNode "API Gateway" "REST regional, stage v1. Nao e edge-optimized." "Amazon API Gateway" {
                        containerInstance apiGateway
                        infrastructureNode "WAF regional" "Bloqueia request sem X-Origin-Verify" "AWS WAF" {
                            tags "Security"
                        }
                        infrastructureNode "VPC Link v2" "Integracao privada direto no ALB. Sem NLB." "VPC Link"
                    }

                    deploymentNode "Cognito" "User Pool Essentials e Pre Token Generation v2" "Amazon Cognito" {
                        softwareSystemInstance identidade
                    }

                    deploymentNode "VPC" "Tres zonas de disponibilidade em prod" "Amazon VPC" {
                        deploymentNode "Subnets publicas" "So NAT. O ALB nao fica aqui." "Subnets" {
                            deploymentNode "NAT" "Um gateway por AZ em prod" "NAT Gateway" {
                                infrastructureNode "NAT Gateway" "Egress das tasks" "NAT Gateway"
                            }
                        }

                        deploymentNode "Subnets de aplicacao" "Tasks sem IP publico" "Subnets" {
                            deploymentNode "ALB interno" "Sem IP publico. Aceita CloudFront e o security group do VPC Link. Reescreve o prefixo /v1." "Application Load Balancer" {
                                infrastructureNode "ALB" "Roteia /api/v1/lancamentos e /api/v1/consolidados" "Application Load Balancer" {
                                    tags "LoadBalancer"
                                }
                            }
                            deploymentNode "ECS Fargate" "Cluster unico, circuit breaker de deploy" "Amazon ECS" {
                                deploymentNode "lancamentos" "Minimo de 2 tasks em AZs distintas" "Fargate ARM" {
                                    containerInstance lancamentos
                                }
                                deploymentNode "consolidado" "Minimo de 2 tasks em AZs distintas" "Fargate ARM" {
                                    containerInstance consolidado
                                }
                            }
                            deploymentNode "VPC endpoints" "ECR, Logs, SQS, SNS, Secrets Manager, STS, KMS e X-Ray" "Interface endpoints" {
                                infrastructureNode "Endpoints" "Mantem o trafego AWS fora do NAT" "PrivateLink"
                            }
                        }

                        deploymentNode "Subnets de dados" "Sem rota para internet" "Subnets" {
                            deploymentNode "RDS lancamentos" "PostgreSQL 17 Multi-AZ, KMS, PITR" "Amazon RDS" {
                                containerInstance dbLancamentos
                            }
                            deploymentNode "RDS consolidado" "Instancia separada, Multi-AZ" "Amazon RDS" {
                                containerInstance dbConsolidado
                            }
                            deploymentNode "ElastiCache" "Valkey com replica e failover" "Amazon ElastiCache" {
                                containerInstance cache
                            }
                        }
                    }

                    deploymentNode "Mensageria" "Criptografada com KMS" "SNS + SQS" {
                        containerInstance topico
                        containerInstance fila
                        containerInstance dlq
                    }

                    deploymentNode "Segredos" "Rotacao automatica" "AWS Secrets Manager" {
                        infrastructureNode "Segredos RDS" "30 dias em prod e hml, 90 em dev. Wrapper rebusca." "Secrets Manager"
                        infrastructureNode "X-Origin-Verify" "current e previous. Lambda atualiza CloudFront e o WAF regional." "Secrets Manager"
                    }

                    deploymentNode "Observabilidade" "Logs, metricas, traces e alarmes" "CloudWatch + X-Ray" {
                        infrastructureNode "ADOT" "Sidecar em cada task. Propaga traceparent." "AWS Distro for OpenTelemetry"
                        infrastructureNode "CloudWatch" "Logs JSON, metricas e alarmes" "Amazon CloudWatch"
                        infrastructureNode "X-Ray" "Trace unico do POST ao consumidor" "AWS X-Ray"
                    }
                }
            }
        }
    }

    views {
        systemContext fluxo "Contexto" "Pessoas e sistemas na fronteira do Fluxo de Caixa." {
            include *
            autolayout lr
        }

        container fluxo "Containers" "Servicos, bancos, cache e mensageria." {
            include *
            autolayout lr
        }

        component lancamentos "ComponentesLancamentos" "Hexagono do servico de lancamentos." {
            include *
            autolayout lr
        }

        component consolidado "ComponentesConsolidado" "Hexagono do servico de consolidado." {
            include *
            autolayout lr
        }

        dynamic fluxo "RegistrarLancamento" "POST ate o commit local. A publicacao nao esta neste fluxo." {
            comerciante -> lancamentos "POST /api/v1/lancamentos com JWT"
            lancamentos -> identidade "Valida assinatura e escopo"
            lancamentos -> dbLancamentos "COMMIT lancamento, outbox e idempotencia"
            autolayout lr
        }

        dynamic fluxo "OutboxRelay" "Publicacao depois do commit." {
            lancamentos -> dbLancamentos "Reserva lote com SKIP LOCKED"
            lancamentos -> topico "Publish LancamentoRegistrado v1"
            topico -> fila "Entrega raw"
            autolayout lr
        }

        dynamic fluxo "Consolidar" "Aplicacao idempotente do evento." {
            fila -> consolidado "ReceiveMessage"
            consolidado -> dbConsolidado "Soma e insere eventId"
            consolidado -> cache "Incrementa a geracao"
            autolayout lr
        }

        dynamic fluxo "ConsultarSaldo" "Cache-aside no GET diario." {
            comerciante -> consolidado "GET /api/v1/consolidados/diario/{data}"
            consolidado -> identidade "Valida JWT"
            consolidado -> cache "GET pela geracao"
            consolidado -> dbConsolidado "Miss: linha do dia e SUM acumulado"
            autolayout lr
        }

        dynamic fluxo "ConsolidadoIndisponivel" "O POST nao espera a fila nem o consumidor." {
            comerciante -> lancamentos "POST /api/v1/lancamentos"
            lancamentos -> dbLancamentos "COMMIT local e 201"
            lancamentos -> topico "Relay publica quando o SNS responde"
            topico -> fila "Mensagem espera ate 14 dias"
            autolayout lr
        }

        dynamic fluxo "TokenInvalido" "O authorizer recusa antes da VPC." {
            comerciante -> apiGateway "POST com Bearer invalido"
            apiGateway -> identidade "Cognito authorizer rejeita"
            autolayout lr
        }

        deployment fluxo prod "AWS" "Producao em us-east-1, com CloudFront, API Gateway e ALB interno." {
            include *
            autolayout tb
        }

        styles {
            element "Person" {
                shape Person
                background "#1f4b99"
                color "#ffffff"
            }
            element "Operador" {
                shape Person
                background "#1f4b99"
                color "#ffffff"
            }
            element "Software System" {
                background "#2f6f4e"
                color "#ffffff"
            }
            element "External" {
                background "#6b7280"
                color "#ffffff"
            }
            element "Legado" {
                background "#7c5e10"
                color "#ffffff"
            }
            element "Container" {
                background "#0f6e6e"
                color "#ffffff"
            }
            element "Database" {
                shape Cylinder
                background "#3d4f7c"
                color "#ffffff"
            }
            element "Queue" {
                shape Pipe
                background "#8a4b08"
                color "#ffffff"
            }
            element "Component" {
                background "#155e75"
                color "#ffffff"
            }
            element "LoadBalancer" {
                shape RoundedBox
                background "#7f1d1d"
                color "#ffffff"
            }
            element "Security" {
                background "#7f1d1d"
                color "#ffffff"
            }
            element "Infrastructure Node" {
                background "#374151"
                color "#ffffff"
            }
        }
    }
}

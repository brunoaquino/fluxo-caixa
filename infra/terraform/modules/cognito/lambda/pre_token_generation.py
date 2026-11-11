"""Pre Token Generation v2.

Copia o atributo custom:comerciante_id para o claim comerciante_id do access token
e acrescenta os escopos de TOKEN_SCOPES. O InitiateAuth nao traz escopo de resource
server; sem isso o API Gateway recusa o token. O servico nunca aceita o comerciante
vindo do corpo ou da URL.
"""

import os


def _escopos():
    bruto = os.environ.get("TOKEN_SCOPES", "")
    return [item for item in bruto.split() if item]


def handler(event, context):
    atributos = event.get("request", {}).get("userAttributes", {})
    # Cognito expoe o atributo como custom: ou, se for developer-only, as vezes com o prefixo dev:.
    comerciante_id = atributos.get("custom:comerciante_id") or atributos.get("dev:custom:comerciante_id") or ""

    access_token = {
        "claimsToAddOrOverride": {
            "comerciante_id": comerciante_id,
        }
    }
    escopos = _escopos()
    if escopos:
        access_token["scopesToAdd"] = escopos

    resposta = event.setdefault("response", {})
    resposta["claimsAndScopeOverrideDetails"] = {
        "accessTokenGeneration": access_token,
        "idTokenGeneration": {
            "claimsToAddOrOverride": {
                "comerciante_id": comerciante_id,
            }
        },
    }
    return event

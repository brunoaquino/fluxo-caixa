"""Pre Token Generation v2.

Copia o atributo custom:comerciante_id para o claim comerciante_id do access token.
O servico nunca aceita o comerciante vindo do corpo ou da URL.
"""


def handler(event, context):
    atributos = event.get("request", {}).get("userAttributes", {})
    # Cognito expoe o atributo como custom: ou, se for developer-only, as vezes com o prefixo dev:.
    comerciante_id = atributos.get("custom:comerciante_id") or atributos.get("dev:custom:comerciante_id") or ""

    resposta = event.setdefault("response", {})
    resposta["claimsAndScopeOverrideDetails"] = {
        "accessTokenGeneration": {
            "claimsToAddOrOverride": {
                "comerciante_id": comerciante_id,
            }
        },
        "idTokenGeneration": {
            "claimsToAddOrOverride": {
                "comerciante_id": comerciante_id,
            }
        },
    }
    return event

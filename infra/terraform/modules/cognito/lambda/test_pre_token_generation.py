"""Testes da Lambda de pre-token. Rode com python3 -m unittest."""

import importlib.util
import os
import unittest
from pathlib import Path

COMERCIANTE = "11111111-1111-1111-1111-111111111111"
ESCOPOS = [
    "fluxo-caixa/lancamentos:write",
    "fluxo-caixa/lancamentos:read",
    "fluxo-caixa/consolidado:read",
]


def _carregar():
    caminho = Path(__file__).with_name("pre_token_generation.py")
    spec = importlib.util.spec_from_file_location("pre_token_generation", caminho)
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


class PreTokenGenerationTest(unittest.TestCase):
    def setUp(self):
        os.environ["TOKEN_SCOPES"] = " ".join(ESCOPOS)
        self.modulo = _carregar()

    def test_claim_e_escopos_no_access_token(self):
        evento = {"request": {"userAttributes": {"custom:comerciante_id": COMERCIANTE}}}

        saida = self.modulo.handler(evento, None)
        access = saida["response"]["claimsAndScopeOverrideDetails"]["accessTokenGeneration"]
        identidade = saida["response"]["claimsAndScopeOverrideDetails"]["idTokenGeneration"]

        self.assertEqual(access["claimsToAddOrOverride"]["comerciante_id"], COMERCIANTE)
        self.assertEqual(access["scopesToAdd"], ESCOPOS)
        self.assertEqual(identidade["claimsToAddOrOverride"]["comerciante_id"], COMERCIANTE)
        self.assertNotIn("scopesToAdd", identidade)

    def test_atributo_developer_only(self):
        evento = {"request": {"userAttributes": {"dev:custom:comerciante_id": COMERCIANTE}}}

        saida = self.modulo.handler(evento, None)
        access = saida["response"]["claimsAndScopeOverrideDetails"]["accessTokenGeneration"]

        self.assertEqual(access["claimsToAddOrOverride"]["comerciante_id"], COMERCIANTE)

    def test_sem_escopos_quando_a_variavel_esta_vazia(self):
        os.environ["TOKEN_SCOPES"] = ""
        modulo = _carregar()
        evento = {"request": {"userAttributes": {"custom:comerciante_id": COMERCIANTE}}}

        saida = modulo.handler(evento, None)
        access = saida["response"]["claimsAndScopeOverrideDetails"]["accessTokenGeneration"]

        self.assertEqual(access["claimsToAddOrOverride"]["comerciante_id"], COMERCIANTE)
        self.assertNotIn("scopesToAdd", access)


if __name__ == "__main__":
    unittest.main()

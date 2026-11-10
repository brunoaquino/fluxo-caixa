package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ImpressaoDigital {

    private ImpressaoDigital() {
    }

    public static String deRegistro(RegistrarLancamentoComando comando) {
        String descricao = Lancamento.descricaoNormalizada(comando.descricao());
        String categoria = Lancamento.categoriaNormalizada(comando.categoria());
        String valor = new Dinheiro(comando.valor()).formatado();
        String canonico = String.join(
                "|",
                comando.tipo().name(),
                valor,
                comando.dataLancamento().toString(),
                descricao,
                categoria == null ? "" : categoria
        );
        return sha256(canonico);
    }

    public static String deEstorno(EstornarLancamentoComando comando) {
        String motivo = comando.motivo() == null ? "" : comando.motivo().trim();
        return sha256(comando.lancamentoId() + "|" + motivo);
    }

    private static String sha256(String canonico) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}

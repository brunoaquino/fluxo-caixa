package br.com.fluxocaixa.lancamentos.domain.lancamento;

import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.excecao.EstornoDeEstornoNaoPermitidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.TextoInvalidoException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Agregado imutável. Correção de um lançamento é sempre um novo lançamento de estorno;
 * não há atualização nem exclusão.
 */
public record Lancamento(
        UUID id,
        UUID comercianteId,
        TipoLancamento tipo,
        Dinheiro valor,
        LocalDate dataLancamento,
        String descricao,
        String categoria,
        UUID lancamentoEstornadoId,
        Instant criadoEm
) {

    public Lancamento {
        if (id == null || comercianteId == null || tipo == null || valor == null
                || dataLancamento == null || criadoEm == null) {
            throw new IllegalArgumentException("Dados obrigatórios do lançamento não informados");
        }
        descricao = descricaoNormalizada(descricao);
        categoria = categoriaNormalizada(categoria);
        if (id.equals(lancamentoEstornadoId)) {
            throw new IllegalArgumentException("Lançamento não pode estornar a si mesmo");
        }
    }

    public static Lancamento registrar(
            UUID id,
            UUID comercianteId,
            TipoLancamento tipo,
            Dinheiro valor,
            LocalDate dataLancamento,
            String descricao,
            String categoria,
            Instant criadoEm
    ) {
        return new Lancamento(
                id,
                comercianteId,
                tipo,
                valor,
                dataLancamento,
                descricao,
                categoria,
                null,
                criadoEm
        );
    }

    /**
     * Gera o lançamento de estorno: tipo oposto, mesmo valor e mesma data de competência.
     * O motivo, quando informado, vira a descrição do estorno.
     */
    public Lancamento estornar(UUID idEstorno, String motivo, Instant criadoEmEstorno) {
        if (ehEstorno()) {
            throw new EstornoDeEstornoNaoPermitidoException(id);
        }
        if (idEstorno == null || criadoEmEstorno == null) {
            throw new IllegalArgumentException("Identificador e instante do estorno são obrigatórios");
        }
        String descricaoEstorno = (motivo == null || motivo.isBlank())
                ? "Estorno do lançamento " + id
                : descricaoNormalizada(motivo);
        return new Lancamento(
                idEstorno,
                comercianteId,
                tipo.oposto(),
                valor,
                dataLancamento,
                descricaoEstorno,
                categoria,
                id,
                criadoEmEstorno
        );
    }

    public boolean ehEstorno() {
        return lancamentoEstornadoId != null;
    }

    public LancamentoRegistrado paraEvento(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId é obrigatório");
        }
        return new LancamentoRegistrado(
                eventId,
                LancamentoRegistrado.TIPO,
                LancamentoRegistrado.VERSAO,
                criadoEm,
                id,
                comercianteId,
                tipo,
                valor,
                dataLancamento,
                lancamentoEstornadoId
        );
    }

    public static String descricaoNormalizada(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new TextoInvalidoException("Descrição é obrigatória");
        }
        String normalizada = descricao.trim();
        if (normalizada.length() > 255) {
            throw new TextoInvalidoException("Descrição deve ter no máximo 255 caracteres");
        }
        return normalizada;
    }

    public static String categoriaNormalizada(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        String normalizada = categoria.trim();
        if (normalizada.length() > 60) {
            throw new TextoInvalidoException("Categoria deve ter no máximo 60 caracteres");
        }
        return normalizada;
    }
}

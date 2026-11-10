package br.com.fluxocaixa.lancamentos.domain.lancamento;

import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.excecao.EstornoDeEstornoNaoPermitidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.TextoInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LancamentoTest {

    private static final UUID ID = UUID.fromString("5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f");
    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AGORA = Instant.parse("2026-11-10T18:00:00.123Z");
    private static final LocalDate DATA = LocalDate.parse("2026-11-10");

    @Test
    void registraCreditoImutavel() {
        Lancamento lancamento = exemplo();

        assertThat(lancamento.ehEstorno()).isFalse();
        assertThat(lancamento.descricao()).isEqualTo("Venda balcão");
        assertThat(lancamento.categoria()).isEqualTo("VENDAS");
        assertThat(lancamento.lancamentoEstornadoId()).isNull();
    }

    @Test
    void normalizaTextoECategoriaVazia() {
        Lancamento lancamento = Lancamento.registrar(
                ID, COMERCIANTE, TipoLancamento.DEBITO, dinheiro(), DATA, "  Aluguel  ", "   ", AGORA
        );

        assertThat(lancamento.descricao()).isEqualTo("Aluguel");
        assertThat(lancamento.categoria()).isNull();
    }

    @Test
    void rejeitaDescricaoECategoriaInvalidas() {
        assertThatThrownBy(() -> exemploComDescricao("  "))
                .isInstanceOf(TextoInvalidoException.class);
        assertThatThrownBy(() -> exemploComDescricao("a".repeat(256)))
                .isInstanceOf(TextoInvalidoException.class);
        assertThatThrownBy(() -> Lancamento.registrar(
                ID, COMERCIANTE, TipoLancamento.CREDITO, dinheiro(), DATA, "Venda", "c".repeat(61), AGORA
        )).isInstanceOf(TextoInvalidoException.class);
    }

    @Test
    void estornoInverteTipoEMantemDataEValor() {
        Lancamento original = exemplo();
        UUID idEstorno = UUID.randomUUID();

        Lancamento estorno = original.estornar(idEstorno, "Cliente devolveu", AGORA.plusSeconds(5));

        assertThat(estorno.tipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(estorno.valor()).isEqualTo(original.valor());
        assertThat(estorno.dataLancamento()).isEqualTo(original.dataLancamento());
        assertThat(estorno.lancamentoEstornadoId()).isEqualTo(original.id());
        assertThat(estorno.descricao()).isEqualTo("Cliente devolveu");
        assertThat(estorno.categoria()).isEqualTo(original.categoria());
        assertThat(estorno.ehEstorno()).isTrue();
        assertThat(original.ehEstorno()).isFalse();
    }

    @Test
    void estornoSemMotivoGeraDescricaoPadrao() {
        Lancamento estorno = exemplo().estornar(UUID.randomUUID(), "  ", AGORA);

        assertThat(estorno.descricao()).isEqualTo("Estorno do lançamento " + ID);
    }

    @Test
    void naoPermiteEstornarUmEstorno() {
        Lancamento estorno = exemplo().estornar(UUID.randomUUID(), null, AGORA);

        assertThatThrownBy(() -> estorno.estornar(UUID.randomUUID(), "de novo", AGORA))
                .isInstanceOf(EstornoDeEstornoNaoPermitidoException.class);
    }

    @Test
    void tipoOpostoEExaustivo() {
        assertThat(TipoLancamento.CREDITO.oposto()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(TipoLancamento.DEBITO.oposto()).isEqualTo(TipoLancamento.CREDITO);
    }

    @Test
    void publicaEventoComOsDadosDoLancamento() {
        Lancamento lancamento = exemplo();
        UUID eventId = UUID.randomUUID();

        LancamentoRegistrado evento = lancamento.paraEvento(eventId);

        assertThat(evento.eventId()).isEqualTo(eventId);
        assertThat(evento.eventType()).isEqualTo("LancamentoRegistrado");
        assertThat(evento.eventVersion()).isEqualTo(1);
        assertThat(evento.occurredAt()).isEqualTo(AGORA);
        assertThat(evento.valor().formatado()).isEqualTo("150.00");
        assertThat(evento.lancamentoEstornadoId()).isNull();
    }

    @Test
    void rejeitaEventoIncompletoOuDeOutraVersao() {
        assertThatThrownBy(() -> exemplo().paraEvento(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LancamentoRegistrado(
                UUID.randomUUID(), "Outro", 2, AGORA, ID, COMERCIANTE,
                TipoLancamento.CREDITO, dinheiro(), DATA, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaLancamentoIncompletoOuAutoEstorno() {
        assertThatThrownBy(() -> new Lancamento(
                null, COMERCIANTE, TipoLancamento.CREDITO, dinheiro(), DATA, "Venda", null, null, AGORA
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Lancamento(
                ID, COMERCIANTE, TipoLancamento.CREDITO, dinheiro(), DATA, "Venda", null, ID, AGORA
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> exemplo().estornar(null, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Lancamento exemplo() {
        return exemploComDescricao("Venda balcão");
    }

    private static Lancamento exemploComDescricao(String descricao) {
        return Lancamento.registrar(
                ID, COMERCIANTE, TipoLancamento.CREDITO, dinheiro(), DATA, descricao, "VENDAS", AGORA
        );
    }

    private static Dinheiro dinheiro() {
        return new Dinheiro(new BigDecimal("150.00"));
    }
}

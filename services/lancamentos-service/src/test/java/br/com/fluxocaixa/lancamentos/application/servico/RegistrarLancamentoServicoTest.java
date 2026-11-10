package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaConflitanteException;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.port.out.GeradorDeIds;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.lancamento.LancamentoRegistrado;
import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarLancamentoServicoTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LANCAMENTO = UUID.fromString("5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f");
    private static final UUID EVENTO = UUID.fromString("0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11");
    private static final UUID CHAVE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant AGORA = Instant.parse("2026-11-10T18:00:00.123Z");

    @Mock
    private LancamentoRepositorio lancamentos;
    @Mock
    private IdempotenciaRepositorio idempotencia;
    @Mock
    private PublicacaoOutbox outbox;
    @Mock
    private Relogio relogio;
    @Mock
    private GeradorDeIds geradorDeIds;

    private RegistrarLancamentoServico servico;

    @BeforeEach
    void setUp() {
        servico = new RegistrarLancamentoServico(
                new GravadorDeLancamento(lancamentos, idempotencia, outbox, relogio, geradorDeIds),
                new ConsultaDeIdempotencia(idempotencia, lancamentos)
        );
    }

    @Test
    void gravaLancamentoEEventoNaMesmaOperacao() {
        when(relogio.agora()).thenReturn(AGORA);
        when(geradorDeIds.novo()).thenReturn(LANCAMENTO, EVENTO);
        RegistrarLancamentoComando comando = comando(null);

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isFalse();
        assertThat(resultado.lancamento().id()).isEqualTo(LANCAMENTO);
        assertThat(resultado.lancamento().valor().formatado()).isEqualTo("150.00");
        ArgumentCaptor<LancamentoRegistrado> evento = ArgumentCaptor.forClass(LancamentoRegistrado.class);
        verify(outbox).registrar(evento.capture());
        assertThat(evento.getValue().eventId()).isEqualTo(EVENTO);
        assertThat(evento.getValue().tipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(evento.getValue().valor().formatado()).isEqualTo("150.00");
        verify(idempotencia, never()).registrar(any());
    }

    @Test
    void reutilizaLancamentoQuandoAChaveJaExiste() {
        RegistrarLancamentoComando comando = comando(CHAVE);
        Lancamento existente = lancamento();
        when(idempotencia.buscar(CHAVE, COMERCIANTE)).thenReturn(Optional.of(
                new RegistroIdempotencia(CHAVE, COMERCIANTE, LANCAMENTO, ImpressaoDigital.deRegistro(comando))
        ));
        when(lancamentos.buscarPorId(LANCAMENTO, COMERCIANTE)).thenReturn(Optional.of(existente));

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isTrue();
        assertThat(resultado.lancamento()).isEqualTo(existente);
        verify(lancamentos, never()).salvar(any());
    }

    @Test
    void recusaChaveReutilizadaComOutroConteudo() {
        RegistrarLancamentoComando comando = comando(CHAVE);
        when(idempotencia.buscar(CHAVE, COMERCIANTE)).thenReturn(Optional.of(
                new RegistroIdempotencia(CHAVE, COMERCIANTE, LANCAMENTO, "outra-impressao")
        ));

        assertThatThrownBy(() -> servico.executar(comando))
                .isInstanceOf(IdempotenciaConflitanteException.class);
        verify(lancamentos, never()).salvar(any());
    }

    @Test
    void relêOVencedorQuandoAChaveColide() {
        when(relogio.agora()).thenReturn(AGORA);
        when(geradorDeIds.novo()).thenReturn(LANCAMENTO, EVENTO);
        RegistrarLancamentoComando comando = comando(CHAVE);
        Lancamento vencedor = lancamento();
        when(idempotencia.buscar(CHAVE, COMERCIANTE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new RegistroIdempotencia(
                        CHAVE, COMERCIANTE, LANCAMENTO, ImpressaoDigital.deRegistro(comando))));
        doThrow(new IdempotenciaDuplicadaException(CHAVE)).when(idempotencia).registrar(any());
        when(lancamentos.buscarPorId(LANCAMENTO, COMERCIANTE)).thenReturn(Optional.of(vencedor));

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isTrue();
        assertThat(resultado.lancamento()).isEqualTo(vencedor);
    }

    private static RegistrarLancamentoComando comando(UUID chave) {
        return new RegistrarLancamentoComando(
                COMERCIANTE,
                chave,
                TipoLancamento.CREDITO,
                new BigDecimal("150.00"),
                LocalDate.parse("2026-11-10"),
                "Venda balcão",
                "VENDAS"
        );
    }

    private static Lancamento lancamento() {
        return Lancamento.registrar(
                LANCAMENTO,
                COMERCIANTE,
                TipoLancamento.CREDITO,
                new Dinheiro(new BigDecimal("150.00")),
                LocalDate.parse("2026-11-10"),
                "Venda balcão",
                "VENDAS",
                AGORA
        );
    }
}

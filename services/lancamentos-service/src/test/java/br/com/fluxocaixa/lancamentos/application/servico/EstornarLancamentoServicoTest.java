package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.out.GeradorDeIds;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.excecao.EstornoDeEstornoNaoPermitidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
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
class EstornarLancamentoServicoTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGINAL = UUID.fromString("5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f");
    private static final UUID ESTORNO = UUID.fromString("6f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f");
    private static final UUID EVENTO = UUID.fromString("0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11");
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

    private EstornarLancamentoServico servico;

    @BeforeEach
    void setUp() {
        servico = new EstornarLancamentoServico(
                new GravadorDeEstorno(lancamentos, idempotencia, outbox, relogio, geradorDeIds),
                new ConsultaDeIdempotencia(idempotencia, lancamentos)
        );
    }

    @Test
    void criaEstornoOpostoEPublicaEvento() {
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(false);
        when(geradorDeIds.novo()).thenReturn(ESTORNO, EVENTO);
        when(relogio.agora()).thenReturn(AGORA.plusSeconds(30));

        var resultado = servico.executar(comando(null, "Devolução"));

        assertThat(resultado.reutilizado()).isFalse();
        assertThat(resultado.lancamento().tipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(resultado.lancamento().lancamentoEstornadoId()).isEqualTo(ORIGINAL);
        assertThat(resultado.lancamento().dataLancamento()).isEqualTo(LocalDate.parse("2026-11-10"));
        ArgumentCaptor<LancamentoRegistrado> evento = ArgumentCaptor.forClass(LancamentoRegistrado.class);
        verify(outbox).registrar(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(evento.getValue().lancamentoEstornadoId()).isEqualTo(ORIGINAL);
    }

    @Test
    void naoEncontraLancamento() {
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.executar(comando(null, null)))
                .isInstanceOf(LancamentoNaoEncontradoException.class);
        verify(outbox, never()).registrar(any());
    }

    @Test
    void naoEstornaEstorno() {
        Lancamento estorno = original().estornar(ESTORNO, null, AGORA);
        when(lancamentos.buscarPorId(ESTORNO, COMERCIANTE)).thenReturn(Optional.of(estorno));

        assertThatThrownBy(() -> servico.executar(new EstornarLancamentoComando(COMERCIANTE, ESTORNO, null, null)))
                .isInstanceOf(EstornoDeEstornoNaoPermitidoException.class);
    }

    @Test
    void naoEstornaDuasVezes() {
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(true);

        assertThatThrownBy(() -> servico.executar(comando(null, null)))
                .isInstanceOf(LancamentoJaEstornadoException.class);
    }

    @Test
    void devolveEstornoExistenteQuandoAChaveJaFoiUsada() {
        var comando = comando(UUID.randomUUID(), "Devolução");
        Lancamento estorno = original().estornar(ESTORNO, "Devolução", AGORA);
        when(idempotencia.buscar(comando.chaveIdempotencia(), COMERCIANTE)).thenReturn(Optional.of(
                new RegistroIdempotencia(
                        comando.chaveIdempotencia(),
                        COMERCIANTE,
                        ESTORNO,
                        ImpressaoDigital.deEstorno(comando)
                )
        ));
        when(lancamentos.buscarPorId(ESTORNO, COMERCIANTE)).thenReturn(Optional.of(estorno));

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isTrue();
        assertThat(resultado.lancamento().id()).isEqualTo(ESTORNO);
        verify(lancamentos, never()).salvar(any());
    }

    @Test
    void gravaChaveDeIdempotenciaDoEstorno() {
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(false);
        when(geradorDeIds.novo()).thenReturn(ESTORNO, EVENTO);
        when(relogio.agora()).thenReturn(AGORA);

        var resultado = servico.executar(comando(UUID.randomUUID(), null));

        assertThat(resultado.reutilizado()).isFalse();
        verify(idempotencia).registrar(any());
    }

    @Test
    void reaproveitaEstornoVencedorAposColisaoDaChave() {
        UUID chave = UUID.randomUUID();
        var comando = comando(chave, "Devolução");
        Lancamento estorno = original().estornar(ESTORNO, "Devolução", AGORA);
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(false);
        when(geradorDeIds.novo()).thenReturn(ESTORNO, EVENTO);
        when(relogio.agora()).thenReturn(AGORA);
        when(idempotencia.buscar(chave, COMERCIANTE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new RegistroIdempotencia(
                        chave, COMERCIANTE, ESTORNO, ImpressaoDigital.deEstorno(comando))));
        doThrow(new IdempotenciaDuplicadaException(chave)).when(idempotencia).registrar(any());
        when(lancamentos.buscarPorId(ESTORNO, COMERCIANTE)).thenReturn(Optional.of(estorno));

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isTrue();
    }

    @Test
    void reaproveitaEstornoQuandoAUnicidadeEstouraDepoisDoCommitVencedor() {
        UUID chave = UUID.randomUUID();
        var comando = comando(chave, null);
        Lancamento estorno = original().estornar(ESTORNO, null, AGORA);
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(false);
        when(geradorDeIds.novo()).thenReturn(ESTORNO);
        when(relogio.agora()).thenReturn(AGORA);
        doThrow(new LancamentoJaEstornadoException(ORIGINAL)).when(lancamentos).salvar(any());
        when(idempotencia.buscar(chave, COMERCIANTE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new RegistroIdempotencia(
                        chave, COMERCIANTE, ESTORNO, ImpressaoDigital.deEstorno(comando))));
        when(lancamentos.buscarPorId(ESTORNO, COMERCIANTE)).thenReturn(Optional.of(estorno));

        var resultado = servico.executar(comando);

        assertThat(resultado.reutilizado()).isTrue();
    }

    @Test
    void propagaJaEstornadoQuandoNaoHaChaveParaReconciliar() {
        when(lancamentos.buscarPorId(ORIGINAL, COMERCIANTE)).thenReturn(Optional.of(original()));
        when(lancamentos.existeEstornoPara(COMERCIANTE, ORIGINAL)).thenReturn(false);
        when(geradorDeIds.novo()).thenReturn(ESTORNO);
        when(relogio.agora()).thenReturn(AGORA);
        doThrow(new LancamentoJaEstornadoException(ORIGINAL)).when(lancamentos).salvar(any());

        assertThatThrownBy(() -> servico.executar(comando(null, null)))
                .isInstanceOf(LancamentoJaEstornadoException.class);
    }

    private static EstornarLancamentoComando comando(UUID chave, String motivo) {
        return new EstornarLancamentoComando(COMERCIANTE, ORIGINAL, chave, motivo);
    }

    private static Lancamento original() {
        return Lancamento.registrar(
                ORIGINAL,
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

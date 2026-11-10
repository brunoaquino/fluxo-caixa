package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaDeLancamentosTest {

    @Mock
    private LancamentoRepositorio lancamentos;

    @Test
    void consultaLancamentoDoComerciante() {
        UUID comerciante = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Lancamento lancamento = Lancamento.registrar(
                id, comerciante, TipoLancamento.DEBITO, new Dinheiro(new BigDecimal("10.00")),
                LocalDate.parse("2026-11-10"), "Aluguel", null, Instant.parse("2026-11-10T12:00:00Z")
        );
        when(lancamentos.buscarPorId(id, comerciante)).thenReturn(Optional.of(lancamento));

        Lancamento encontrado = new ConsultarLancamentoServico(lancamentos).executar(comerciante, id);

        assertThat(encontrado).isEqualTo(lancamento);
    }

    @Test
    void consultaInexistente() {
        UUID comerciante = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(lancamentos.buscarPorId(id, comerciante)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ConsultarLancamentoServico(lancamentos).executar(comerciante, id))
                .isInstanceOf(LancamentoNaoEncontradoException.class);
    }

    @Test
    void listaPaginaPorData() {
        UUID comerciante = UUID.randomUUID();
        LocalDate data = LocalDate.parse("2026-11-10");
        Pagina<Lancamento> pagina = new Pagina<>(List.of(), 0, 50, 0, 0);
        when(lancamentos.listarPorData(comerciante, data, 0, 50)).thenReturn(pagina);

        assertThat(new ListarLancamentosServico(lancamentos).executar(comerciante, data, 0, 50)).isEqualTo(pagina);
    }
}

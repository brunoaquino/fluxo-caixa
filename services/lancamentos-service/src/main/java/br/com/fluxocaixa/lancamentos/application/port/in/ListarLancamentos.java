package br.com.fluxocaixa.lancamentos.application.port.in;

import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

import java.time.LocalDate;
import java.util.UUID;

public interface ListarLancamentos {

    Pagina<Lancamento> executar(UUID comercianteId, LocalDate data, int pagina, int tamanho);
}

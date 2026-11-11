package br.com.fluxocaixa.lancamentos.adapters.in.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroCorrelacaoTest {

    private final FiltroCorrelacao filtro = new FiltroCorrelacao();

    @AfterEach
    void limpa() {
        MDC.clear();
    }

    @Test
    void headerAusenteOuInvalidoGeraUuidEDevolveNaResposta() throws Exception {
        assertGerado(null);
        assertGerado("   ");
        assertGerado("nao-e-uuid");
        assertGerado("11111111-1111-1111-1111-111111111111\r\nX-Injected: sim");
    }

    @Test
    void headerUuidECanonizadoELimpoDoMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FiltroCorrelacao.CABECALHO, "  22222222-2222-4222-8222-222222222222  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> visto = new AtomicReference<>();

        filtro.doFilter(request, response, (FilterChain) (req, res) -> visto.set(MDC.get(FiltroCorrelacao.MDC_CORRELACAO)));

        assertThat(visto.get()).isEqualTo("22222222-2222-4222-8222-222222222222");
        assertThat(response.getHeader(FiltroCorrelacao.CABECALHO)).isEqualTo(visto.get());
        assertThat(MDC.get(FiltroCorrelacao.MDC_CORRELACAO)).isNull();
    }

    private void assertGerado(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(FiltroCorrelacao.CABECALHO, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> visto = new AtomicReference<>();
        filtro.doFilter(request, response, (FilterChain) (req, res) -> visto.set(MDC.get(FiltroCorrelacao.MDC_CORRELACAO)));
        assertThat(UUID.fromString(visto.get())).isNotNull();
        assertThat(visto.get()).isNotEqualTo(header);
        assertThat(response.getHeader(FiltroCorrelacao.CABECALHO)).isEqualTo(visto.get());
    }
}

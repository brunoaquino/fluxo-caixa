package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.observabilidade.CorrelationId;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroCorrelationIdTest {

    private final FiltroCorrelationId filtro = new FiltroCorrelationId();

    @Test
    void geraEDevolveQuandoAusenteOuInvalido() throws Exception {
        MockHttpServletResponse ausente = executar(new MockHttpServletRequest(), (request, response) ->
                assertThat(MDC.get(CorrelationId.MDC)).isNotBlank());
        assertThat(ausente.getHeader(CorrelationId.HEADER)).matches(uuid());
        assertThat(MDC.get(CorrelationId.MDC)).isNull();

        MockHttpServletRequest invalido = new MockHttpServletRequest();
        invalido.addHeader(CorrelationId.HEADER, "abc\r\nSet-Cookie: evil");
        MockHttpServletResponse resposta = executar(invalido, (request, response) -> { });
        assertThat(resposta.getHeader(CorrelationId.HEADER)).matches(uuid());
        assertThat(resposta.getHeader(CorrelationId.HEADER)).doesNotContain("Set-Cookie");
    }

    @Test
    void preservaUuidInformado() throws Exception {
        String informado = "AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, informado);
        MockHttpServletResponse response = executar(request, (req, res) ->
                assertThat(MDC.get(CorrelationId.MDC)).isEqualTo(informado.toLowerCase()));
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(informado.toLowerCase());
        assertThat(MDC.get(CorrelationId.MDC)).isNull();
    }

    private MockHttpServletResponse executar(MockHttpServletRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, chain);
        return response;
    }

    private static String uuid() {
        return "^" + UUID.randomUUID().toString().replaceAll("[0-9a-f]", "[0-9a-f]") + "$";
    }
}

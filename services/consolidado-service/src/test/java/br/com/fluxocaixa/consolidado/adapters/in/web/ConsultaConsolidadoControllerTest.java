package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarRelatorioUseCase;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.config.ConfiguracaoSeguranca;
import br.com.fluxocaixa.consolidado.config.JwtDecoderTesteConfig;
import br.com.fluxocaixa.consolidado.domain.exception.PeriodoConsultaInvalidoException;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConsultaConsolidadoController.class)
@ActiveProfiles("test")
@Import({ConfiguracaoSeguranca.class, ComercianteAutenticado.class, JwtDecoderTesteConfig.class, TratadorErrosApi.class})
class ConsultaConsolidadoControllerTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockitoBean
    private ConsultarSaldoDiarioUseCase consultarSaldo;

    @MockitoBean
    private ConsultarRelatorioUseCase consultarRelatorio;

    @Test
    void diaSemMovimentoRetornaZeros() throws Exception {
        when(consultarSaldo.consultar(COMERCIANTE, LocalDate.of(2026, 11, 12)))
                .thenReturn(SaldoDiarioConsulta.de(MovimentoDia.vazio(LocalDate.of(2026, 11, 12)), new BigDecimal("210.00")));

        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-12").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreditos").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.totalDebitos").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.saldo").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.saldoAcumulado").value(closeTo(210.0, 0.001)))
                .andExpect(jsonPath("$.quantidadeLancamentos").value(0))
                .andExpect(jsonPath("$.atualizadoEm").value(nullValue()));
    }

    @Test
    void retornaSaldoDoDia() throws Exception {
        LocalDate data = LocalDate.of(2026, 11, 10);
        when(consultarSaldo.consultar(COMERCIANTE, data)).thenReturn(new SaldoDiarioConsulta(
                data,
                new BigDecimal("250.00"),
                new BigDecimal("50.00"),
                new BigDecimal("200.00"),
                new BigDecimal("200.00"),
                3,
                Instant.parse("2026-11-10T18:00:00Z")));

        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$.saldoAcumulado").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$.quantidadeLancamentos").value(3));
    }

    @Test
    void relatorioDoPeriodo() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 11, 10);
        LocalDate fim = LocalDate.of(2026, 11, 11);
        when(consultarRelatorio.consultar(COMERCIANTE, inicio, fim)).thenReturn(new RelatorioPeriodoConsulta(
                inicio,
                fim,
                List.of(
                        SaldoDiarioConsulta.de(MovimentoDia.vazio(inicio), BigDecimal.ZERO),
                        SaldoDiarioConsulta.de(MovimentoDia.vazio(fim), BigDecimal.ZERO))));

        mockMvc.perform(get("/api/v1/consolidados/diario").param("inicio", "2026-11-10").param("fim", "2026-11-11").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(2));
    }

    @Test
    void periodoInvalidoRetornaProblemDetail() throws Exception {
        when(consultarRelatorio.consultar(any(), any(), any()))
                .thenThrow(new PeriodoConsultaInvalidoException(PeriodoConsultaInvalidoException.Motivo.INTERVALO_MAIOR_QUE_366));

        mockMvc.perform(get("/api/v1/consolidados/diario").param("inicio", "2024-01-01").param("fim", "2025-01-02").with(token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Período inválido"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void dataInvalidaRetornaProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/consolidados/diario/2026-13-40").with(token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void escopoErradoRetorna403() throws Exception {
        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10")
                        .with(jwt().jwt(jwt -> jwt.claim("comerciante_id", COMERCIANTE.toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_lancamentos:read"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void claimAusenteRetorna403() throws Exception {
        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_consolidado:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Comerciante não identificado"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(jwt -> jwt.claim("comerciante_id", COMERCIANTE.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_consolidado:read"));
    }
}

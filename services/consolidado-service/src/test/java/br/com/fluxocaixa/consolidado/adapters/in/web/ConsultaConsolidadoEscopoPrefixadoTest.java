package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarRelatorioUseCase;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.config.ConfiguracaoSeguranca;
import br.com.fluxocaixa.consolidado.config.JwtDecoderTesteConfig;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConsultaConsolidadoController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.scope-prefix=fluxo-caixa/")
@Import({ConfiguracaoSeguranca.class, ComercianteAutenticado.class, JwtDecoderTesteConfig.class, LimitadorPassThroughConfig.class})
class ConsultaConsolidadoEscopoPrefixadoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarSaldoDiarioUseCase consultarSaldo;

    @MockitoBean
    private ConsultarRelatorioUseCase consultarRelatorio;

    @Test
    void exigeEscopoComPrefixo() throws Exception {
        when(consultarSaldo.consultar(any(), any())).thenReturn(SaldoDiarioConsulta.de(
                MovimentoDia.vazio(LocalDate.of(2026, 11, 10)),
                BigDecimal.ZERO));

        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10")
                        .with(jwt().jwt(jwt -> jwt.claim("comerciante_id", "11111111-1111-1111-1111-111111111111"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_consolidado:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-10")
                        .with(jwt().jwt(jwt -> jwt.claim("comerciante_id", "11111111-1111-1111-1111-111111111111"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_fluxo-caixa/consolidado:read"))))
                .andExpect(status().isOk());
    }
}

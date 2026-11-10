package br.com.fluxocaixa.lancamentos.adapters.in.web;

import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaConflitanteException;
import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.in.ConsultarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.in.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.in.ListarLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.in.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.config.DecodificadorJwtConfig;
import br.com.fluxocaixa.lancamentos.config.EscritorDeProblema;
import br.com.fluxocaixa.lancamentos.config.PropriedadesSeguranca;
import br.com.fluxocaixa.lancamentos.config.SegurancaConfig;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LancamentoController.class)
@AutoConfigureMockMvc
@Import({SegurancaConfig.class, DecodificadorJwtConfig.class, TratadorDeErros.class, ComercianteAutenticado.class, EscritorDeProblema.class})
@EnableConfigurationProperties(PropriedadesSeguranca.class)
@TestPropertySource(properties = {
        "JWT_ISSUER_URI=http://localhost/realms/fluxo-caixa",
        "JWT_JWK_SET_URI=http://127.0.0.1:9/jwks",
        "LANCAMENTOS_TOPIC_ARN=arn:aws:sns:sa-east-1:000000000000:fluxo-caixa-lancamentos-events",
        "DB_URL=jdbc:postgresql://localhost:1/lancamentos",
        "DB_USERNAME=lancamentos",
        "DB_PASSWORD=lancamentos"
})
class LancamentoControllerTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID = UUID.fromString("5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarLancamento registrarLancamento;
    @MockitoBean
    private ConsultarLancamento consultarLancamento;
    @MockitoBean
    private ListarLancamentos listarLancamentos;
    @MockitoBean
    private EstornarLancamento estornarLancamento;

    @Test
    void registraEDevolveCriado() throws Exception {
        when(registrarLancamento.executar(any())).thenReturn(new ResultadoEscrita(lancamento(), false));

        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("comerciante_id", COMERCIANTE.toString())
                                .claim("scope", "lancamentos:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão","categoria":"VENDAS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(ID.toString())))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.valor").value("150.00"))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.tipo").value("CREDITO"));
    }

    @Test
    void repeticaoDaChaveDevolveOk() throws Exception {
        when(registrarLancamento.executar(any())).thenReturn(new ResultadoEscrita(lancamento(), true));

        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão","categoria":"VENDAS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void chaveInvalidaEConflitoDeIdempotencia() throws Exception {
        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", "nao-e-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        when(registrarLancamento.executar(any())).thenThrow(new IdempotenciaConflitanteException(UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":10.00,"dataLancamento":"2026-11-10","descricao":"Outra venda"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito de idempotência"));
    }

    @Test
    void validacaoENaoAutenticado() throws Exception {
        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":0,"dataLancamento":"2026-11-10","descricao":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/v1/lancamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":10.00,"dataLancamento":"2026-11-10","descricao":"Venda"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void escopoInsuficiente() throws Exception {
        mockMvc.perform(get("/api/v1/lancamentos/" + ID).with(token("lancamentos:write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void detalhaListaENaoEncontrado() throws Exception {
        when(consultarLancamento.executar(COMERCIANTE, ID)).thenReturn(lancamento());
        mockMvc.perform(get("/api/v1/lancamentos/" + ID).with(token("lancamentos:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Venda balcão"));

        when(consultarLancamento.executar(COMERCIANTE, ID)).thenThrow(new LancamentoNaoEncontradoException(ID));
        mockMvc.perform(get("/api/v1/lancamentos/" + ID).with(token("lancamentos:read")))
                .andExpect(status().isNotFound());

        when(listarLancamentos.executar(COMERCIANTE, LocalDate.parse("2026-11-10"), 0, 50))
                .thenReturn(new Pagina<>(List.of(lancamento()), 0, 50, 1, 1));
        mockMvc.perform(get("/api/v1/lancamentos")
                        .with(token("lancamentos:read"))
                        .param("data", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(ID.toString()));
    }

    @Test
    void estornaERecusaSegundoEstorno() throws Exception {
        Lancamento estorno = lancamento().estornar(UUID.randomUUID(), "Devolução", Instant.parse("2026-11-10T19:00:00Z"));
        when(estornarLancamento.executar(any())).thenReturn(new ResultadoEscrita(estorno, false));

        mockMvc.perform(post("/api/v1/lancamentos/" + ID + "/estornos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Devolução"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEBITO"))
                .andExpect(jsonPath("$.lancamentoEstornadoId").value(ID.toString()));

        when(estornarLancamento.executar(any())).thenThrow(new LancamentoJaEstornadoException(ID));
        mockMvc.perform(post("/api/v1/lancamentos/" + ID + "/estornos")
                        .with(token("lancamentos:write")))
                .andExpect(status().isConflict());
    }

    @Test
    void paginacaoInvalidaEComercianteAusente() throws Exception {
        mockMvc.perform(get("/api/v1/lancamentos")
                        .with(token("lancamentos:read"))
                        .param("data", "2026-11-10")
                        .param("size", "500"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/lancamentos/" + ID)
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "lancamentos:read"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(String escopos) {
        return jwt().jwt(token -> token
                .claim("comerciante_id", COMERCIANTE.toString())
                .claim("scope", escopos));
    }

    private static Lancamento lancamento() {
        return Lancamento.registrar(
                ID,
                COMERCIANTE,
                TipoLancamento.CREDITO,
                new Dinheiro(new BigDecimal("150.00")),
                LocalDate.parse("2026-11-10"),
                "Venda balcão",
                "VENDAS",
                Instant.parse("2026-11-10T18:00:00.123Z")
        );
    }
}

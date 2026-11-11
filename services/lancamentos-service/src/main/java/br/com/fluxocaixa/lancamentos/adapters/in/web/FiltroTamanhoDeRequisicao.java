package br.com.fluxocaixa.lancamentos.adapters.in.web;

import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.CorpoGrandeDemaisException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FiltroTamanhoDeRequisicao extends OncePerRequestFilter {

    static final int MAXIMO_BYTES = 8 * 1024;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        long comprimento = request.getContentLengthLong();
        if (comprimento > MAXIMO_BYTES) {
            rejeitar(response);
            return;
        }
        filterChain.doFilter(new RequisicaoLimitada(request), response);
    }

    private static void rejeitar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://fluxocaixa.local/problemas/corpo-grande","title":"Corpo grande demais","status":413,"detail":"O corpo da requisição excede o limite de 8 KB"}
                """);
    }

    private static final class RequisicaoLimitada extends HttpServletRequestWrapper {

        private RequisicaoLimitada(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new FluxoLimitado(super.getInputStream());
        }
    }

    private static final class FluxoLimitado extends ServletInputStream {

        private final ServletInputStream origem;
        private int lidos;

        private FluxoLimitado(ServletInputStream origem) {
            this.origem = origem;
        }

        @Override
        public boolean isFinished() {
            return origem.isFinished();
        }

        @Override
        public boolean isReady() {
            return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            origem.setReadListener(readListener);
        }

        @Override
        public int read() throws IOException {
            int byteLido = origem.read();
            if (byteLido >= 0) {
                contar(1);
            }
            return byteLido;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int quantidade = origem.read(buffer, offset, length);
            if (quantidade > 0) {
                contar(quantidade);
            }
            return quantidade;
        }

        private void contar(int quantidade) {
            lidos += quantidade;
            if (lidos > MAXIMO_BYTES) {
                throw new CorpoGrandeDemaisException();
            }
        }
    }
}

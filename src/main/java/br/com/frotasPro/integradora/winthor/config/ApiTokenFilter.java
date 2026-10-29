package br.com.frotasPro.integradora.winthor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * A integradora não tem login: quem alcançar a porta lia nota fiscal, devoluções e o log do
 * servidor. Este filtro só deixa passar quem manda o segredo combinado com a frota-pro-api
 * no cabeçalho X-Integradora-Token (variável INTEGRADORA_API_TOKEN, igual nos dois lados).
 *
 * Sem o segredo configurado o filtro fica desligado (e avisa no log): assim o deploy não
 * derruba a integração antes de os dois lados terem o valor. /actuator/health continua
 * aberto pro healthcheck do Docker; ele não mostra detalhes.
 */
@Slf4j
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Integradora-Token";

    private final byte[] tokenEsperado;

    public ApiTokenFilter(@Value("${frotapro.integradora.api-token:}") String token) {
        this.tokenEsperado = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @PostConstruct
    void avisarSeDesligado() {
        if (tokenEsperado.length == 0) {
            log.warn("INTEGRADORA_API_TOKEN não configurado: a integradora está ACEITANDO chamadas sem autenticação. "
                    + "Defina o mesmo valor aqui e na frota-pro-api.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return tokenEsperado.length == 0 || "/actuator/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(HEADER);
        byte[] recebidoBytes = recebido == null ? new byte[0] : recebido.trim().getBytes(StandardCharsets.UTF_8);

        // comparação em tempo constante, pra não vazar o segredo pelo tempo de resposta
        if (!MessageDigest.isEqual(tokenEsperado, recebidoBytes)) {
            log.warn("Chamada recusada na integradora (token ausente ou inválido): {} {} de {}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Não autorizado\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

package br.com.frotasPro.integradora.winthor.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiTokenFilterTest {

    private MockHttpServletResponse chamar(String tokenConfigurado, String caminho, String tokenEnviado) throws Exception {
        ApiTokenFilter filtro = new ApiTokenFilter(tokenConfigurado);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", caminho);
        if (tokenEnviado != null) {
            request.addHeader(ApiTokenFilter.HEADER, tokenEnviado);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, response, chain);
        // MockFilterChain guarda o request quando a cadeia seguiu adiante
        response.setHeader("X-Seguiu", chain.getRequest() != null ? "sim" : "nao");
        return response;
    }

    @Test
    void comTokenCorretoPassa() throws Exception {
        MockHttpServletResponse r = chamar("segredo", "/winthor/cargas/1/notas/2/xml", "segredo");
        assertEquals("sim", r.getHeader("X-Seguiu"));
        assertEquals(200, r.getStatus());
    }

    @Test
    void semTokenOuComTokenErradoRecebe401EANaoChegaNoControlador() throws Exception {
        for (String enviado : new String[]{null, "", "errado", "segredo-extra", "segred"}) {
            MockHttpServletResponse r = chamar("segredo", "/winthor/cargas/1/notas/2/xml", enviado);
            assertEquals(401, r.getStatus(), "enviado=" + enviado);
            assertEquals("nao", r.getHeader("X-Seguiu"));
        }
    }

    @Test
    void healthContinuaAbertoParaOHealthcheck() throws Exception {
        MockHttpServletResponse r = chamar("segredo", "/actuator/health", null);
        assertEquals("sim", r.getHeader("X-Seguiu"));
    }

    @Test
    void logfileEOutrosEndpointsDoActuatorExigemToken() throws Exception {
        assertEquals(401, chamar("segredo", "/actuator/logfile", null).getStatus());
        assertEquals(401, chamar("segredo", "/actuator/info", "x").getStatus());
    }

    @Test
    void semSegredoConfiguradoOFiltroFicaDesligado() throws Exception {
        for (String configurado : new String[]{"", "   ", null}) {
            MockHttpServletResponse r = chamar(configurado, "/winthor/cargas/1/notas/2/xml", null);
            assertEquals("sim", r.getHeader("X-Seguiu"));
            assertEquals(200, r.getStatus());
        }
        assertNull(null);
        assertNotNull(new ApiTokenFilter(null));
    }
}

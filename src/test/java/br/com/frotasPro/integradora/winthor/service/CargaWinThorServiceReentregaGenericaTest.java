package br.com.frotasPro.integradora.winthor.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargaWinThorServiceReentregaGenericaTest {

    private CargaWinThorService service() {
        return new CargaWinThorService(Mockito.mock(JdbcTemplate.class));
    }

    @Test
    void porPadraoTiraOMotoristaEOVeiculoGenericosDeReentregaDasCargas() {
        String sql = service().filtroSemReentregaGenerica();

        assertTrue(sql.contains("NVL(c.codmotorista, 0) NOT IN (10007)"), sql);
        assertTrue(sql.contains("NVL(c.codveiculo, 0) NOT IN (278, 2)"), sql);
        assertTrue(sql.stripLeading().startsWith("AND"), "precisa ser um AND para encaixar no WHERE");
    }

    @Test
    void naConsultaDeExistenciaViraNotExistsNoCarregamento() {
        String sql = service().filtroExistenciaSemReentregaGenerica();

        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM pccarreg rc WHERE rc.numcar = n.numcar"), sql);
        assertTrue(sql.contains("rc.codmotorista IN (10007) OR rc.codveiculo IN (278, 2)"), sql);
    }

    @Test
    void aceitaVariosCodigosEIgnoraLixo() {
        CargaWinThorService service = service();
        ReflectionTestUtils.setField(service, "codigosMotoristaReentrega", " 10007, 10008 ,abc;drop,");
        ReflectionTestUtils.setField(service, "codigosVeiculoReentrega", "278");

        String sql = service.filtroSemReentregaGenerica();

        assertTrue(sql.contains("NOT IN (10007, 10008)"), "só números entram no SQL: " + sql);
        assertTrue(!sql.contains("abc") && !sql.contains("drop"));
    }

    @Test
    void vazioDesligaARegra() {
        CargaWinThorService service = service();
        ReflectionTestUtils.setField(service, "codigosMotoristaReentrega", "");
        ReflectionTestUtils.setField(service, "codigosVeiculoReentrega", "  ");

        assertEquals("", service.filtroSemReentregaGenerica());
        assertEquals("", service.filtroExistenciaSemReentregaGenerica());
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtrarReentregaGenericaPerguntaAoCarregamentoEDevolveSoOsGenericos() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        CargaWinThorService service = new CargaWinThorService(jdbc);
        Mockito.when(jdbc.query(Mockito.anyString(), Mockito.any(Object[].class), Mockito.<RowMapper<Integer>>any()))
                .thenReturn(List.of(422308));

        List<Integer> resultado = service.filtrarReentregaGenerica(List.of(422308, 422122, 422308));

        assertEquals(List.of(422308), resultado);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbc).query(sql.capture(), Mockito.any(Object[].class), Mockito.<RowMapper<Integer>>any());
        assertTrue(sql.getValue().contains("FROM pccarreg c"), sql.getValue());
        assertTrue(sql.getValue().contains("c.codmotorista IN (10007) OR c.codveiculo IN (278, 2)"), sql.getValue());
        assertTrue(sql.getValue().contains("c.numcar IN (?,?)"), "códigos repetidos são tirados: " + sql.getValue());
    }

    @Test
    void filtrarReentregaGenericaComRegraDesligadaNemConsultaOBanco() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        CargaWinThorService service = new CargaWinThorService(jdbc);
        ReflectionTestUtils.setField(service, "codigosMotoristaReentrega", "");
        ReflectionTestUtils.setField(service, "codigosVeiculoReentrega", "");

        assertEquals(List.of(), service.filtrarReentregaGenerica(List.of(1, 2)));
        Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void parseCodigos() {
        assertEquals(List.of(1, 22), CargaWinThorService.parseCodigos("1, 22, x"));
        assertEquals(List.of(), CargaWinThorService.parseCodigos(null));
    }
}

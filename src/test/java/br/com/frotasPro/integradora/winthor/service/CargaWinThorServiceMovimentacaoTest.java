package br.com.frotasPro.integradora.winthor.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CargaWinThorServiceMovimentacaoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 6);

    private ResultSet linha(Object a, Object b) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("a")).thenReturn(a);
        when(rs.getObject("b")).thenReturn(b);
        return rs;
    }

    @Test
    void transferenciaDeHojeTrazOrigemEDestinoMesmoComNotaAntiga() throws SQLException {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(linha(422238, 422788));
            h.processRow(linha(422717, 422541));
            return null;
        }).when(jdbc).query(contains("pclogtransfnfcarreg"), any(RowCallbackHandler.class), any(), any());
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(linha(421000, null));
            return null;
        }).when(jdbc).query(contains("view_devol_resumo_faturamento"), any(RowCallbackHandler.class), any(), any());

        Set<Integer> codigos = new CargaWinThorService(jdbc).buscarCodigosComMovimentacaoNoPeriodo(DIA, DIA);

        assertEquals(Set.of(422238, 422788, 422717, 422541, 421000), codigos);
    }

    @Test
    void ignoraCarregamentoZeroOuUmEValoresNulos() throws SQLException {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(linha(0, 1));
            h.processRow(linha(null, 422500));
            return null;
        }).when(jdbc).query(contains("pclogtransfnfcarreg"), any(RowCallbackHandler.class), any(), any());

        Set<Integer> codigos = new CargaWinThorService(jdbc).buscarCodigosComMovimentacaoNoPeriodo(DIA, DIA);

        assertEquals(Set.of(422500), codigos);
    }

    @Test
    void falhaEmUmaConsultaNaoDerrubaAOutraNemOSync() throws SQLException {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new RuntimeException("ORA-00942")).when(jdbc)
                .query(contains("pclogtransfnfcarreg"), any(RowCallbackHandler.class), any(), any());
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(linha(421000, null));
            return null;
        }).when(jdbc).query(contains("view_devol_resumo_faturamento"), any(RowCallbackHandler.class), any(), any());

        Set<Integer> codigos = new CargaWinThorService(jdbc).buscarCodigosComMovimentacaoNoPeriodo(DIA, DIA);

        assertEquals(Set.of(421000), codigos);
    }

    @Test
    void semPeriodoNaoConsultaNada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        Set<Integer> codigos = new CargaWinThorService(jdbc).buscarCodigosComMovimentacaoNoPeriodo(null, DIA);

        assertTrue(codigos.isEmpty());
        verify(jdbc, never()).query(any(String.class), any(RowCallbackHandler.class), any(), any());
    }

    @Test
    void lotesRespeitamOTamanhoMaximoDoIn() {
        List<Integer> itens = java.util.stream.IntStream.rangeClosed(1, 1203).boxed().toList();

        List<List<Integer>> lotes = CargaWinThorService.emLotes(itens, 500);

        assertEquals(3, lotes.size());
        assertEquals(500, lotes.get(0).size());
        assertEquals(500, lotes.get(1).size());
        assertEquals(203, lotes.get(2).size());
        assertTrue(CargaWinThorService.emLotes(List.<Integer>of(), 500).isEmpty());
    }
}

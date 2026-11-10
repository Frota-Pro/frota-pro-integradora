package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ConferenciaMotoristaCaminhaoItemDto;
import br.com.frotasPro.integradora.winthor.service.ConferenciaMotoristaCaminhaoWinThorService.Transferencia;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConferenciaMotoristaCaminhaoWinThorServiceTest {

    private static final LocalDateTime GERACAO_MDFE = LocalDateTime.of(2026, 11, 3, 16, 45, 52);

    private static Map<String, Object> row(Object... chaveValor) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            m.put((String) chaveValor[i], chaveValor[i + 1]);
        }
        return m;
    }

    private static Transferencia t(long nota, int anterior, int atual, LocalDateTime quando) {
        return new Transferencia(nota, anterior, atual, quando, "TRANSF. DE PEDIDO ENTRE CARGAS");
    }

    // ------------------------------------------------------------------ regra pura

    @Test
    void notaQueEntrouDepoisDoManifestoEContaComoEvidencia() {
        List<Transferencia> doCarregamento = List.of(
                t(1696853, 422810, 422812, LocalDateTime.of(2026, 11, 10, 8, 30, 4)),
                t(1696878, 422811, 422812, LocalDateTime.of(2026, 11, 10, 8, 29, 47)),
                t(1696879, 422186, 422812, LocalDateTime.of(2026, 11, 6, 14, 56, 52)));
        Map<Long, Set<Long>> mdfesDaNota = Map.of(1696853L, Set.of(33288L), 1696878L, Set.of(33288L), 1696879L, Set.of(33288L));

        List<Transferencia> evidencias = ConferenciaMotoristaCaminhaoWinThorService.transferenciasPosterioresAoMdfe(
                33288L, GERACAO_MDFE, doCarregamento, mdfesDaNota, Set.of(1696853L, 1696878L, 1696879L));

        assertEquals(List.of(1696879L, 1696878L, 1696853L), evidencias.stream().map(Transferencia::numNota).toList(),
                "em ordem de data de transferência");
    }

    @Test
    void notaQueJaEstavaNoCarregamentoQuandoOManifestoFoiGeradoNaoContaCaso422186() {
        // 3/11 16:43 — dois minutos ANTES do manifesto: é a viagem normal, vale o manifesto.
        List<Transferencia> doCarregamento = List.of(
                t(1696878, 422213, 422186, LocalDateTime.of(2026, 11, 3, 16, 43, 15)),
                t(1696879, 422213, 422186, LocalDateTime.of(2026, 11, 3, 16, 43, 16)));

        List<Transferencia> evidencias = ConferenciaMotoristaCaminhaoWinThorService.transferenciasPosterioresAoMdfe(
                33288L, GERACAO_MDFE, doCarregamento,
                Map.of(1696878L, Set.of(33288L), 1696879L, Set.of(33288L)),
                Set.of(1696878L, 1696879L));

        assertTrue(evidencias.isEmpty());
    }

    @Test
    void notaQueJaSaiuDoCarregamentoNaoContaMesmoComLogDeEntrada() {
        List<Transferencia> evidencias = ConferenciaMotoristaCaminhaoWinThorService.transferenciasPosterioresAoMdfe(
                33288L, GERACAO_MDFE,
                List.of(t(1696853, 422186, 422810, LocalDateTime.of(2026, 11, 6, 14, 54, 45))),
                Map.of(1696853L, Set.of(33288L)),
                Set.of());

        assertTrue(evidencias.isEmpty(), "a nota foi para outro carregamento depois");
    }

    @Test
    void notaDeOutroManifestoNaoContaParaOManifestoEscolhido() {
        List<Transferencia> evidencias = ConferenciaMotoristaCaminhaoWinThorService.transferenciasPosterioresAoMdfe(
                40000L, GERACAO_MDFE,
                List.of(t(1696853, 422186, 422812, LocalDateTime.of(2026, 11, 10, 8, 30, 4))),
                Map.of(1696853L, Set.of(33288L)),
                Set.of(1696853L));

        assertTrue(evidencias.isEmpty(), "o manifesto escolhido é outro (ex.: um novo, aprovado)");
    }

    @Test
    void semDataDeGeracaoNaoAfirmaNada() {
        List<Transferencia> evidencias = ConferenciaMotoristaCaminhaoWinThorService.transferenciasPosterioresAoMdfe(
                33288L, null,
                List.of(t(1, 1, 2, LocalDateTime.now())), Map.of(1L, Set.of(33288L)), Set.of(1L));

        assertTrue(evidencias.isEmpty());
    }

    @Test
    void comparaMotoristaEVeiculo() {
        assertTrue(ConferenciaMotoristaCaminhaoWinThorService.motoristaDiferente(29, 378));
        assertFalse(ConferenciaMotoristaCaminhaoWinThorService.motoristaDiferente(378, 378));
        assertTrue(ConferenciaMotoristaCaminhaoWinThorService.veiculoDiferente("86", 253));
        assertFalse(ConferenciaMotoristaCaminhaoWinThorService.veiculoDiferente("253", 253));
        assertFalse(ConferenciaMotoristaCaminhaoWinThorService.veiculoDiferente(" 253 ", 253), "espaços não contam");
        assertTrue(ConferenciaMotoristaCaminhaoWinThorService.veiculoDiferente("ABC", 253));
    }

    // ------------------------------------------------------------------ fluxo completo (Oracle simulado)

    /** Um ResultSet de mentira que devolve os valores de um mapa e sabe responder wasNull(). */
    private static ResultSet linha(Map<String, Object> valores) throws Exception {
        ResultSet rs = Mockito.mock(ResultSet.class);
        AtomicReference<Object> ultimo = new AtomicReference<>();
        Mockito.when(rs.getInt(Mockito.anyString())).thenAnswer(i -> {
            Object v = valores.get(i.<String>getArgument(0));
            ultimo.set(v);
            return v == null ? 0 : ((Number) v).intValue();
        });
        Mockito.when(rs.getLong(Mockito.anyString())).thenAnswer(i -> {
            Object v = valores.get(i.<String>getArgument(0));
            ultimo.set(v);
            return v == null ? 0L : ((Number) v).longValue();
        });
        Mockito.when(rs.getString(Mockito.anyString())).thenAnswer(i -> (String) valores.get(i.<String>getArgument(0)));
        Mockito.when(rs.getTimestamp(Mockito.anyString())).thenAnswer(i -> {
            Object v = valores.get(i.<String>getArgument(0));
            return v == null ? null : Timestamp.valueOf((LocalDateTime) v);
        });
        Mockito.when(rs.wasNull()).thenAnswer(i -> ultimo.get() == null);
        return rs;
    }

    /** Faz o JdbcTemplate responder, por pedaço do SQL, com as linhas dadas (RowMapper ou RowCallbackHandler). */
    private static void responder(JdbcTemplate jdbc, String pedacoDoSql, List<Map<String, Object>> linhas) {
        Mockito.when(jdbc.query(Mockito.contains(pedacoDoSql), Mockito.any(Object[].class), Mockito.<RowMapper<Object>>any()))
                .thenAnswer(i -> {
                    RowMapper<Object> mapper = i.getArgument(2);
                    java.util.ArrayList<Object> resultado = new java.util.ArrayList<>();
                    int n = 0;
                    for (Map<String, Object> l : linhas) {
                        resultado.add(mapper.mapRow(linha(l), n++));
                    }
                    return resultado;
                });
        Mockito.doAnswer(i -> {
            RowCallbackHandler handler = i.getArgument(2);
            for (Map<String, Object> l : linhas) {
                handler.processRow(linha(l));
            }
            return null;
        }).when(jdbc).query(Mockito.contains(pedacoDoSql), Mockito.any(Object[].class), Mockito.any(RowCallbackHandler.class));
    }

    private ConferenciaMotoristaCaminhaoWinThorService servicoComOCaso422812(String codMotoristaDoCarregamento) {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        CargaWinThorService cargas = Mockito.mock(CargaWinThorService.class);
        Mockito.when(cargas.isMotoristaGenerico(Mockito.any())).thenAnswer(i -> Integer.valueOf(10007).equals(i.getArgument(0)));
        Mockito.when(cargas.isVeiculoGenerico(Mockito.any())).thenReturn(false);

        LocalDateTime em10 = LocalDateTime.of(2026, 11, 10, 8, 30, 4);
        responder(jdbc, "FROM pclogtransfnfcarreg", List.of(
                row("numnota", 1696853L, "anterior", 422810, "atual", 422812, "dttransf", em10, "motivo", "TRANSF. DE PEDIDO ENTRE CARGAS"),
                row("numnota", 1696878L, "anterior", 422811, "atual", 422812, "dttransf", em10, "motivo", "TRANSF. DE PEDIDO ENTRE CARGAS"),
                row("numnota", 1696879L, "anterior", 422186, "atual", 422812, "dttransf", em10, "motivo", "TRANSF. DE PEDIDO ENTRE CARGAS")));
        responder(jdbc, "FROM pccarreg", List.of(row("numcar", 422812, "codmot", Integer.parseInt(codMotoristaDoCarregamento), "codvei", 253)));
        responder(jdbc, "FROM pcnfsaid", List.of(
                row("numcar", 422812, "numnota", 1696853L),
                row("numcar", 422812, "numnota", 1696878L),
                row("numcar", 422812, "numnota", 1696879L)));
        responder(jdbc, "FROM pcmanifestoeletronicoc", List.of(row("nummdfe", 33288L, "geracao", GERACAO_MDFE)));
        responder(jdbc, "FROM pcmanifestoeletronicoi", List.of(
                row("numnota", 1696853L, "nummdfe", 33288L),
                row("numnota", 1696878L, "nummdfe", 33288L),
                row("numnota", 1696879L, "nummdfe", 33288L)));
        responder(jdbc, "FROM pcempr", List.of(
                row("codigo", 29, "nome", "JOSE HERCULANO"),
                row("codigo", 378, "nome", "MARTINHO")));
        responder(jdbc, "FROM pcveicul", List.of(
                row("codigo", 86, "placa", "QFF6539"),
                row("codigo", 253, "placa", "ABC1D23")));

        CargaWinThorDto manifesto = CargaWinThorDto.builder()
                .numCar(422812).numMdfe(33288L).situacaoMdfe("102")
                .codMotorista(29).nomeMotorista("JOSE HERCULANO").codVeiculo("86").placaVeiculo("QFF6539")
                .build();
        Mockito.when(cargas.buscarMdfeVigentePorCarregamento(Mockito.anyList())).thenReturn(Map.of(422812, manifesto));

        return new ConferenciaMotoristaCaminhaoWinThorService(jdbc, cargas);
    }

    @Test
    void casoReal422812ApareceComMotoristaECaminhaoDiferentes() {
        List<ConferenciaMotoristaCaminhaoItemDto> itens = servicoComOCaso422812("378")
                .buscar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null);

        assertEquals(1, itens.size());
        ConferenciaMotoristaCaminhaoItemDto item = itens.get(0);
        assertEquals(422812, item.getNumCar());
        assertEquals(33288L, item.getNumMdfe());
        assertEquals(GERACAO_MDFE, item.getDataGeracaoMdfe());
        assertEquals(29, item.getCodMotoristaMdfe());
        assertEquals("86", item.getCodVeiculoMdfe());
        assertEquals(378, item.getCodMotoristaCarregamento());
        assertEquals("MARTINHO", item.getNomeMotoristaCarregamento());
        assertEquals("253", item.getCodVeiculoCarregamento());
        assertEquals("ABC1D23", item.getPlacaCarregamento());
        assertTrue(item.isMotoristaDiferente());
        assertTrue(item.isVeiculoDiferente());
        assertEquals(3, item.getTransferencias().size());
    }

    @Test
    void motoristaIgualEVeiculoDiferenteMarcaSoOVeiculo() {
        List<ConferenciaMotoristaCaminhaoItemDto> itens = servicoComOCaso422812("29")
                .buscar(null, null, List.of(422812));

        assertEquals(1, itens.size());
        assertFalse(itens.get(0).isMotoristaDiferente());
        assertTrue(itens.get(0).isVeiculoDiferente());
    }

    @Test
    void carregamentoComMotoristaGenericoNaoApareceEmNenhumCaso() {
        List<ConferenciaMotoristaCaminhaoItemDto> itens = servicoComOCaso422812("10007")
                .buscar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null);

        assertTrue(itens.isEmpty(), "estacionamento não é viagem: nada a conferir");
    }

    @Test
    void semPeriodoNemCarregamentoNaoConsultaNada() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        ConferenciaMotoristaCaminhaoWinThorService service =
                new ConferenciaMotoristaCaminhaoWinThorService(jdbc, Mockito.mock(CargaWinThorService.class));

        assertTrue(service.buscar(null, null, List.of()).isEmpty());
        Mockito.verifyNoInteractions(jdbc);
    }
}

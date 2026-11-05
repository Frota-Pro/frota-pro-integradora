package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.DevolucaoNotaDto;
import br.com.frotasPro.integradora.winthor.dto.ManifestoSemNotaDto;
import br.com.frotasPro.integradora.winthor.dto.NotaConferenciaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.TransferenciaNotaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Entrega ao FrotaPRO, só pra leitura, as notas do WinThor de um período com as marcas que dizem
 * se cada uma "entra" como transportada (carregamento, pedido, MDF-e). É a base da conferência
 * mensal do valor transportado. Nenhuma consulta grava nada.
 *
 * <p>As consultas são pequenas e separadas de propósito (nota, MDF-e, carregamento, pedido) e o
 * cruzamento é feito em memória: subconsulta por nota dentro do SELECT trava o Oracle neste volume.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenciaNotasWinThorService {

    /** Passa de 3 meses já é consulta de relatório de outro tipo: evita pedir o banco inteiro por engano. */
    static final int MAXIMO_DIAS = 93;

    /** Carregamentos que têm ao menos uma nota emitida no período (parâmetros: inicio, fimExclusivo). */
    private static final String CARREGAMENTOS_DO_PERIODO = """
            SELECT y.numcar FROM pcnfsaid y
             WHERE y.codfilial = 1 AND y.numcar > 1
               AND y.dtsaida >= ? AND y.dtsaida < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ManifestosSemNotaWinThorService manifestosSemNota;

    public List<NotaConferenciaWinThorDto> buscarNotas(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);

        Date de = Date.valueOf(inicio);
        Date ate = Date.valueOf(fim.plusDays(1));

        log.info("Conferência do transportado: buscando notas do WinThor. inicio={} fim={}", inicio, fim);

        List<NotaConferenciaWinThorDto> notas = jdbcTemplate.query("""
                SELECT n.numnota, n.serie, n.especie, TRUNC(n.dtsaida) AS dtsaida, n.codcli, n.condvenda,
                       n.numcar, n.numtransvenda, n.vltotal,
                       CASE WHEN n.dtcancel IS NULL THEN 0 ELSE 1 END AS cancelada,
                       CASE WHEN NVL(n.codmotorista, 0) <> 0 AND NVL(n.codveiculo, 0) <> 0 THEN 1 ELSE 0 END AS mot_veic
                  FROM pcnfsaid n
                 WHERE n.codfilial = 1 AND n.dtsaida >= ? AND n.dtsaida < ?
                UNION
                SELECT n.numnota, n.serie, n.especie, TRUNC(n.dtsaida) AS dtsaida, n.codcli, n.condvenda,
                       n.numcar, n.numtransvenda, n.vltotal,
                       CASE WHEN n.dtcancel IS NULL THEN 0 ELSE 1 END AS cancelada,
                       CASE WHEN NVL(n.codmotorista, 0) <> 0 AND NVL(n.codveiculo, 0) <> 0 THEN 1 ELSE 0 END AS mot_veic
                  FROM pcnfsaid n
                 WHERE n.codfilial = 1 AND n.numcar IN (""" + CARREGAMENTOS_DO_PERIODO + ")",
                (rs, i) -> NotaConferenciaWinThorDto.builder()
                        .numNota(rs.getLong("numnota"))
                        .serie(rs.getString("serie"))
                        .especie(rs.getString("especie"))
                        .dtSaida(rs.getDate("dtsaida") != null ? rs.getDate("dtsaida").toLocalDate() : null)
                        .codCli(rs.getObject("codcli") != null ? rs.getInt("codcli") : null)
                        .condVenda(rs.getObject("condvenda") != null ? rs.getInt("condvenda") : null)
                        .numCar(rs.getObject("numcar") != null ? rs.getInt("numcar") : null)
                        .numTransVenda(rs.getObject("numtransvenda") != null ? rs.getLong("numtransvenda") : null)
                        .vlTotal(rs.getBigDecimal("vltotal") != null ? rs.getBigDecimal("vltotal") : BigDecimal.ZERO)
                        .cancelada(rs.getInt("cancelada") == 1)
                        .temMotoristaVeiculo(rs.getInt("mot_veic") == 1)
                        .build(),
                de, ate, de, ate);

        // MDF-e da filial 1 por nota. A ligação é só por número da nota, igual à da integradora que
        // monta as cargas: assim a conferência enxerga exatamente o que a sincronização enxerga.
        List<LinhaMdfe> linhasMdfe = jdbcTemplate.query("""
                SELECT i.numnota, m.nummdfe, m.codmotorista, m.codveiculo, m.situacaomdfe
                  FROM pcmanifestoeletronicoi i
                  JOIN pcmanifestoeletronicoc m ON m.nummdfe = i.nummdfe AND m.codfilial = 1
                 WHERE i.numnota IN (
                       SELECT x.numnota FROM pcnfsaid x
                        WHERE x.codfilial = 1 AND x.dtsaida >= ? AND x.dtsaida < ?
                       UNION
                       SELECT z.numnota FROM pcnfsaid z
                        WHERE z.codfilial = 1 AND z.numcar IN (""" + CARREGAMENTOS_DO_PERIODO + "))",
                (rs, i) -> new LinhaMdfe(
                        rs.getLong("numnota"),
                        rs.getLong("nummdfe"),
                        rs.getObject("codmotorista") != null ? rs.getInt("codmotorista") : null,
                        rs.getString("codveiculo"),
                        rs.getString("situacaomdfe")),
                de, ate, de, ate);
        Map<Long, MdfeInfo> comMdfe = escolherMdfePorNota(linhasMdfe);

        Set<Integer> comCarregamento = new HashSet<>(jdbcTemplate.query(
                "SELECT c.numcar FROM pccarreg c WHERE c.numcar IN (" + CARREGAMENTOS_DO_PERIODO + ")",
                (rs, i) -> rs.getInt("numcar"), de, ate));

        Set<Integer> comPedido = new HashSet<>(jdbcTemplate.query(
                "SELECT p.numcar FROM pcpedc p WHERE p.numcar IN (" + CARREGAMENTOS_DO_PERIODO + ") GROUP BY p.numcar",
                (rs, i) -> rs.getInt("numcar"), de, ate));

        List<NotaConferenciaWinThorDto> resultado = marcar(notas, comMdfe, comCarregamento, comPedido);
        log.info("Conferência do transportado: {} notas devolvidas ({} com MDF-e).", resultado.size(), comMdfe.size());
        return resultado;
    }

    /** Linha de PCMANIFESTOELETRONICOI/C: uma nota num manifesto, com quem consta nele. */
    record LinhaMdfe(long numNota, long numMdfe, Integer codMotorista, String codVeiculo, String situacao) {
    }

    /** O manifesto de uma nota: qual é e quem consta nele (motorista e caminhão). */
    record MdfeInfo(long numMdfe, Integer codMotorista, String codVeiculo) {
    }

    /**
     * Uma nota pode estar em mais de um MDF-e (reemissão). Vale o aprovado (situação 100) e, entre iguais,
     * o mais recente: a mesma escolha que a integradora faz ao montar a carga.
     */
    static Map<Long, MdfeInfo> escolherMdfePorNota(List<LinhaMdfe> linhas) {
        Map<Long, LinhaMdfe> escolhida = new HashMap<>();
        for (LinhaMdfe l : linhas) {
            escolhida.merge(l.numNota(), l, (atual, nova) -> {
                boolean atualOk = "100".equals(atual.situacao() == null ? null : atual.situacao().trim());
                boolean novaOk = "100".equals(nova.situacao() == null ? null : nova.situacao().trim());
                if (atualOk != novaOk) return novaOk ? nova : atual;
                return nova.numMdfe() >= atual.numMdfe() ? nova : atual;
            });
        }
        Map<Long, MdfeInfo> resultado = new HashMap<>();
        escolhida.forEach((nota, l) -> resultado.put(nota, new MdfeInfo(l.numMdfe(), l.codMotorista(), l.codVeiculo())));
        return resultado;
    }

    /** Manifestos da filial 1 sem nenhuma nota no período (ver {@link ManifestosSemNotaWinThorService}). */
    public List<ManifestoSemNotaDto> buscarManifestosSemNota(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        return manifestosSemNota.buscar(inicio, fim);
    }

    /** Devolução (valor e peso) das notas emitidas no período, somada de todos os produtos devolvidos. */
    public List<DevolucaoNotaDto> buscarDevolucoes(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);

        return jdbcTemplate.query("""
                SELECT n.numnota, SUM(d.vldevolucao) AS valor, SUM(d.totpeso) AS peso
                  FROM pcnfsaid n
                  JOIN view_devol_resumo_faturamento d ON d.numtransvenda = n.numtransvenda
                 WHERE n.codfilial = 1 AND n.dtsaida >= ? AND n.dtsaida < ?
                 GROUP BY n.numnota
                """,
                (rs, i) -> DevolucaoNotaDto.builder()
                        .numNota(rs.getLong("numnota"))
                        .valor(rs.getBigDecimal("valor"))
                        .pesoKg(rs.getBigDecimal("peso"))
                        .build(),
                Date.valueOf(inicio), Date.valueOf(fim.plusDays(1)));
    }

    /**
     * Log de transferência entre carregamentos (PCLOGTRANSFNFCARREG) das mesmas notas que {@link #buscarNotas}
     * devolve. Sem filtro de data no log: a nota é do período, mas ela pode ter trocado de carga depois dele.
     * O log liga pelo número da nota, igual à consulta de transferências já usada pela integradora.
     */
    public List<TransferenciaNotaDto> buscarTransferencias(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);

        Date de = Date.valueOf(inicio);
        Date ate = Date.valueOf(fim.plusDays(1));

        List<TransferenciaNotaDto> lista = jdbcTemplate.query("""
                SELECT t.numnota, t.numcaranterior, t.numcaratual, t.dttransf, t.motivotransf
                  FROM pclogtransfnfcarreg t
                 WHERE t.numnota IN (
                       SELECT x.numnota FROM pcnfsaid x
                        WHERE x.codfilial = 1 AND x.dtsaida >= ? AND x.dtsaida < ?
                       UNION
                       SELECT z.numnota FROM pcnfsaid z
                        WHERE z.codfilial = 1 AND z.numcar IN (""" + CARREGAMENTOS_DO_PERIODO + "))"
                + " ORDER BY t.dttransf, t.numnota",
                (rs, i) -> TransferenciaNotaDto.builder()
                        .numNota(rs.getLong("numnota"))
                        .numCarAnterior(rs.getObject("numcaranterior") != null ? rs.getInt("numcaranterior") : null)
                        .numCarAtual(rs.getObject("numcaratual") != null ? rs.getInt("numcaratual") : null)
                        .dtTransferencia(rs.getTimestamp("dttransf") != null ? rs.getTimestamp("dttransf").toLocalDateTime() : null)
                        .motivo(rs.getString("motivotransf"))
                        .build(),
                de, ate, de, ate);
        log.info("Conferência do transportado: {} transferências entre cargas no log.", lista.size());
        return lista;
    }

    /** Junta as marcas às notas; separado da consulta pra poder ser testado sem banco. */
    static List<NotaConferenciaWinThorDto> marcar(List<NotaConferenciaWinThorDto> notas,
                                                  Set<Long> comMdfe,
                                                  Set<Integer> comCarregamento,
                                                  Set<Integer> comPedido) {
        Map<Long, MdfeInfo> mapa = new HashMap<>();
        comMdfe.forEach(n -> mapa.put(n, new MdfeInfo(0L, null, null)));
        return marcar(notas, mapa, comCarregamento, comPedido);
    }

    static List<NotaConferenciaWinThorDto> marcar(List<NotaConferenciaWinThorDto> notas,
                                                  Map<Long, MdfeInfo> comMdfe,
                                                  Set<Integer> comCarregamento,
                                                  Set<Integer> comPedido) {
        List<NotaConferenciaWinThorDto> saida = new ArrayList<>(notas.size());
        for (NotaConferenciaWinThorDto n : notas) {
            Integer car = n.getNumCar();
            boolean temCarregamento = car != null && car > 1 && comCarregamento.contains(car);
            boolean temPedido = car != null && car > 1 && comPedido.contains(car);
            MdfeInfo mdfe = comMdfe.get(n.getNumNota());
            saida.add(NotaConferenciaWinThorDto.builder()
                    .numNota(n.getNumNota())
                    .serie(n.getSerie())
                    .especie(n.getEspecie())
                    .dtSaida(n.getDtSaida())
                    .codCli(n.getCodCli())
                    .condVenda(n.getCondVenda())
                    .numCar(n.getNumCar())
                    .numTransVenda(n.getNumTransVenda())
                    .vlTotal(n.getVlTotal())
                    .cancelada(n.isCancelada())
                    .temMotoristaVeiculo(n.isTemMotoristaVeiculo())
                    .temMdfe(mdfe != null)
                    .numMdfe(mdfe != null && mdfe.numMdfe() != 0 ? mdfe.numMdfe() : null)
                    .codMotoristaMdfe(mdfe != null ? mdfe.codMotorista() : null)
                    .codVeiculoMdfe(mdfe != null ? mdfe.codVeiculo() : null)
                    .temPedido(temPedido)
                    .temCarregamento(temCarregamento)
                    .build());
        }
        return saida;
    }

    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Informe o início e o fim do período.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser antes do início.");
        }
        if (inicio.plusDays(MAXIMO_DIAS).isBefore(fim)) {
            throw new IllegalArgumentException("Período grande demais: no máximo " + MAXIMO_DIAS + " dias por consulta.");
        }
    }
}

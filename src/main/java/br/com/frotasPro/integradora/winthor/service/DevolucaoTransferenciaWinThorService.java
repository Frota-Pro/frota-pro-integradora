package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.DevolucaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.TransferenciaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Detalhe (produto a produto / nota a nota) por trás dos resumos que já
 * viajam no sync de carga (CargaWinThorDto.codigosDevolucao/temTransferencia).
 * Consultado só sob demanda, quando o usuário abre o detalhe da carga —
 * nunca guardado em disco/banco, igual ao XML/PDF de nota fiscal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevolucaoTransferenciaWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<DevolucaoWinThorDto> buscarDevolucoes(Integer numCar) {
        String sql = """
            SELECT
                d.coddevol      AS codDevolucao,
                d.numnota       AS numNota,
                d.numped        AS numPedido,
                d.dtent         AS dtEntrada,
                d.motivo        AS motivo,
                d.codcli        AS codCliente,
                d.cliente       AS nomeCliente,
                d.codprod       AS codProduto,
                d.descricao     AS descricaoProduto,
                d.qt            AS quantidade,
                d.unidade       AS unidade,
                d.embalagem     AS embalagem,
                d.vldevolucao   AS valorDevolucao,
                d.totpeso       AS pesoTotalKg,
                d.fornecedor    AS fornecedor,
                d.nomemotorista AS nomeMotoristaDevolucao
            FROM pcnfsaid n
            JOIN view_devol_resumo_faturamento d ON d.numtransvenda = n.numtransvenda
            WHERE n.numcar = ?
            ORDER BY d.numnota, d.codprod
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapDevolucao(rs), numCar);
    }

    public List<TransferenciaWinThorDto> buscarTransferencias(Integer numCar) {
        String sql = """
            SELECT
                t.numnota       AS numNota,
                t.numcaratual   AS numCarAtual,
                t.numcaranterior AS numCarAnterior,
                t.dttransf      AS dtTransferencia,
                t.motivotransf  AS motivo,
                n.totpeso       AS pesoKg,
                n.vltotal       AS valorTotal
            FROM pclogtransfnfcarreg t
            LEFT JOIN pcnfsaid n ON n.numnota = t.numnota AND n.codfilial = 1
            WHERE t.numcaranterior = ? OR t.numcaratual = ?
            ORDER BY t.dttransf DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapTransferencia(rs, numCar), numCar, numCar);
    }

    private DevolucaoWinThorDto mapDevolucao(ResultSet rs) throws SQLException {
        return DevolucaoWinThorDto.builder()
                .codDevolucao(getInteger(rs, "codDevolucao"))
                .numNota(getLong(rs, "numNota"))
                .numPedido(getLong(rs, "numPedido"))
                .dtEntrada(toOffsetDateTime(rs.getTimestamp("dtEntrada")))
                .motivo(rs.getString("motivo"))
                .codCliente(getInteger(rs, "codCliente"))
                .nomeCliente(rs.getString("nomeCliente"))
                .codProduto(getInteger(rs, "codProduto"))
                .descricaoProduto(rs.getString("descricaoProduto"))
                .quantidade(rs.getBigDecimal("quantidade"))
                .unidade(rs.getString("unidade"))
                .embalagem(rs.getString("embalagem"))
                .valorDevolucao(rs.getBigDecimal("valorDevolucao"))
                .pesoTotalKg(getDouble(rs, "pesoTotalKg"))
                .fornecedor(rs.getString("fornecedor"))
                .nomeMotoristaDevolucao(rs.getString("nomeMotoristaDevolucao"))
                .build();
    }

    private TransferenciaWinThorDto mapTransferencia(ResultSet rs, Integer numCarConsultado) throws SQLException {
        Integer numCarAtual = getInteger(rs, "numCarAtual");
        Integer numCarAnterior = getInteger(rs, "numCarAnterior");

        String direcao = numCarConsultado.equals(numCarAnterior) ? "PERDIDA" : "RECEBIDA";

        return TransferenciaWinThorDto.builder()
                .numNota(getLong(rs, "numNota"))
                .numCarAtual(numCarAtual)
                .numCarAnterior(numCarAnterior)
                .dtTransferencia(toOffsetDateTime(rs.getTimestamp("dtTransferencia")))
                .motivo(rs.getString("motivo"))
                .direcao(direcao)
                .pesoKg(rs.getBigDecimal("pesoKg"))
                .valorTotal(rs.getBigDecimal("valorTotal"))
                .build();
    }

    private OffsetDateTime toOffsetDateTime(java.sql.Timestamp ts) {
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneId.systemDefault().getRules().getOffset(ts.toInstant()));
    }

    // O driver Oracle sempre devolve colunas NUMBER como BigDecimal via
    // getObject/ResultSetMetaData, mesmo quando o valor é logicamente um
    // inteiro — um cast direto pra Integer/Long/Double quebra com
    // ClassCastException. Sempre passar pelo BigDecimal primeiro.
    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(column);
        return valor != null ? valor.intValue() : null;
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(column);
        return valor != null ? valor.longValue() : null;
    }

    private Double getDouble(ResultSet rs, String column) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(column);
        return valor != null ? valor.doubleValue() : null;
    }
}

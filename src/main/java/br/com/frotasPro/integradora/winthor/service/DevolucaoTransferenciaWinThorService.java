package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.DevolucaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.TransferenciaWinThorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Detalhe (produto a produto / nota a nota) de devolução e transferência de
 * pedido de um carregamento, buscado ao vivo no WinThor — nada fica guardado
 * aqui. Complementa o resumo que já vai embutido na sincronização em lote
 * (CargaWinThorService.preencherDevolucaoETransferencia).
 */
@Service
@RequiredArgsConstructor
public class DevolucaoTransferenciaWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<DevolucaoWinThorDto> buscarDevolucoes(Integer numeroCarga) {
        String sql = """
            SELECT
                d.coddevol   AS codDevolucao,
                d.numnota    AS numNota,
                d.numped     AS numPedido,
                d.dtent      AS dtEntrada,
                d.motivo     AS motivo,
                d.codcli     AS codCliente,
                d.cliente    AS nomeCliente,
                d.codprod    AS codProduto,
                d.descricao  AS descricaoProduto,
                d.qt         AS quantidade,
                d.unidade    AS unidade,
                d.embalagem  AS embalagem,
                d.vldevolucao AS valorDevolucao,
                d.totpeso    AS pesoTotalKg,
                d.fornecedor AS fornecedor,
                d.nomemotorista AS nomeMotoristaDevolucao
            FROM
                pcnfsaid n
                JOIN view_devol_resumo_faturamento d
                    ON d.numnota = n.numnota
            WHERE
                n.numcar = ?
                AND d.coddevol IS NOT NULL
            ORDER BY
                d.dtent DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapDevolucao(rs), numeroCarga);
    }

    public List<TransferenciaWinThorDto> buscarTransferencias(Integer numeroCarga) {
        String sql = """
            SELECT
                numnota,
                numcaratual,
                numcaranterior,
                dttransf,
                motivotransf
            FROM
                pclogtransfnfcarreg
            WHERE
                numcaratual = ? OR numcaranterior = ?
            ORDER BY
                dttransf DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapTransferencia(rs, numeroCarga), numeroCarga, numeroCarga);
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

    private TransferenciaWinThorDto mapTransferencia(ResultSet rs, Integer numeroCargaConsultado) throws SQLException {
        Integer numCarAtual = getInteger(rs, "numcaratual");
        Integer numCarAnterior = getInteger(rs, "numcaranterior");

        String direcao = numeroCargaConsultado.equals(numCarAnterior) ? "PERDIDA" : "RECEBIDA";

        return TransferenciaWinThorDto.builder()
                .numNota(getLong(rs, "numnota"))
                .numCarAtual(numCarAtual)
                .numCarAnterior(numCarAnterior)
                .dtTransferencia(toOffsetDateTime(rs.getTimestamp("dttransf")))
                .motivo(rs.getString("motivotransf"))
                .direcao(direcao)
                .build();
    }

    private OffsetDateTime toOffsetDateTime(java.sql.Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toInstant().atOffset(ZoneId.systemDefault().getRules().getOffset(ts.toInstant()));
    }

    private Integer getInteger(ResultSet rs, String coluna) throws SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }

    private Long getLong(ResultSet rs, String coluna) throws SQLException {
        long valor = rs.getLong(coluna);
        return rs.wasNull() ? null : valor;
    }

    private Double getDouble(ResultSet rs, String coluna) throws SQLException {
        double valor = rs.getDouble(coluna);
        return rs.wasNull() ? null : valor;
    }
}

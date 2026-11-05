package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ManifestoSemNotaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manifestos (MDF-e) da filial 1 que não têm nenhuma nota: o caminhão saiu, mas não há valor, só o peso do
 * manifesto. Servem à conferência do transportado e entram no sync como cargas sem valor (viagem real, com km e
 * abastecimento lançados à mão). Só leitura.
 *
 * <p>As colunas de data e de peso do cabeçalho do MDF-e não estão no código: são descobertas no dicionário do
 * Oracle (ver {@link ColunasManifesto}). Sem coluna de data não há como filtrar o período e a lista vem vazia,
 * com aviso no log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManifestosSemNotaWinThorService {

    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");

    /** Mantém só os manifestos em situação válida (100 aprovado, 102 encerrado). Conjunto vazio = não filtra. */
    static List<ManifestoSemNotaDto> apenasValidos(List<ManifestoSemNotaDto> manifestos, Set<String> situacoesValidas) {
        if (situacoesValidas.isEmpty()) return manifestos;
        return manifestos.stream()
                .filter(m -> m.getSituacao() != null && situacoesValidas.contains(m.getSituacao().trim()))
                .toList();
    }

    /** Destino mostrado na carga de manifesto sem nota (o manifesto não tem cliente nem cidade de entrega). */
    static final String DESTINO_SEM_NOTA = "MANIFESTO SEM NOTA";

    private final JdbcTemplate jdbcTemplate;

    public List<ManifestoSemNotaDto> buscar(LocalDate inicio, LocalDate fim) {
        Set<String> colunas = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM all_tab_columns WHERE table_name = 'PCMANIFESTOELETRONICOC'", String.class));
        String colunaData = ColunasManifesto.colunaData(colunas).filter(c -> IDENTIFICADOR.matcher(c).matches()).orElse(null);
        String colunaPeso = ColunasManifesto.colunaPeso(colunas).filter(c -> IDENTIFICADOR.matcher(c).matches()).orElse(null);
        if (colunaData == null) {
            log.warn("Manifestos sem nota: nenhuma coluna de data reconhecida em PCMANIFESTOELETRONICOC (colunas: {}).", colunas);
            return List.of();
        }

        String sql = "SELECT m.nummdfe, m.codmotorista, e.nome AS nomemotorista, m.codveiculo, v.placa, m.situacaomdfe,"
                + " m." + colunaData + " AS dt" + (colunaPeso != null ? ", m." + colunaPeso + " AS peso" : ", NULL AS peso")
                + " FROM pcmanifestoeletronicoc m"
                + " LEFT JOIN pcmanifestoeletronicoi i ON i.nummdfe = m.nummdfe"
                + " LEFT JOIN pcempr e ON e.matricula = m.codmotorista AND e.tipo = 'M'"
                + " LEFT JOIN pcveicul v ON v.codveiculo = m.codveiculo"
                + " WHERE m.codfilial = 1 AND i.nummdfe IS NULL AND m." + colunaData + " >= ? AND m." + colunaData + " < ?"
                + " ORDER BY m.nummdfe";

        List<ManifestoSemNotaDto> lista = jdbcTemplate.query(sql,
                (rs, n) -> ManifestoSemNotaDto.builder()
                        .numMdfe(rs.getLong("nummdfe"))
                        .codMotorista(rs.getObject("codmotorista") != null ? rs.getInt("codmotorista") : null)
                        .nomeMotorista(rs.getString("nomemotorista"))
                        .codVeiculo(rs.getString("codveiculo"))
                        .placa(rs.getString("placa"))
                        .situacao(rs.getString("situacaomdfe"))
                        .data(rs.getTimestamp("dt") != null ? rs.getTimestamp("dt").toLocalDateTime().toLocalDate() : null)
                        .pesoKg(rs.getBigDecimal("peso"))
                        .build(),
                Date.valueOf(inicio), Date.valueOf(fim.plusDays(1)));
        log.info("Manifestos sem nota: {} no período (data={}, peso={}).", lista.size(), colunaData, colunaPeso);
        return lista;
    }

    /**
     * Manifesto sem nota como carga sem valor, para o sync: não tem numCar (a chave é o número do manifesto),
     * não tem cliente nem nota, e o peso é o do manifesto. Só vale manifesto em situação válida (100 aprovado,
     * 102 encerrado); cancelado e rejeitado pela SEFAZ ficam de fora. Conjunto vazio = não filtra.
     */
    static List<CargaWinThorDto> comoCargas(List<ManifestoSemNotaDto> manifestos, Set<String> situacoesValidas,
                                            List<Integer> codigosCaminhoes, List<Integer> codigosMotoristas) {
        List<CargaWinThorDto> cargas = new ArrayList<>();
        for (ManifestoSemNotaDto m : manifestos) {
            if (m.getNumMdfe() == null || m.getCodVeiculo() == null || m.getCodMotorista() == null || m.getData() == null) continue;
            String situacao = m.getSituacao() == null ? "" : m.getSituacao().trim();
            if (!situacoesValidas.isEmpty() && !situacoesValidas.contains(situacao)) continue;
            if (!codigosCaminhoes.isEmpty() && !codigosCaminhoes.stream().map(String::valueOf).toList().contains(m.getCodVeiculo().trim())) continue;
            if (!codigosMotoristas.isEmpty() && !codigosMotoristas.contains(m.getCodMotorista())) continue;

            var instante = m.getData().atStartOfDay(ZoneId.systemDefault()).toInstant();
            OffsetDateTime dtSaida = instante.atOffset(ZoneId.systemDefault().getRules().getOffset(instante));
            cargas.add(CargaWinThorDto.builder()
                    .numMdfe(m.getNumMdfe())
                    .numCar(null)
                    .semNota(true)
                    .codVeiculo(m.getCodVeiculo().trim())
                    .codMotorista(m.getCodMotorista())
                    .nomeMotorista(m.getNomeMotorista())
                    .placaVeiculo(m.getPlaca())
                    .dtSaida(dtSaida)
                    .destino(DESTINO_SEM_NOTA)
                    // pesoTotalKg carrega TONELADAS (unidade de Carga.pesoCarga); o peso do manifesto é lido em kg
                    .pesoTotalKg(m.getPesoKg() == null ? null : m.getPesoKg().divide(java.math.BigDecimal.valueOf(1000)).doubleValue())
                    .valorTotal(null)
                    .situacaoMdfe(m.getSituacao())
                    .totalClientes(0)
                    .clientes(new ArrayList<>())
                    .origemCarga("MANIFESTO_SEM_NOTA")
                    .notasTransportadas(0)
                    .somenteTv10Terceiro(false)
                    .build());
        }
        return cargas;
    }
}

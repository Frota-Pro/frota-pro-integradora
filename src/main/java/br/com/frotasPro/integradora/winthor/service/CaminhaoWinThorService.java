package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CaminhaoWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaminhaoWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<CaminhaoWinThorDto> buscarCaminhoes(Integer codFilial, List<Integer> codigosCaminhoes) {

        List<Integer> codigosFiltro = codigosCaminhoes == null
                ? List.of()
                : codigosCaminhoes.stream().filter(java.util.Objects::nonNull).distinct().toList();

        StringBuilder sql = new StringBuilder("""
            SELECT
                v.codveiculo   AS codVeiculo,
                v.placa        AS placa,
                v.descricao    AS descricao,
                v.marca        AS marca,
                v.pesocargakg  AS pesoMaximoKg,
                v.situacao     AS situacao
            FROM pcveicul v
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (!codigosFiltro.isEmpty()) {
            sql.append(" AND v.codveiculo IN (");
            sql.append("?,".repeat(codigosFiltro.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(codigosFiltro);
        }

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                CaminhaoWinThorDto.builder()
                        .codVeiculo(rs.getInt("codVeiculo"))
                        .placa(rs.getString("placa"))
                        .descricao(rs.getString("descricao"))
                        .marca(rs.getString("marca"))
                        .pesoMaximoKg(rs.getBigDecimal("pesoMaximoKg"))
                        .situacao(rs.getString("situacao"))
                        .build()
        );
    }
}

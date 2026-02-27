package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.MotoristaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotoristaWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<MotoristaWinThorDto> buscarMotoristas(Integer codFilial, List<Integer> codigosMotoristas) {

        List<Integer> codigosFiltro = codigosMotoristas == null
                ? List.of()
                : codigosMotoristas.stream().filter(Objects::nonNull).distinct().toList();

        log.info("Buscando motoristas no WinThor. codFilial={}", codFilial);

        StringBuilder sql = new StringBuilder("""
            SELECT 
                e.matricula      AS codigoExterno,
                e.nome           AS nome,
                e.cpf            AS cpf,
                e.situacao       AS situacao
            FROM pcempr e
            WHERE 
                e.tipo = 'M'
                AND (? = 0 OR e.codfilial = ?)
            """);

        List<Object> params = new ArrayList<>();
        int codFilialFiltro = codFilial == null ? 0 : codFilial;
        params.add(codFilialFiltro);
        params.add(codFilialFiltro);

        if (!codigosFiltro.isEmpty()) {
            sql.append(" AND e.matricula IN (");
            sql.append("?,".repeat(codigosFiltro.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(codigosFiltro);
        }

        return jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                (rs, rowNum) -> MotoristaWinThorDto.builder()
                        .codigoExterno(rs.getInt("codigoExterno"))
                        .nome(rs.getString("nome"))
                        .cpf(rs.getString("cpf"))
                        .ativo("A".equalsIgnoreCase(rs.getString("situacao")))
                        .build()
        );
    }
}

package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.MotoristaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotoristaWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<MotoristaWinThorDto> buscarMotoristas(Integer codFilial) {

        log.info("Buscando motoristas no WinThor. codFilial={}", codFilial);

        String sql = """
            SELECT 
                e.matricula      AS codigoExterno,
                e.nome           AS nome,
                e.cpf            AS cpf,
                e.situacao       AS situacao
            FROM pcempr e
            WHERE 
                e.tipo = 'M'
                AND (e.codfilial = ? OR ? IS NULL)
            """;

        return jdbcTemplate.query(
                sql,
                ps -> {
                    if (codFilial != null) {
                        ps.setInt(1, codFilial);
                        ps.setInt(2, codFilial);
                    } else {
                        ps.setNull(1, java.sql.Types.INTEGER);
                        ps.setNull(2, java.sql.Types.INTEGER);
                    }
                },
                (rs, rowNum) -> MotoristaWinThorDto.builder()
                        .codigoExterno(rs.getInt("codigoExterno"))
                        .nome(rs.getString("nome"))
                        .cpf(rs.getString("cpf"))
                        .ativo("A".equalsIgnoreCase(rs.getString("situacao")))
                        .build()
        );
    }
}

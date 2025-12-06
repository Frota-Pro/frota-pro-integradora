package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ClienteCargaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CargaWinThorService {

    private final JdbcTemplate jdbcTemplate;

    public List<CargaWinThorDto> buscarCargasFaturadas(UUID empresaId,
                                                       LocalDate dataInicial,
                                                       LocalDate dataFinal) {

        log.info("Buscando cargas faturadas no WinThor. empresaId={} dataInicial={} dataFinal={}",
                empresaId, dataInicial, dataFinal);

        String sqlCargas = """
            SELECT 
                m.nummdfe                    AS numMdfe,
                n.numcar                     AS numCar,
                m.codmotorista               AS codMotorista,
                m.codveiculo                 AS codVeiculo,
                c.numnotas                   AS numNotas,
                MAX(n.dtsaida)               AS dtSaida,
                c.totpeso / 1000.0           AS totalPeso,
                SUM(n.vltotal)               AS valorTotal,
                m.situacaomdfe               AS situacaoMdfe,
                c.destino                    AS destino,
                COUNT(DISTINCT p.codcli)     AS totalClientes
            FROM 
                pcnfsaid n
                JOIN pcpedc p 
                    ON n.numcar = p.numcar
                JOIN pcmanifestoeletronicoi i 
                    ON n.numnota = i.numnota
                JOIN pcmanifestoeletronicoc m 
                    ON i.nummdfe = m.nummdfe
                JOIN pcveicul v 
                    ON m.codveiculo = v.codveiculo
                JOIN pcempr e 
                    ON m.codmotorista = e.matricula 
                   AND e.tipo = 'M'
                JOIN pccarreg c 
                    ON n.numcar = c.numcar
            WHERE 
                TRUNC(n.dtsaida) BETWEEN TRUNC(?) AND TRUNC(?)
                AND n.codmotorista IS NOT NULL
                AND n.codveiculo   IS NOT NULL
                AND n.codmotorista <> 0
                AND n.codveiculo   <> 0
                AND n.codfilial    = 1
                AND m.codfilial    = 1
            GROUP BY 
                m.nummdfe,
                n.numcar,
                m.codmotorista,
                m.codveiculo,
                c.numnotas,
                c.totpeso,
                m.situacaomdfe,
                c.destino
            ORDER BY 
                m.nummdfe
            """;

        List<CargaWinThorDto> cargas = jdbcTemplate.query(
                sqlCargas,
                ps -> {
                    ps.setDate(1, java.sql.Date.valueOf(dataInicial));
                    ps.setDate(2, java.sql.Date.valueOf(dataFinal));
                },
                (rs, rowNum) -> mapCarga(rs)
        );

        if (cargas.isEmpty()) {
            log.info("Nenhuma carga encontrada no WinThor para o período.");
            return cargas;
        }

        String sqlClientes = """
            SELECT 
                m.nummdfe                    AS numMdfe,
                n.numcar                     AS numCar,
                n.codcli                     AS codCli,
                cli.cliente                  AS nomeCli,
                LISTAGG(DISTINCT n.numnota, ',') 
                    WITHIN GROUP (ORDER BY n.numnota) AS notas
            FROM 
                pcnfsaid n
                JOIN pcmanifestoeletronicoi i 
                    ON n.numnota = i.numnota
                JOIN pcmanifestoeletronicoc m 
                    ON i.nummdfe = m.nummdfe
                JOIN pcveicul v 
                    ON m.codveiculo = v.codveiculo
                JOIN pcempr e 
                    ON m.codmotorista = e.matricula 
                   AND e.tipo = 'M'
                JOIN pccarreg c 
                    ON n.numcar = c.numcar
                JOIN pcclient cli
                    ON cli.codcli = n.codcli
            WHERE 
                TRUNC(n.dtsaida) BETWEEN TRUNC(?) AND TRUNC(?)
                AND n.codmotorista IS NOT NULL
                AND n.codveiculo   IS NOT NULL
                AND n.codmotorista <> 0
                AND n.codveiculo   <> 0
                AND n.codfilial    = 1
                AND m.codfilial    = 1
            GROUP BY 
                m.nummdfe,
                n.numcar,
                n.codcli,
                cli.cliente
            ORDER BY 
                m.nummdfe,
                n.numcar,
                nomeCli
            """;

        List<ClienteRow> clienteRows = jdbcTemplate.query(
                sqlClientes,
                ps -> {
                    ps.setDate(1, java.sql.Date.valueOf(dataInicial));
                    ps.setDate(2, java.sql.Date.valueOf(dataFinal));
                },
                (rs, rowNum) -> mapClienteRow(rs)
        );

        Map<String, List<ClienteCargaWinThorDto>> clientesPorCarga =
                clienteRows.stream()
                        .collect(Collectors.groupingBy(
                                ClienteRow::chave,
                                Collectors.mapping(ClienteRow::toDto, Collectors.toList())
                        ));

        cargas.forEach(c -> {
            String chave = c.getNumMdfe() + ":" + c.getNumCar();
            List<ClienteCargaWinThorDto> clientes = clientesPorCarga.getOrDefault(chave, Collections.emptyList());
            c.setClientes(clientes);
            c.setTotalClientes(clientes.size());
        });

        return cargas;
    }

    private CargaWinThorDto mapCarga(ResultSet rs) throws SQLException {
        Long numMdfe        = rs.getLong("numMdfe");
        Integer numCar      = rs.getInt("numCar");
        Integer codMot      = rs.getInt("codMotorista");
        String codVeiculo   = rs.getString("codVeiculo");
        java.sql.Timestamp tsSaida = rs.getTimestamp("dtSaida");

        OffsetDateTime dtSaida = tsSaida != null
                ? tsSaida.toInstant().atOffset(ZoneId.systemDefault().getRules().getOffset(tsSaida.toInstant()))
                : null;

        Double totalPeso    = rs.getDouble("totalPeso");
        BigDecimal valorTotal  = rs.getBigDecimal("valorTotal");
        String situacaoMdfe = rs.getString("situacaoMdfe");
        String destino      = rs.getString("destino");
        Integer totalCli    = rs.getInt("totalClientes");

        return CargaWinThorDto.builder()
                .numMdfe(numMdfe)
                .numCar(numCar)
                .codMotorista(codMot)
                .codVeiculo(codVeiculo)
                .dtSaida(dtSaida)
                .pesoTotalKg(totalPeso)
                .valorTotal(valorTotal)
                .situacaoMdfe(situacaoMdfe)
                .destino(destino)
                .totalClientes(totalCli)
                .clientes(new ArrayList<>())
                .build();
    }

    private ClienteRow mapClienteRow(ResultSet rs) throws SQLException {
        long numMdfe    = rs.getLong("numMdfe");
        int numCar      = rs.getInt("numCar");
        int codCli      = rs.getInt("codCli");
        String nomeCli  = rs.getString("nomeCli");
        String notasStr = rs.getString("notas");

        List<Long> notas = notasStr == null || notasStr.isBlank()
                ? Collections.emptyList()
                : Arrays.stream(notasStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toList());

        return new ClienteRow(numMdfe, numCar, codCli, nomeCli, notas);
    }

    private record ClienteRow(long numMdfe, int numCar, int codCli,
                              String nomeCli, List<Long> notas) {

        String chave() {
            return numMdfe + ":" + numCar;
        }

        ClienteCargaWinThorDto toDto() {
            return ClienteCargaWinThorDto.builder()
                    .codCli(codCli)
                    .nomeCli(nomeCli)
                    .notas(notas)
                    .build();
        }
    }
}

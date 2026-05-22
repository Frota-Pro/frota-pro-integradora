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
                                                       LocalDate dataFinal,
                                                       List<Integer> codigosCargas,
                                                       List<Integer> codigosCaminhoes,
                                                       List<Integer> codigosMotoristas) {

        List<Integer> codigosCargasFiltro = normalizarCodigos(codigosCargas);
        List<Integer> codigosCaminhoesFiltro = normalizarCodigos(codigosCaminhoes);
        List<Integer> codigosMotoristasFiltro = normalizarCodigos(codigosMotoristas);

        log.info("Buscando cargas faturadas no WinThor. empresaId={} dataInicial={} dataFinal={} codigosCargas={}",
                empresaId, dataInicial, dataFinal, codigosCargasFiltro);

        List<CargaWinThorDto> cargasPorData = buscarCargasPorPeriodo(
                dataInicial,
                dataFinal,
                codigosCaminhoesFiltro,
                codigosMotoristasFiltro
        );

        List<CargaWinThorDto> cargasPorCodigo = codigosCargasFiltro.isEmpty()
                ? List.of()
                : buscarCargasPorCodigos(
                        codigosCargasFiltro,
                        codigosCaminhoesFiltro,
                        codigosMotoristasFiltro
                );

        Map<Integer, CargaWinThorDto> cargasPorNumCar = new LinkedHashMap<>();
        cargasPorData.stream()
                .filter(carga -> carga.getNumCar() != null)
                .forEach(carga -> cargasPorNumCar.putIfAbsent(carga.getNumCar(), carga));
        cargasPorCodigo.stream()
                .filter(carga -> carga.getNumCar() != null)
                .forEach(carga -> cargasPorNumCar.putIfAbsent(carga.getNumCar(), carga));

        List<CargaWinThorDto> cargas = new ArrayList<>(cargasPorNumCar.values());

        if (cargas.isEmpty()) {
            log.info("Nenhuma carga encontrada no WinThor para o período/códigos informados.");
            return cargas;
        }

        preencherClientes(cargas);

        return cargas;
    }

    private List<Integer> normalizarCodigos(List<Integer> codigos) {
        return codigos == null
                ? List.of()
                : codigos.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<CargaWinThorDto> buscarCargasPorPeriodo(LocalDate dataInicial,
                                                         LocalDate dataFinal,
                                                         List<Integer> codigosCaminhoesFiltro,
                                                         List<Integer> codigosMotoristasFiltro) {
        List<Object> parametros = new ArrayList<>();
        parametros.add(java.sql.Date.valueOf(dataInicial));
        parametros.add(java.sql.Date.valueOf(dataFinal));

        return buscarCargas(
                "TRUNC(n.dtsaida) BETWEEN TRUNC(?) AND TRUNC(?)",
                parametros,
                codigosCaminhoesFiltro,
                codigosMotoristasFiltro
        );
    }

    private List<CargaWinThorDto> buscarCargasPorCodigos(List<Integer> codigosCargasFiltro,
                                                         List<Integer> codigosCaminhoesFiltro,
                                                         List<Integer> codigosMotoristasFiltro) {
        List<Object> parametros = new ArrayList<>();

        StringBuilder filtroCodigos = new StringBuilder("n.numcar IN (");
        filtroCodigos.append("?,".repeat(codigosCargasFiltro.size()));
        filtroCodigos.setLength(filtroCodigos.length() - 1);
        filtroCodigos.append(")");
        parametros.addAll(codigosCargasFiltro);

        return buscarCargas(
                filtroCodigos.toString(),
                parametros,
                codigosCaminhoesFiltro,
                codigosMotoristasFiltro
        );
    }

    private List<CargaWinThorDto> buscarCargas(String filtroPrincipal,
                                               List<Object> parametrosCargas,
                                               List<Integer> codigosCaminhoesFiltro,
                                               List<Integer> codigosMotoristasFiltro) {
        StringBuilder sqlCargas = new StringBuilder("""
            SELECT 
                m.nummdfe                    AS numMdfe,
                n.numcar                     AS numCar,
                m.codmotorista               AS codMotorista,
                m.codveiculo                 AS codVeiculo,
                c.numnotas                   AS numNotas,
                MAX(n.dtsaida)               AS dtSaida,
                c.totpeso / 1000.0           AS totalPeso,
        
                -- soma correta, sem duplicar pelos JOINs
                (
                    SELECT SUM(v.vltotal)
                    FROM pcnfsaid v
                    WHERE v.numcar = n.numcar
                )                            AS valorTotal,
        
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
            """);

        sqlCargas.append("                ").append(filtroPrincipal).append("\n");
        sqlCargas.append("""
                AND n.codmotorista IS NOT NULL
                AND n.codveiculo   IS NOT NULL
                AND n.codmotorista <> 0
                AND n.codveiculo   <> 0
                AND n.codfilial    = 1
                AND m.codfilial    = 1
            """);

        adicionarFiltroIn(sqlCargas, parametrosCargas, "m.codveiculo", codigosCaminhoesFiltro);
        adicionarFiltroIn(sqlCargas, parametrosCargas, "m.codmotorista", codigosMotoristasFiltro);

        sqlCargas.append("""
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
            """);

        return jdbcTemplate.query(
                sqlCargas.toString(),
                parametrosCargas.toArray(),
                (rs, rowNum) -> mapCarga(rs)
        );
    }

    private void preencherClientes(List<CargaWinThorDto> cargas) {
        List<Integer> codigosCargas = cargas.stream()
                .map(CargaWinThorDto::getNumCar)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        StringBuilder sqlClientes = new StringBuilder("""
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
                n.codmotorista IS NOT NULL
                AND n.codveiculo   IS NOT NULL
                AND n.codmotorista <> 0
                AND n.codveiculo   <> 0
                AND n.codfilial    = 1
                AND m.codfilial    = 1
            """);

        List<Object> parametrosClientes = new ArrayList<>();
        adicionarFiltroIn(sqlClientes, parametrosClientes, "n.numcar", codigosCargas);

        sqlClientes.append("""
            GROUP BY 
                m.nummdfe,
                n.numcar,
                n.codcli,
                cli.cliente
            ORDER BY 
                m.nummdfe,
                n.numcar,
                nomeCli
            """);

        List<ClienteRow> clienteRows = jdbcTemplate.query(
                sqlClientes.toString(),
                parametrosClientes.toArray(),
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
    }

    private void adicionarFiltroIn(StringBuilder sql, List<Object> parametros, String coluna, List<Integer> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(coluna).append(" IN (");
        sql.append("?,".repeat(codigos.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        parametros.addAll(codigos);
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

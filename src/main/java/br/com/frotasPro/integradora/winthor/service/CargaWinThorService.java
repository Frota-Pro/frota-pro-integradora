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

    // Código de situação do MDF-e confirmado no WinThor (tela 1455) como "Aprovado".
    private static final String SITUACAO_MDFE_APROVADO = "100";

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

        // Um carregamento (numCar) pode ter mais de um MDF-e emitido (ex: reemissão
        // trocando motorista/veículo). Preferimos o MDF-e com situação "100 - Aprovado"
        // como fonte de motorista/veículo/peso/valor daquele carregamento; entre MDF-e's
        // com o mesmo status, fica com o mais recente (maior numMdfe).
        Map<Integer, CargaWinThorDto> cargasPorNumCar = new LinkedHashMap<>();
        java.util.stream.Stream.concat(cargasPorData.stream(), cargasPorCodigo.stream())
                .filter(carga -> carga.getNumCar() != null)
                .forEach(carga -> cargasPorNumCar.merge(
                        carga.getNumCar(),
                        carga,
                        this::preferirMdfeVigente
                ));

        List<CargaWinThorDto> cargas = new ArrayList<>(cargasPorNumCar.values());

        if (cargas.isEmpty()) {
            log.info("Nenhuma carga encontrada no WinThor para o período/códigos informados.");
            return cargas;
        }

        preencherClientes(cargas);
        preencherDevolucaoETransferencia(cargas);

        return cargas;
    }

    /**
     * Resumo (não o detalhe) de devolução/transferência por carregamento —
     * usado pela frota-pro-api só pra decidir se uma diminuição de peso/valor
     * tem motivo reconhecido, e pra mostrar o badge na tela. O detalhe
     * produto-a-produto/nota-a-nota é buscado sob demanda, ver
     * DevolucaoTransferenciaController.
     */
    private void preencherDevolucaoETransferencia(List<CargaWinThorDto> cargas) {
        List<Integer> codigosCargas = cargas.stream()
                .map(CargaWinThorDto::getNumCar)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, List<String>> devolucoesPorNumCar = buscarCodigosDevolucaoPorNumCar(codigosCargas);
        Set<Integer> comTransferencia = buscarNumCarComTransferenciaPerdida(codigosCargas);

        cargas.forEach(c -> {
            c.setCodigosDevolucao(devolucoesPorNumCar.getOrDefault(c.getNumCar(), List.of()));
            c.setTemTransferencia(comTransferencia.contains(c.getNumCar()));
        });
    }

    private Map<Integer, List<String>> buscarCodigosDevolucaoPorNumCar(List<Integer> codigosCargas) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT
                n.numcar     AS numCar,
                d.coddevol   AS codDevol
            FROM
                pcnfsaid n
                JOIN view_devol_resumo_faturamento d
                    ON d.numnota = n.numnota
            WHERE
                d.coddevol IS NOT NULL
            """);

        List<Object> parametros = new ArrayList<>();
        adicionarFiltroIn(sql, parametros, "n.numcar", codigosCargas);

        return jdbcTemplate.query(sql.toString(), parametros.toArray(), rs -> {
            Map<Integer, List<String>> resultado = new HashMap<>();
            while (rs.next()) {
                int numCar = rs.getInt("numCar");
                String codDevol = String.valueOf(rs.getInt("codDevol"));
                resultado.computeIfAbsent(numCar, k -> new ArrayList<>()).add(codDevol);
            }
            return resultado;
        });
    }

    private Set<Integer> buscarNumCarComTransferenciaPerdida(List<Integer> codigosCargas) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT numcaranterior AS numCar
            FROM pclogtransfnfcarreg
            WHERE numcaranterior IS NOT NULL
            """);

        List<Object> parametros = new ArrayList<>();
        adicionarFiltroIn(sql, parametros, "numcaranterior", codigosCargas);

        List<Integer> numCars = jdbcTemplate.query(
                sql.toString(),
                parametros.toArray(),
                (rs, rowNum) -> rs.getInt("numCar")
        );
        return new HashSet<>(numCars);
    }

    private CargaWinThorDto preferirMdfeVigente(CargaWinThorDto atual, CargaWinThorDto candidato) {
        boolean atualAprovado = isAprovado(atual);
        boolean candidatoAprovado = isAprovado(candidato);

        if (candidatoAprovado != atualAprovado) {
            return candidatoAprovado ? candidato : atual;
        }
        return candidato.getNumMdfe() >= atual.getNumMdfe() ? candidato : atual;
    }

    private boolean isAprovado(CargaWinThorDto dto) {
        return dto.getSituacaoMdfe() != null
                && SITUACAO_MDFE_APROVADO.equals(dto.getSituacaoMdfe().trim());
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
                cid.nomecidade                AS cidade,
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
                LEFT JOIN pccidade cid
                    ON cid.codcidade = cli.codcidade
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
                cli.cliente,
                cid.nomecidade
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

        // Agrupa por numCar (carregamento), não por MDF-e: um carregamento com mais de
        // um MDF-e tem clientes/notas espalhados entre eles, e todos pertencem ao mesmo
        // carregamento. Clientes repetidos entre MDF-e's diferentes têm as notas unidas.
        Map<Integer, Map<Integer, ClienteCargaWinThorDto>> clientesPorNumCar = new HashMap<>();
        for (ClienteRow row : clienteRows) {
            Map<Integer, ClienteCargaWinThorDto> clientesDoCarregamento =
                    clientesPorNumCar.computeIfAbsent(row.numCar(), k -> new LinkedHashMap<>());

            clientesDoCarregamento.merge(
                    row.codCli(),
                    row.toDto(),
                    (existente, novo) -> {
                        Set<Long> notasUnidas = new LinkedHashSet<>(existente.getNotas());
                        notasUnidas.addAll(novo.getNotas());
                        return ClienteCargaWinThorDto.builder()
                                .codCli(existente.getCodCli())
                                .nomeCli(existente.getNomeCli())
                                .cidade(existente.getCidade() != null ? existente.getCidade() : novo.getCidade())
                                .notas(new ArrayList<>(notasUnidas))
                                .build();
                    }
            );
        }

        cargas.forEach(c -> {
            List<ClienteCargaWinThorDto> clientes = new ArrayList<>(
                    clientesPorNumCar.getOrDefault(c.getNumCar(), Map.of()).values()
            );
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
        int numCar      = rs.getInt("numCar");
        int codCli      = rs.getInt("codCli");
        String nomeCli  = rs.getString("nomeCli");
        String cidade   = rs.getString("cidade");
        String notasStr = rs.getString("notas");

        List<Long> notas = notasStr == null || notasStr.isBlank()
                ? Collections.emptyList()
                : Arrays.stream(notasStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toList());

        return new ClienteRow(numCar, codCli, nomeCli, cidade, notas);
    }

    private record ClienteRow(int numCar, int codCli, String nomeCli, String cidade, List<Long> notas) {

        ClienteCargaWinThorDto toDto() {
            return ClienteCargaWinThorDto.builder()
                    .codCli(codCli)
                    .nomeCli(nomeCli)
                    .cidade(cidade)
                    .notas(notas)
                    .build();
        }
    }
}

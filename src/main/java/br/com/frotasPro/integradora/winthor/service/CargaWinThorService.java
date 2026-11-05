package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ClienteCargaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** Máximo de itens num IN do Oracle (o limite do banco é 1000). */
    private static final int TAMANHO_LOTE_IN = 900;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Liga o cálculo do valor/peso/data da carga pelas notas transportadas (não canceladas e com MDF-e).
     * Desligado, a integradora volta a somar todas as notas do carregamento, como antes.
     */
    @Value("${frotapro.sync.valor-pelas-notas-transportadas:true}")
    private boolean valorPelasNotasTransportadas = true;

    /** Tipos de venda e clientes cujo transporte é de terceiro (TV10 dos clientes 15575 e 10073). */
    @Value("${frotapro.sync.tv-terceiro:10}")
    private List<Integer> tvTerceiro = List.of(10);

    @Value("${frotapro.sync.clientes-terceiro:15575,10073}")
    private List<Integer> clientesTerceiro = List.of(15575, 10073);

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
        preencherMotivosAlteracao(cargas);
        aplicarTotaisDasNotasTransportadas(cargas);

        return cargas;
    }

    /**
     * Troca o valor, o peso e a data da carga pelos das NOTAS TRANSPORTADAS do carregamento (não canceladas
     * e dentro de um MDF-e da filial 1), em qualquer mês. Antes a carga somava todas as notas do carregamento,
     * inclusive cancelada e sem manifesto, e a data só olhava as notas da janela do sync.
     * Consultas pequenas e separadas, cruzadas em memória (subconsulta por linha trava o Oracle neste volume).
     */
    private void aplicarTotaisDasNotasTransportadas(List<CargaWinThorDto> cargas) {
        if (!valorPelasNotasTransportadas || cargas.isEmpty()) {
            return;
        }
        List<Integer> numCars = cargas.stream().map(CargaWinThorDto::getNumCar).filter(Objects::nonNull).distinct().toList();

        List<TotaisCargaTransportada.NotaCarga> notas = new ArrayList<>();
        for (List<Integer> lote : lotes(numCars)) {
            notas.addAll(jdbcTemplate.query(
                    "SELECT n.numcar, n.numnota, n.vltotal, n.totpeso, n.dtsaida, n.condvenda, n.codcli,"
                            + " CASE WHEN n.dtcancel IS NULL THEN 0 ELSE 1 END AS cancelada"
                            + " FROM pcnfsaid n WHERE n.codfilial = 1 AND n.numcar IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (rs, i) -> new TotaisCargaTransportada.NotaCarga(
                            rs.getInt("numcar"),
                            rs.getLong("numnota"),
                            rs.getBigDecimal("vltotal"),
                            rs.getBigDecimal("totpeso"),
                            rs.getDate("dtsaida") != null ? rs.getDate("dtsaida").toLocalDate() : null,
                            rs.getObject("condvenda") != null ? rs.getInt("condvenda") : null,
                            rs.getObject("codcli") != null ? rs.getInt("codcli") : null,
                            rs.getInt("cancelada") == 1)));
        }

        List<Long> numeros = notas.stream().filter(n -> !n.cancelada()).map(TotaisCargaTransportada.NotaCarga::numNota).distinct().toList();
        Set<Long> comMdfe = new HashSet<>();
        for (List<Long> lote : lotes(numeros)) {
            comMdfe.addAll(jdbcTemplate.query(
                    "SELECT DISTINCT i.numnota FROM pcmanifestoeletronicoi i"
                            + " JOIN pcmanifestoeletronicoc m ON m.nummdfe = i.nummdfe AND m.codfilial = 1"
                            + " WHERE i.numnota IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (rs, i) -> rs.getLong("numnota")));
        }

        Map<Integer, TotaisCargaTransportada.Totais> totais = TotaisCargaTransportada.calcular(
                notas, comMdfe, Set.copyOf(tvTerceiro), Set.copyOf(clientesTerceiro));

        for (CargaWinThorDto c : cargas) {
            var t = totais.get(c.getNumCar());
            if (t == null) {
                // nenhuma nota transportada hoje: mantém o que a consulta principal trouxe
                c.setNotasTransportadas(0);
                c.setSomenteTv10Terceiro(false);
                continue;
            }
            c.setValorTotal(t.valor());
            c.setPesoTotalKg(t.pesoKg().doubleValue());
            if (t.dataUltimaNota() != null) {
                java.time.Instant instante = t.dataUltimaNota().atStartOfDay(ZoneId.systemDefault()).toInstant();
                c.setDtSaida(instante.atOffset(ZoneId.systemDefault().getRules().getOffset(instante)));
            }
            c.setNotasTransportadas(t.notas());
            c.setSomenteTv10Terceiro(t.somenteTv10Terceiro());
        }
    }

    private static String marcadores(int quantidade) {
        return String.join(",", Collections.nCopies(quantidade, "?"));
    }

    private static <T> List<List<T>> lotes(List<T> itens) {
        List<List<T>> resultado = new ArrayList<>();
        for (int i = 0; i < itens.size(); i += TAMANHO_LOTE_IN) {
            resultado.add(itens.subList(i, Math.min(itens.size(), i + TAMANHO_LOTE_IN)));
        }
        return resultado;
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

    /**
     * Dado um lote de numcar já sincronizados antes, diz quais deles AINDA
     * existem no WinThor (têm nota fiscal vinculada) — usado pra detectar
     * carregamento que foi apagado/desvinculado no WinThor depois de já ter
     * sido sincronizado pro FrotaPRO (ex.: pedido cancelado, minuta refeita).
     * Não usa os mesmos filtros de codmotorista/codveiculo != 0 da consulta
     * principal — aqui o que importa é só "esse numcar ainda existe de
     * alguma forma", não se está pronto pra ser (re)sincronizado.
     */
    public List<Integer> filtrarExistentes(List<Integer> codigosCargas) {
        List<Integer> codigos = normalizarCodigos(codigosCargas);
        if (codigos.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT n.numcar AS numcar
            FROM pcnfsaid n
            WHERE n.codfilial = 1
            """);

        List<Object> parametros = new ArrayList<>();
        adicionarFiltroIn(sql, parametros, "n.numcar", codigos);

        return jdbcTemplate.query(sql.toString(), parametros.toArray(), (rs, rowNum) -> rs.getInt("numcar"));
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
                e.nome                       AS nomeMotorista,
                v.placa                      AS placaVeiculo,
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
                e.nome,
                v.placa,
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
                pca.praca                    AS cidade,
                cli.cgcent                   AS documento,
                cli.enderent                 AS logradouro,
                cli.numeroent                AS numero,
                cli.bairroent                AS bairro,
                cli.municent                 AS municipio,
                cli.estent                   AS uf,
                cli.cepent                   AS cep,
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
                LEFT JOIN pcpraca pca
                    ON pca.codpraca = cli.codpraca
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
                pca.praca,
                cli.cgcent,
                cli.enderent,
                cli.numeroent,
                cli.bairroent,
                cli.municent,
                cli.estent,
                cli.cepent
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
                                // cidade/endereço deveriam ser sempre os mesmos pro mesmo cliente; se por
                                // algum motivo vier diferente entre MDF-e's, fica com o primeiro não-nulo.
                                .cidade(existente.getCidade() != null ? existente.getCidade() : novo.getCidade())
                                .documento(existente.getDocumento() != null ? existente.getDocumento() : novo.getDocumento())
                                .logradouro(existente.getLogradouro() != null ? existente.getLogradouro() : novo.getLogradouro())
                                .numero(existente.getNumero() != null ? existente.getNumero() : novo.getNumero())
                                .bairro(existente.getBairro() != null ? existente.getBairro() : novo.getBairro())
                                .municipio(existente.getMunicipio() != null ? existente.getMunicipio() : novo.getMunicipio())
                                .uf(existente.getUf() != null ? existente.getUf() : novo.getUf())
                                .cep(existente.getCep() != null ? existente.getCep() : novo.getCep())
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

    /**
     * Preenche, pra cada carregamento, os códigos de devolução (view_devol_resumo_faturamento,
     * via pcnfsaid.numtransvenda) e se houve transferência de pedido pra outro carregamento
     * (PCLOGTRANSFNFCARREG.numcaranterior). Usado pelo gate de peso/valor em
     * ParametroSistema.validarMotivoAlteracaoPesoValorCarga, na API.
     */
    private void preencherMotivosAlteracao(List<CargaWinThorDto> cargas) {
        List<Integer> codigosCargas = cargas.stream()
                .map(CargaWinThorDto::getNumCar)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, List<String>> devolucoesPorNumCar = buscarCodigosDevolucaoPorNumCar(codigosCargas);
        Set<Integer> numCarsComTransferencia = buscarNumCarsComTransferencia(codigosCargas);

        Map<Integer, String[]> origens = buscarOrigemDasCargas(codigosCargas);

        cargas.forEach(c -> {
            c.setCodigosDevolucao(devolucoesPorNumCar.getOrDefault(c.getNumCar(), List.of()));
            c.setTemTransferencia(numCarsComTransferencia.contains(c.getNumCar()));
            String[] origem = origens.get(c.getNumCar());
            if (origem != null) {
                c.setOrigemCarga(origem[0]);
                c.setOrigemNumCar(origem[1] == null ? null : Integer.valueOf(origem[1]));
            }
        });
    }

    /**
     * Como cada carregamento nasceu, pelo primeiro registro do log de transferência em que ele é o destino:
     * "REENTREGA" (pedido que voltou e ganhou carga própria, em geral pequena) ou "TRANSFERENCIA".
     * Devolve numCar -> {origem, carregamento de onde veio}.
     */
    private Map<Integer, String[]> buscarOrigemDasCargas(List<Integer> codigosCargas) {
        Map<Integer, String[]> resultado = new HashMap<>();
        for (List<Integer> lote : lotes(codigosCargas)) {
            jdbcTemplate.query(
                    "SELECT t.numcaratual, t.numcaranterior, t.motivotransf FROM pclogtransfnfcarreg t"
                            + " WHERE t.numcaratual IN (" + marcadores(lote.size()) + ") ORDER BY t.dttransf, t.numnota",
                    lote.toArray(),
                    rs -> {
                        int destino = rs.getInt("numcaratual");
                        if (resultado.containsKey(destino)) return;
                        String motivo = rs.getString("motivotransf");
                        String tipo = motivo != null && motivo.toUpperCase().contains("REENTREGA") ? "REENTREGA" : "TRANSFERENCIA";
                        Object anterior = rs.getObject("numcaranterior");
                        resultado.put(destino, new String[]{tipo, anterior == null ? null : String.valueOf(rs.getInt("numcaranterior"))});
                    });
        }
        return resultado;
    }

    private Map<Integer, List<String>> buscarCodigosDevolucaoPorNumCar(List<Integer> codigosCargas) {
        if (codigosCargas.isEmpty()) {
            return Map.of();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                n.numcar               AS numCar,
                d.coddevol             AS codDevol
            FROM
                pcnfsaid n
                JOIN view_devol_resumo_faturamento d
                    ON d.numtransvenda = n.numtransvenda
            WHERE
                d.coddevol IS NOT NULL
            """);

        List<Object> parametros = new ArrayList<>();
        adicionarFiltroIn(sql, parametros, "n.numcar", codigosCargas);

        List<DevolucaoRow> rows = jdbcTemplate.query(
                sql.toString(),
                parametros.toArray(),
                (rs, rowNum) -> new DevolucaoRow(rs.getInt("numCar"), rs.getString("codDevol"))
        );

        Map<Integer, Set<String>> agrupado = new LinkedHashMap<>();
        for (DevolucaoRow row : rows) {
            agrupado.computeIfAbsent(row.numCar(), k -> new LinkedHashSet<>()).add(row.codDevol().trim());
        }

        Map<Integer, List<String>> resultado = new HashMap<>();
        agrupado.forEach((numCar, codigos) -> resultado.put(numCar, new ArrayList<>(codigos)));
        return resultado;
    }

    private Set<Integer> buscarNumCarsComTransferencia(List<Integer> codigosCargas) {
        if (codigosCargas.isEmpty()) {
            return Set.of();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT
                t.numcaranterior AS numCarAnterior
            FROM
                pclogtransfnfcarreg t
            WHERE
                1 = 1
            """);

        List<Object> parametros = new ArrayList<>();
        adicionarFiltroIn(sql, parametros, "t.numcaranterior", codigosCargas);

        List<Integer> numCars = jdbcTemplate.query(
                sql.toString(),
                parametros.toArray(),
                (rs, rowNum) -> rs.getInt("numCarAnterior")
        );

        return new HashSet<>(numCars);
    }

    private record DevolucaoRow(int numCar, String codDevol) {
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
        String nomeMotorista = rs.getString("nomeMotorista");
        String placaVeiculo  = rs.getString("placaVeiculo");
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
                .nomeMotorista(nomeMotorista)
                .placaVeiculo(placaVeiculo)
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
        int numCar        = rs.getInt("numCar");
        int codCli        = rs.getInt("codCli");
        String nomeCli    = rs.getString("nomeCli");
        String cidade     = rs.getString("cidade");
        String documento  = rs.getString("documento");
        String logradouro = rs.getString("logradouro");
        String numero     = rs.getString("numero");
        String bairro     = rs.getString("bairro");
        String municipio  = rs.getString("municipio");
        String uf         = rs.getString("uf");
        String cep        = rs.getString("cep");
        String notasStr   = rs.getString("notas");

        List<Long> notas = notasStr == null || notasStr.isBlank()
                ? Collections.emptyList()
                : Arrays.stream(notasStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toList());

        return new ClienteRow(numCar, codCli, nomeCli, cidade, documento, logradouro, numero, bairro, municipio,
                uf, cep, notas);
    }

    private record ClienteRow(
            int numCar, int codCli, String nomeCli, String cidade,
            String documento, String logradouro, String numero, String bairro, String municipio,
            String uf, String cep,
            List<Long> notas
    ) {

        ClienteCargaWinThorDto toDto() {
            return ClienteCargaWinThorDto.builder()
                    .codCli(codCli)
                    .nomeCli(nomeCli)
                    .cidade(cidade)
                    .documento(documento)
                    .logradouro(logradouro)
                    .numero(numero)
                    .bairro(bairro)
                    .municipio(municipio)
                    .uf(uf)
                    .cep(cep)
                    .notas(notas)
                    .build();
        }
    }
}

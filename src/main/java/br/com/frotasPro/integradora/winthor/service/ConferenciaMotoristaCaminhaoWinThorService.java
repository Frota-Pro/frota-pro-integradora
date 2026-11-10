package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ConferenciaMotoristaCaminhaoItemDto;
import br.com.frotasPro.integradora.winthor.dto.ConferenciaMotoristaCaminhaoItemDto.TransferenciaNotaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Conferência (só leitura) de reentrega: acha carregamentos cujas notas entraram DEPOIS de o MDF-e ser gerado e cujo
 * motorista/veículo do carregamento (pccarreg) é diferente do motorista/veículo do manifesto.
 *
 * Caso típico: o motorista A saiu com as notas (MDF-e dele), elas voltaram, a logística as colocou num carregamento
 * novo para o motorista B. O WinThor liga o MDF-e à NOTA, então o carregamento novo continua enxergando o manifesto
 * do motorista A. Nada aqui altera dado: a API só mostra ao usuário, que decide.
 *
 * As consultas são pequenas e cruzadas em memória (uma consulta única com subconsulta por nota trava no Oracle).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenciaMotoristaCaminhaoWinThorService {

    private static final int TAMANHO_LOTE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final CargaWinThorService cargaWinThorService;

    /**
     * @param numCars se informado, olha só esses carregamentos (qualquer data de transferência); senão usa o período
     *                pela data em que a nota entrou no carregamento.
     */
    public List<ConferenciaMotoristaCaminhaoItemDto> buscar(LocalDate dataInicial, LocalDate dataFinal, List<Integer> numCars) {
        List<Integer> filtroNumCars = numCars == null ? List.of() : numCars.stream().filter(Objects::nonNull).distinct().toList();

        Map<Integer, List<Transferencia>> transferenciasPorCarregamento =
                agruparPorCarregamentoAtual(lerTransferencias(dataInicial, dataFinal, filtroNumCars));
        if (transferenciasPorCarregamento.isEmpty()) {
            return List.of();
        }

        // 1) Só carregamentos com motorista e veículo de verdade (nem zero, nem o genérico de reentrega).
        Map<Integer, Carregamento> carregamentos = new LinkedHashMap<>();
        lerCarregamentos(new ArrayList<>(transferenciasPorCarregamento.keySet())).forEach((numCar, c) -> {
            if (c.codMotorista() != null && c.codMotorista() != 0 && !cargaWinThorService.isMotoristaGenerico(c.codMotorista())
                    && c.codVeiculo() != null && c.codVeiculo() != 0 && !cargaWinThorService.isVeiculoGenerico(c.codVeiculo())) {
                carregamentos.put(numCar, c);
            }
        });
        if (carregamentos.isEmpty()) {
            return List.of();
        }

        // 2) O manifesto que a sincronização usa para cada um; só interessa onde ele diverge do carregamento.
        Map<Integer, CargaWinThorDto> mdfeVigente = cargaWinThorService.buscarMdfeVigentePorCarregamento(new ArrayList<>(carregamentos.keySet()));
        Map<Integer, CargaWinThorDto> divergentes = new LinkedHashMap<>();
        mdfeVigente.forEach((numCar, mdfe) -> {
            Carregamento c = carregamentos.get(numCar);
            if (c != null && (motoristaDiferente(mdfe.getCodMotorista(), c.codMotorista())
                    || veiculoDiferente(mdfe.getCodVeiculo(), c.codVeiculo()))) {
                divergentes.put(numCar, mdfe);
            }
        });
        if (divergentes.isEmpty()) {
            return List.of();
        }

        // 3) Data de geração do manifesto, notas que estão hoje em cada carregamento e a quais manifestos as notas
        //    transferidas pertencem.
        Map<Long, LocalDateTime> geracaoPorMdfe = lerDatasGeracao(divergentes.values().stream().map(CargaWinThorDto::getNumMdfe).distinct().toList());
        Map<Integer, Set<Long>> notasAtuais = lerNotasAtuais(new ArrayList<>(divergentes.keySet()));
        Set<Long> notasTransferidas = new LinkedHashSet<>();
        divergentes.keySet().forEach(numCar -> transferenciasPorCarregamento.get(numCar).forEach(t -> notasTransferidas.add(t.numNota())));
        Map<Long, Set<Long>> mdfesDaNota = lerMdfesDasNotas(new ArrayList<>(notasTransferidas));

        // 4) Aplica a regra e monta os itens.
        Map<Integer, String> nomesMotorista = lerNomesMotorista(codigosMotorista(divergentes, carregamentos));
        Map<Integer, String> placas = lerPlacas(codigosVeiculo(divergentes, carregamentos));

        List<ConferenciaMotoristaCaminhaoItemDto> itens = new ArrayList<>();
        divergentes.forEach((numCar, mdfe) -> {
            Carregamento c = carregamentos.get(numCar);
            List<Transferencia> evidencias = transferenciasPosterioresAoMdfe(
                    mdfe.getNumMdfe(),
                    geracaoPorMdfe.get(mdfe.getNumMdfe()),
                    transferenciasPorCarregamento.get(numCar),
                    mdfesDaNota,
                    notasAtuais.getOrDefault(numCar, Set.of()));
            if (evidencias.isEmpty()) {
                return;
            }
            itens.add(ConferenciaMotoristaCaminhaoItemDto.builder()
                    .numCar(numCar)
                    .numMdfe(mdfe.getNumMdfe())
                    .situacaoMdfe(mdfe.getSituacaoMdfe())
                    .dataGeracaoMdfe(geracaoPorMdfe.get(mdfe.getNumMdfe()))
                    .codMotoristaMdfe(mdfe.getCodMotorista())
                    .nomeMotoristaMdfe(mdfe.getNomeMotorista())
                    .codVeiculoMdfe(mdfe.getCodVeiculo())
                    .placaMdfe(mdfe.getPlacaVeiculo())
                    .codMotoristaCarregamento(c.codMotorista())
                    .nomeMotoristaCarregamento(nomesMotorista.get(c.codMotorista()))
                    .codVeiculoCarregamento(String.valueOf(c.codVeiculo()))
                    .placaCarregamento(placas.get(c.codVeiculo()))
                    .motoristaDiferente(motoristaDiferente(mdfe.getCodMotorista(), c.codMotorista()))
                    .veiculoDiferente(veiculoDiferente(mdfe.getCodVeiculo(), c.codVeiculo()))
                    .transferencias(evidencias.stream().map(ConferenciaMotoristaCaminhaoWinThorService::paraDto).toList())
                    .build());
        });
        itens.sort(Comparator.comparing(ConferenciaMotoristaCaminhaoItemDto::getNumCar).reversed());
        return itens;
    }

    // ------------------------------------------------------------------ regra (pura, testada à parte)

    /**
     * As transferências que justificam a conferência: a nota ainda está no carregamento, pertence ao manifesto que a
     * sincronização usa e entrou ali DEPOIS de o manifesto ser gerado. Sem data de geração não há como afirmar nada.
     */
    static List<Transferencia> transferenciasPosterioresAoMdfe(Long numMdfe,
                                                              LocalDateTime geracaoMdfe,
                                                              List<Transferencia> transferenciasDoCarregamento,
                                                              Map<Long, Set<Long>> mdfesDaNota,
                                                              Set<Long> notasAtuaisDoCarregamento) {
        if (numMdfe == null || geracaoMdfe == null || transferenciasDoCarregamento == null) {
            return List.of();
        }
        return transferenciasDoCarregamento.stream()
                .filter(t -> t.dataTransferencia() != null && t.dataTransferencia().isAfter(geracaoMdfe))
                .filter(t -> notasAtuaisDoCarregamento.contains(t.numNota()))
                .filter(t -> mdfesDaNota.getOrDefault(t.numNota(), Set.of()).contains(numMdfe))
                .sorted(Comparator.comparing(Transferencia::dataTransferencia))
                .toList();
    }

    static boolean motoristaDiferente(Integer codMotoristaMdfe, Integer codMotoristaCarregamento) {
        return !Objects.equals(codMotoristaMdfe, codMotoristaCarregamento);
    }

    /** O veículo do manifesto vem como texto e o do carregamento como número; compara como número quando der. */
    static boolean veiculoDiferente(String codVeiculoMdfe, Integer codVeiculoCarregamento) {
        if (codVeiculoMdfe == null || codVeiculoCarregamento == null) {
            return !Objects.equals(codVeiculoMdfe, codVeiculoCarregamento == null ? null : String.valueOf(codVeiculoCarregamento));
        }
        try {
            return Integer.parseInt(codVeiculoMdfe.trim()) != codVeiculoCarregamento;
        } catch (NumberFormatException e) {
            return !codVeiculoMdfe.trim().equals(String.valueOf(codVeiculoCarregamento));
        }
    }

    private static TransferenciaNotaDto paraDto(Transferencia t) {
        return TransferenciaNotaDto.builder()
                .numNota(t.numNota())
                .numCarAnterior(t.numCarAnterior())
                .numCarAtual(t.numCarAtual())
                .dataTransferencia(t.dataTransferencia())
                .motivo(t.motivo())
                .build();
    }

    private static Map<Integer, List<Transferencia>> agruparPorCarregamentoAtual(List<Transferencia> transferencias) {
        Map<Integer, List<Transferencia>> agrupado = new LinkedHashMap<>();
        for (Transferencia t : transferencias) {
            if (t.numCarAtual() != null && t.numCarAtual() > 1) {
                agrupado.computeIfAbsent(t.numCarAtual(), k -> new ArrayList<>()).add(t);
            }
        }
        return agrupado;
    }

    private static Set<Integer> codigosMotorista(Map<Integer, CargaWinThorDto> divergentes, Map<Integer, Carregamento> carregamentos) {
        Set<Integer> codigos = new LinkedHashSet<>();
        divergentes.forEach((numCar, mdfe) -> {
            if (mdfe.getCodMotorista() != null) {
                codigos.add(mdfe.getCodMotorista());
            }
            codigos.add(carregamentos.get(numCar).codMotorista());
        });
        return codigos;
    }

    private static Set<Integer> codigosVeiculo(Map<Integer, CargaWinThorDto> divergentes, Map<Integer, Carregamento> carregamentos) {
        Set<Integer> codigos = new LinkedHashSet<>();
        divergentes.forEach((numCar, mdfe) -> {
            if (mdfe.getCodVeiculo() != null) {
                try {
                    codigos.add(Integer.parseInt(mdfe.getCodVeiculo().trim()));
                } catch (NumberFormatException ignorado) {
                    // sem placa extra; o DTO do manifesto já traz a placa
                }
            }
            codigos.add(carregamentos.get(numCar).codVeiculo());
        });
        return codigos;
    }

    // ------------------------------------------------------------------ leituras (Oracle, só SELECT)

    private List<Transferencia> lerTransferencias(LocalDate dataInicial, LocalDate dataFinal, List<Integer> numCars) {
        String colunas = "SELECT t.numnota AS numnota, t.numcaranterior AS anterior, t.numcaratual AS atual,"
                + " t.dttransf AS dttransf, t.motivotransf AS motivo FROM pclogtransfnfcarreg t WHERE ";
        RowMapperTransferencia mapper = new RowMapperTransferencia();
        List<Transferencia> resultado = new ArrayList<>();

        if (!numCars.isEmpty()) {
            for (List<Integer> lote : CargaWinThorService.emLotes(numCars, TAMANHO_LOTE)) {
                resultado.addAll(jdbcTemplate.query(
                        colunas + "t.numcaratual IN (" + marcadores(lote.size()) + ")",
                        lote.toArray(), mapper));
            }
            return resultado;
        }
        if (dataInicial == null || dataFinal == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                colunas + "t.dttransf >= TRUNC(?) AND t.dttransf < TRUNC(?) + 1",
                new Object[]{java.sql.Date.valueOf(dataInicial), java.sql.Date.valueOf(dataFinal)}, mapper);
    }

    private Map<Integer, Carregamento> lerCarregamentos(List<Integer> numCars) {
        Map<Integer, Carregamento> resultado = new LinkedHashMap<>();
        for (List<Integer> lote : CargaWinThorService.emLotes(numCars, TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT c.numcar AS numcar, c.codmotorista AS codmot, c.codveiculo AS codvei FROM pccarreg c"
                            + " WHERE c.numcar IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> resultado.put(
                            rs.getInt("numcar"), new Carregamento(inteiroOuNulo(rs, "codmot"), inteiroOuNulo(rs, "codvei"))));
        }
        return resultado;
    }

    private Map<Integer, Set<Long>> lerNotasAtuais(List<Integer> numCars) {
        Map<Integer, Set<Long>> resultado = new HashMap<>();
        for (List<Integer> lote : CargaWinThorService.emLotes(numCars, TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT n.numcar AS numcar, n.numnota AS numnota FROM pcnfsaid n WHERE n.codfilial = 1"
                            + " AND n.numcar IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> resultado
                            .computeIfAbsent(rs.getInt("numcar"), k -> new HashSet<>())
                            .add(rs.getLong("numnota")));
        }
        return resultado;
    }

    private Map<Long, LocalDateTime> lerDatasGeracao(List<Long> numMdfes) {
        Map<Long, LocalDateTime> resultado = new HashMap<>();
        List<Long> validos = numMdfes.stream().filter(Objects::nonNull).toList();
        for (List<Long> lote : CargaWinThorService.emLotes(validos, TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT m.nummdfe AS nummdfe, m.datahorageracao AS geracao FROM pcmanifestoeletronicoc m"
                            + " WHERE m.nummdfe IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        java.sql.Timestamp ts = rs.getTimestamp("geracao");
                        if (ts != null) {
                            resultado.put(rs.getLong("nummdfe"), ts.toLocalDateTime());
                        }
                    });
        }
        return resultado;
    }

    private Map<Long, Set<Long>> lerMdfesDasNotas(List<Long> notas) {
        Map<Long, Set<Long>> resultado = new HashMap<>();
        for (List<Long> lote : CargaWinThorService.emLotes(notas, TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT i.numnota AS numnota, i.nummdfe AS nummdfe FROM pcmanifestoeletronicoi i"
                            + " WHERE i.numnota IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> resultado
                            .computeIfAbsent(rs.getLong("numnota"), k -> new HashSet<>())
                            .add(rs.getLong("nummdfe")));
        }
        return resultado;
    }

    private Map<Integer, String> lerNomesMotorista(Set<Integer> codigos) {
        Map<Integer, String> resultado = new HashMap<>();
        for (List<Integer> lote : CargaWinThorService.emLotes(new ArrayList<>(codigos), TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT e.matricula AS codigo, e.nome AS nome FROM pcempr e WHERE e.tipo = 'M'"
                            + " AND e.matricula IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> resultado.put(rs.getInt("codigo"), rs.getString("nome")));
        }
        return resultado;
    }

    private Map<Integer, String> lerPlacas(Set<Integer> codigos) {
        Map<Integer, String> resultado = new HashMap<>();
        for (List<Integer> lote : CargaWinThorService.emLotes(new ArrayList<>(codigos), TAMANHO_LOTE)) {
            jdbcTemplate.query(
                    "SELECT v.codveiculo AS codigo, v.placa AS placa FROM pcveicul v"
                            + " WHERE v.codveiculo IN (" + marcadores(lote.size()) + ")",
                    lote.toArray(),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> resultado.put(rs.getInt("codigo"), rs.getString("placa")));
        }
        return resultado;
    }

    private static String marcadores(int quantidade) {
        return String.join(",", Collections.nCopies(quantidade, "?"));
    }

    private static Integer inteiroOuNulo(java.sql.ResultSet rs, String coluna) throws java.sql.SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }

    // ------------------------------------------------------------------ tipos internos

    record Transferencia(Long numNota, Integer numCarAnterior, Integer numCarAtual, LocalDateTime dataTransferencia, String motivo) {
    }

    record Carregamento(Integer codMotorista, Integer codVeiculo) {
    }

    private static class RowMapperTransferencia implements org.springframework.jdbc.core.RowMapper<Transferencia> {
        @Override
        public Transferencia mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            java.sql.Timestamp ts = rs.getTimestamp("dttransf");
            return new Transferencia(
                    rs.getLong("numnota"),
                    inteiroOuNulo(rs, "anterior"),
                    inteiroOuNulo(rs, "atual"),
                    ts != null ? ts.toLocalDateTime() : null,
                    rs.getString("motivo"));
        }
    }
}

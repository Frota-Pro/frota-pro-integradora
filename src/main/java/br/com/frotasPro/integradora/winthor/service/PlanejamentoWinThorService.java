package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.PlanejamentoCarregamentoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Leitura (só leitura) dos carregamentos para a planilha de planejamento. Não depende de nota nem de MDF-e: o
 * planejamento acontece antes do faturamento. Três consultas pequenas, cruzadas em memória.
 */
@Service
@RequiredArgsConstructor
public class PlanejamentoWinThorService {

    private static final int TAMANHO_LOTE = 500;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param numCars se informado, devolve só esses carregamentos (qualquer data); senão, os montados no período.
     */
    public List<PlanejamentoCarregamentoDto> buscar(LocalDate dataInicial, LocalDate dataFinal, List<Integer> numCars) {
        List<PlanejamentoCarregamentoDto> base = (numCars != null && !numCars.isEmpty())
                ? lerCarregamentosPorNumero(numCars.stream().filter(Objects::nonNull).distinct().toList())
                : lerCarregamentosDoPeriodo(dataInicial, dataFinal);
        if (base.isEmpty()) {
            return List.of();
        }

        List<Integer> todos = base.stream().map(PlanejamentoCarregamentoDto::getNumCar).toList();
        Map<Integer, Pedidos> pedidos = lerPedidos(todos);
        Set<Integer> faturadas = lerFaturadas(todos);
        Map<Integer, String> nomesMotoristas = lerNomesMotoristas(base.stream().map(PlanejamentoCarregamentoDto::getCodMotorista).toList());
        Map<Integer, String> placasVeiculos = lerPlacasVeiculos(base.stream().map(PlanejamentoCarregamentoDto::getCodVeiculo).toList());

        List<PlanejamentoCarregamentoDto> resultado = new ArrayList<>();
        for (PlanejamentoCarregamentoDto c : base) {
            Pedidos p = pedidos.getOrDefault(c.getNumCar(), new Pedidos());
            resultado.add(PlanejamentoCarregamentoDto.builder()
                    .numCar(c.getNumCar())
                    .dataMontagem(c.getDataMontagem())
                    .destino(c.getDestino())
                    .codMotorista(c.getCodMotorista())
                    .codVeiculo(c.getCodVeiculo())
                    .nomeMotorista(c.getCodMotorista() == null ? null : nomesMotoristas.get(c.getCodMotorista()))
                    .placaVeiculo(c.getCodVeiculo() == null ? null : placasVeiculos.get(c.getCodVeiculo()))
                    .pesoKg(c.getPesoKg())
                    .totalPedidos(p.pedidos)
                    .totalClientes(p.clientes.size())
                    .valorTotal(p.valor)
                    .codigosClientes(new ArrayList<>(p.clientes))
                    .faturada(faturadas.contains(c.getNumCar()))
                    .build());
        }
        return resultado;
    }

    private List<PlanejamentoCarregamentoDto> lerCarregamentosDoPeriodo(LocalDate ini, LocalDate fim) {
        // datamon pode ter hora: vai do início do 1º dia até antes do dia seguinte ao último.
        String sql = "SELECT c.numcar AS numcar, c.datamon AS datamon, c.destino AS destino, c.totpeso AS totpeso,"
                + " c.codmotorista AS codmotorista, c.codveiculo AS codveiculo"
                + " FROM pccarreg c WHERE c.datamon >= ? AND c.datamon < ? AND c.dt_cancel IS NULL"
                + " ORDER BY c.numcar";
        return jdbcTemplate.query(sql, (rs, i) -> mapCarregamento(rs),
                Date.valueOf(ini), Date.valueOf(fim.plusDays(1)));
    }

    private List<PlanejamentoCarregamentoDto> lerCarregamentosPorNumero(List<Integer> numCars) {
        List<PlanejamentoCarregamentoDto> resultado = new ArrayList<>();
        for (List<Integer> lote : lotes(numCars)) {
            String sql = "SELECT c.numcar AS numcar, c.datamon AS datamon, c.destino AS destino, c.totpeso AS totpeso,"
                    + " c.codmotorista AS codmotorista, c.codveiculo AS codveiculo"
                    + " FROM pccarreg c WHERE c.numcar IN (" + interrogacoes(lote.size()) + ") AND c.dt_cancel IS NULL";
            resultado.addAll(jdbcTemplate.query(sql, (rs, i) -> mapCarregamento(rs), lote.toArray()));
        }
        resultado.sort(Comparator.comparing(PlanejamentoCarregamentoDto::getNumCar));
        return resultado;
    }

    /** numcar → pedidos e clientes distintos, sem pedido cancelado. */
    private Map<Integer, Pedidos> lerPedidos(List<Integer> numCars) {
        Map<Integer, Pedidos> mapa = new HashMap<>();
        for (List<Integer> lote : lotes(numCars)) {
            String sql = "SELECT p.numcar AS numcar, p.codcli AS codcli, COUNT(*) AS pedidos, SUM(p.vltotal) AS valor"
                    + " FROM pcpedc p WHERE p.numcar IN (" + interrogacoes(lote.size()) + ") AND p.dtcancel IS NULL"
                    + " GROUP BY p.numcar, p.codcli";
            jdbcTemplate.query(sql, rs -> {
                Pedidos p = mapa.computeIfAbsent(rs.getInt("numcar"), k -> new Pedidos());
                p.pedidos += rs.getInt("pedidos");
                BigDecimal valor = rs.getBigDecimal("valor");
                if (valor != null) {
                    p.valor = p.valor.add(valor);
                }
                p.clientes.add(rs.getInt("codcli"));
            }, lote.toArray());
        }
        return mapa;
    }

    private static class Pedidos {
        int pedidos;
        BigDecimal valor = BigDecimal.ZERO;
        final Set<Integer> clientes = new TreeSet<>();
    }

    /** codmotorista → nome (pcempr, tipo M — o mesmo cadastro que a leitura das cargas usa). */
    private Map<Integer, String> lerNomesMotoristas(List<Integer> codigos) {
        Map<Integer, String> nomes = new HashMap<>();
        List<Integer> distintos = codigos.stream().filter(c -> c != null && c != 0).distinct().toList();
        for (List<Integer> lote : lotes(distintos)) {
            String sql = "SELECT e.matricula AS codigo, e.nome AS nome FROM pcempr e WHERE e.tipo = 'M' AND e.matricula IN ("
                    + interrogacoes(lote.size()) + ")";
            jdbcTemplate.query(sql, rs -> {
                nomes.put(rs.getInt("codigo"), rs.getString("nome"));
            }, lote.toArray());
        }
        return nomes;
    }

    /** codveiculo → placa (pcveicul). */
    private Map<Integer, String> lerPlacasVeiculos(List<Integer> codigos) {
        Map<Integer, String> placas = new HashMap<>();
        List<Integer> distintos = codigos.stream().filter(c -> c != null && c != 0).distinct().toList();
        for (List<Integer> lote : lotes(distintos)) {
            String sql = "SELECT v.codveiculo AS codigo, v.placa AS placa FROM pcveicul v WHERE v.codveiculo IN ("
                    + interrogacoes(lote.size()) + ")";
            jdbcTemplate.query(sql, rs -> {
                placas.put(rs.getInt("codigo"), rs.getString("placa"));
            }, lote.toArray());
        }
        return placas;
    }

    private Set<Integer> lerFaturadas(List<Integer> numCars) {
        Set<Integer> faturadas = new HashSet<>();
        for (List<Integer> lote : lotes(numCars)) {
            String sql = "SELECT DISTINCT n.numcar AS numcar FROM pcnfsaid n WHERE n.numcar IN ("
                    + interrogacoes(lote.size()) + ")";
            jdbcTemplate.query(sql, rs -> {
                faturadas.add(rs.getInt("numcar"));
            }, lote.toArray());
        }
        return faturadas;
    }

    private PlanejamentoCarregamentoDto mapCarregamento(ResultSet rs) throws SQLException {
        Date data = rs.getDate("datamon");
        BigDecimal peso = rs.getBigDecimal("totpeso");
        return PlanejamentoCarregamentoDto.builder()
                .numCar(rs.getInt("numcar"))
                .dataMontagem(data != null ? data.toLocalDate() : null)
                .destino(rs.getString("destino"))
                .codMotorista(inteiroOuNulo(rs, "codmotorista"))
                .codVeiculo(inteiroOuNulo(rs, "codveiculo"))
                .pesoKg(peso != null ? peso.doubleValue() : 0.0)
                .build();
    }

    private static Integer inteiroOuNulo(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private static List<List<Integer>> lotes(List<Integer> itens) {
        List<List<Integer>> lotes = new ArrayList<>();
        for (int i = 0; i < itens.size(); i += TAMANHO_LOTE) {
            lotes.add(itens.subList(i, Math.min(i + TAMANHO_LOTE, itens.size())));
        }
        return lotes;
    }

    private static String interrogacoes(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }
}

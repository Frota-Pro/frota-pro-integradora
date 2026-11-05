package br.com.frotasPro.integradora.winthor.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valor, peso e data de uma carga calculados só com as notas que são TRANSPORTE: não canceladas e
 * dentro de um MDF-e da filial 1. Nota cancelada, ou que nunca entrou num manifesto, não viajou e não
 * pode somar no que a frota transportou. Função pura, sem banco.
 *
 * <p>O peso é a soma do peso das notas (em kg), então valor e peso saem da mesma lista de notas e
 * diminuem e aumentam juntos quando uma nota entra, sai ou é cancelada.
 */
final class TotaisCargaTransportada {

    private TotaisCargaTransportada() {
    }

    record NotaCarga(int numCar, long numNota, BigDecimal valor, BigDecimal pesoKg, LocalDate dtSaida,
                     Integer condVenda, Integer codCli, boolean cancelada) {
    }

    record Totais(BigDecimal valor, BigDecimal pesoKg, LocalDate dataUltimaNota, int notas, boolean somenteTv10Terceiro) {
    }

    /**
     * @param tvTerceiro       tipos de venda que podem ser viagem de terceiro (TV10)
     * @param clientesTerceiro clientes cujas cargas TV10 saem com motorista e caminhão de terceiro
     * @return um item por carregamento que tem ao menos uma nota transportada
     */
    static Map<Integer, Totais> calcular(List<NotaCarga> notas, Set<Long> comMdfe,
                                         Set<Integer> tvTerceiro, Set<Integer> clientesTerceiro) {
        Map<Integer, List<NotaCarga>> porCarga = new HashMap<>();
        for (NotaCarga n : notas) {
            if (n.cancelada() || !comMdfe.contains(n.numNota())) continue;
            porCarga.computeIfAbsent(n.numCar(), k -> new ArrayList<>()).add(n);
        }

        Map<Integer, Totais> resultado = new HashMap<>();
        porCarga.forEach((numCar, lista) -> {
            BigDecimal valor = BigDecimal.ZERO;
            BigDecimal peso = BigDecimal.ZERO;
            LocalDate ultima = null;
            boolean soTerceiro = true;
            for (NotaCarga n : lista) {
                valor = valor.add(n.valor() == null ? BigDecimal.ZERO : n.valor());
                peso = peso.add(n.pesoKg() == null ? BigDecimal.ZERO : n.pesoKg());
                if (n.dtSaida() != null && (ultima == null || n.dtSaida().isAfter(ultima))) ultima = n.dtSaida();
                boolean terceiro = n.condVenda() != null && tvTerceiro.contains(n.condVenda())
                        && n.codCli() != null && clientesTerceiro.contains(n.codCli());
                soTerceiro &= terceiro;
            }
            resultado.put(numCar, new Totais(valor, peso, ultima, lista.size(), soTerceiro));
        });
        return resultado;
    }
}

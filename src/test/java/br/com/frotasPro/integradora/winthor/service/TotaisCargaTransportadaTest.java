package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.service.TotaisCargaTransportada.NotaCarga;
import br.com.frotasPro.integradora.winthor.service.TotaisCargaTransportada.Totais;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotaisCargaTransportadaTest {

    private static final Set<Integer> TV = Set.of(10);
    private static final Set<Integer> CLIENTES = Set.of(15575, 10073);

    private static NotaCarga nota(int car, long numero, String valor, String peso, LocalDate dia, int tv, int cliente, boolean cancelada) {
        return new NotaCarga(car, numero, new BigDecimal(valor), new BigDecimal(peso), dia, tv, cliente, cancelada);
    }

    private static Map<Integer, Totais> calcular(List<NotaCarga> notas, Set<Long> comMdfe) {
        return TotaisCargaTransportada.calcular(notas, comMdfe, TV, CLIENTES);
    }

    @Test
    void somaValorEPesoSoDasNotasTransportadas() {
        var notas = List.of(
                nota(500, 1, "1000.00", "100", LocalDate.of(2026, 10, 5), 1, 10, false),
                nota(500, 2, "250.50", "20.5", LocalDate.of(2026, 10, 6), 1, 11, false),
                nota(500, 3, "900.00", "90", LocalDate.of(2026, 10, 6), 1, 12, true),   // cancelada
                nota(500, 4, "300.00", "30", LocalDate.of(2026, 10, 7), 1, 13, false)); // sem MDF-e

        Totais t = calcular(notas, Set.of(1L, 2L, 3L)).get(500);

        assertEquals(0, new BigDecimal("1250.50").compareTo(t.valor()));
        assertEquals(0, new BigDecimal("120.5").compareTo(t.pesoKg()));
        assertEquals(2, t.notas());
        assertEquals(LocalDate.of(2026, 10, 6), t.dataUltimaNota(), "a data é a da última nota transportada");
    }

    @Test
    void pesoDaCargaVaiParaToneladasPoisEssaEAUnidadeGuardadaNoFrotaPro() {
        var notas = List.of(
                nota(500, 1, "100", "26.392", LocalDate.of(2026, 10, 5), 1, 10, false),
                nota(500, 2, "100", "9973.608", LocalDate.of(2026, 10, 5), 1, 10, false));

        Totais t = calcular(notas, Set.of(1L, 2L)).get(500);

        assertEquals(0, new BigDecimal("10000").compareTo(t.pesoKg()));
        assertEquals(0, new BigDecimal("10").compareTo(t.pesoToneladas()), "10.000 kg = 10 toneladas");
    }

    @Test
    void dataEDeQualquerMesPoisAUltimaNotaDecideACarga() {
        var notas = List.of(
                nota(500, 1, "100", "1", LocalDate.of(2026, 9, 30), 1, 10, false),
                nota(500, 2, "100", "1", LocalDate.of(2026, 10, 2), 1, 10, false));

        assertEquals(LocalDate.of(2026, 10, 2), calcular(notas, Set.of(1L, 2L)).get(500).dataUltimaNota());
    }

    @Test
    void carregamentoSemNenhumaNotaTransportadaNaoApareceNoResultado() {
        var notas = List.of(nota(600, 7, "50", "5", LocalDate.of(2026, 10, 1), 1, 10, false));

        assertNull(calcular(notas, Set.of()).get(600));
    }

    @Test
    void tv10DosClientesTerceirosDeixaMarcadoQueOTransporteEDeTerceiro() {
        var soTerceiro = List.of(
                nota(700, 1, "100", "1", LocalDate.of(2026, 10, 1), 10, 15575, false),
                nota(700, 2, "100", "1", LocalDate.of(2026, 10, 1), 10, 10073, false));
        var misturada = List.of(
                nota(800, 3, "100", "1", LocalDate.of(2026, 10, 1), 10, 15575, false),
                nota(800, 4, "100", "1", LocalDate.of(2026, 10, 1), 1, 10, false));
        var tv10OutroCliente = List.of(nota(900, 5, "100", "1", LocalDate.of(2026, 10, 1), 10, 99, false));

        assertTrue(calcular(soTerceiro, Set.of(1L, 2L)).get(700).somenteTv10Terceiro());
        assertFalse(calcular(misturada, Set.of(3L, 4L)).get(800).somenteTv10Terceiro(), "uma nota comum já tira o 'só terceiro'");
        assertFalse(calcular(tv10OutroCliente, Set.of(5L)).get(900).somenteTv10Terceiro());
    }

    @Test
    void notaSemValorOuPesoNaoQuebra() {
        var notas = List.of(new NotaCarga(500, 1, null, null, null, null, null, false));

        Totais t = calcular(notas, Set.of(1L)).get(500);

        assertEquals(0, BigDecimal.ZERO.compareTo(t.valor()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.pesoKg()));
        assertNull(t.dataUltimaNota());
        assertFalse(t.somenteTv10Terceiro());
    }
}

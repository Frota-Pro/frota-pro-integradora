package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.NotaConferenciaWinThorDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConferenciaNotasWinThorServiceTest {

    private static NotaConferenciaWinThorDto nota(long numero, Integer car) {
        return NotaConferenciaWinThorDto.builder()
                .numNota(numero).serie("1").especie("NF").dtSaida(LocalDate.of(2026, 10, 5))
                .codCli(10).condVenda(1).numCar(car).vlTotal(new BigDecimal("100.00")).build();
    }

    @Test
    void marcaMdfeCarregamentoEPedidoPorNotaEPorCarregamento() {
        var marcadas = ConferenciaNotasWinThorService.marcar(
                List.of(nota(1, 500), nota(2, 500), nota(3, 600)),
                Set.of(1L),
                Set.of(500),
                Set.of(500));

        assertTrue(marcadas.get(0).isTemMdfe());
        assertFalse(marcadas.get(1).isTemMdfe(), "o MDF-e é por nota, não por carregamento");
        assertTrue(marcadas.get(0).isTemCarregamento() && marcadas.get(1).isTemCarregamento());
        assertTrue(marcadas.get(0).isTemPedido());
        assertFalse(marcadas.get(2).isTemCarregamento());
        assertFalse(marcadas.get(2).isTemPedido());
    }

    @Test
    void notaSemCarregamentoNuncaTemCarregamentoNemPedido() {
        // numcar 0/1/nulo são "sem carregamento" no WinThor, mesmo que exista um PCCARREG com esse número
        var marcadas = ConferenciaNotasWinThorService.marcar(
                List.of(nota(1, 0), nota(2, 1), nota(3, null)),
                Set.of(), Set.of(0, 1), Set.of(0, 1));

        marcadas.forEach(n -> {
            assertFalse(n.isTemCarregamento());
            assertFalse(n.isTemPedido());
        });
    }

    @Test
    void marcarPreservaOsDadosDaNota() {
        var original = NotaConferenciaWinThorDto.builder()
                .numNota(7L).serie("2").especie("NF").dtSaida(LocalDate.of(2026, 10, 9)).codCli(33).condVenda(5)
                .numCar(900).numTransVenda(42L).vlTotal(new BigDecimal("12.34")).cancelada(true).temMotoristaVeiculo(true)
                .build();

        var n = ConferenciaNotasWinThorService.marcar(List.of(original), Set.of(7L), Set.of(900), Set.of()).get(0);

        assertEquals(7L, n.getNumNota());
        assertEquals(new BigDecimal("12.34"), n.getVlTotal());
        assertEquals(5, n.getCondVenda());
        assertEquals(42L, n.getNumTransVenda());
        assertTrue(n.isCancelada());
        assertTrue(n.isTemMotoristaVeiculo());
        assertTrue(n.isTemMdfe());
        assertTrue(n.isTemCarregamento());
        assertFalse(n.isTemPedido());
    }

    @Test
    void notaGuardaOManifestoEQuemConstaNele() {
        var mdfe = java.util.Map.of(1L, new ConferenciaNotasWinThorService.MdfeInfo(77L, 905, "123"));

        var marcadas = ConferenciaNotasWinThorService.marcar(List.of(nota(1, 500), nota(2, 500)), mdfe, Set.of(500), Set.of(500));

        assertEquals(77L, marcadas.get(0).getNumMdfe());
        assertEquals(905, marcadas.get(0).getCodMotoristaMdfe());
        assertEquals("123", marcadas.get(0).getCodVeiculoMdfe());
        assertFalse(marcadas.get(1).isTemMdfe());
        assertEquals(null, marcadas.get(1).getNumMdfe());
    }

    @Test
    void notaEmDoisManifestosFicaComOAprovadoEDepoisOMaisRecente() {
        var linhas = List.of(
                new ConferenciaNotasWinThorService.LinhaMdfe(1, 10, 1, "A", "100"),
                new ConferenciaNotasWinThorService.LinhaMdfe(1, 20, 2, "B", "101"),   // mais novo, mas não aprovado
                new ConferenciaNotasWinThorService.LinhaMdfe(2, 30, 3, "C", "100"),
                new ConferenciaNotasWinThorService.LinhaMdfe(2, 40, 4, "D", "100"));  // os dois aprovados: o mais novo

        var escolhidos = ConferenciaNotasWinThorService.escolherMdfePorNota(linhas);

        assertEquals(10L, escolhidos.get(1L).numMdfe());
        assertEquals(40L, escolhidos.get(2L).numMdfe());
    }

    @Test
    void colunasDoManifestoSaoDescobertasEntreOsNomesConhecidos() {
        assertEquals("DTEMISSAO", ColunasManifesto.colunaData(Set.of("NUMMDFE", "DTEMISSAO", "DATA")).orElseThrow());
        assertEquals("TOTPESO", ColunasManifesto.colunaPeso(Set.of("NUMMDFE", "TOTPESO")).orElseThrow());
        assertTrue(ColunasManifesto.colunaData(Set.of("NUMMDFE", "CODMOTORISTA")).isEmpty());
        assertTrue(ColunasManifesto.colunaPeso(Set.of()).isEmpty());
    }

    @Test
    void periodoPrecisaTerInicioFimEmOrdemENaoPassarDoLimite() {
        assertThrows(IllegalArgumentException.class, () -> ConferenciaNotasWinThorService.validarPeriodo(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
                () -> ConferenciaNotasWinThorService.validarPeriodo(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ConferenciaNotasWinThorService.validarPeriodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        ConferenciaNotasWinThorService.validarPeriodo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }
}

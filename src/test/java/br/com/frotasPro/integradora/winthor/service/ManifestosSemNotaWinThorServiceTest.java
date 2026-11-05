package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ManifestoSemNotaDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestosSemNotaWinThorServiceTest {

    private static ManifestoSemNotaDto manifesto(long numero, Integer motorista, String veiculo, String situacao, LocalDate data, String peso) {
        return ManifestoSemNotaDto.builder().numMdfe(numero).codMotorista(motorista).nomeMotorista("JOSE").codVeiculo(veiculo)
                .placa("ABC1D23").situacao(situacao).data(data).pesoKg(peso == null ? null : new BigDecimal(peso)).build();
    }

    private static List<CargaWinThorDto> converter(List<ManifestoSemNotaDto> lista, List<Integer> caminhoes, List<Integer> motoristas) {
        return ManifestosSemNotaWinThorService.comoCargas(lista, Set.of("100", "102"), caminhoes, motoristas);
    }

    @Test
    void manifestoSemNotaViraCargaSemValorSemNumCarComOPesoDoManifesto() {
        var cargas = converter(List.of(manifesto(77, 905, " 123 ", "100", LocalDate.of(2026, 10, 3), "12000")), List.of(), List.of());

        assertEquals(1, cargas.size());
        CargaWinThorDto c = cargas.get(0);
        assertNull(c.getNumCar(), "a chave da carga é o número do manifesto, não um carregamento");
        assertEquals(77L, c.getNumMdfe());
        assertTrue(c.getSemNota());
        assertEquals("123", c.getCodVeiculo());
        assertEquals(905, c.getCodMotorista());
        assertNull(c.getValorTotal(), "manifesto não tem valor");
        assertEquals(12.0, c.getPesoTotalKg(), "12.000 kg do manifesto = 12 toneladas, a unidade da carga");
        assertEquals("MANIFESTO_SEM_NOTA", c.getOrigemCarga());
        assertTrue(c.getClientes().isEmpty());
        assertEquals(LocalDate.of(2026, 10, 3), c.getDtSaida().toLocalDate());
    }

    @Test
    void soValeManifestoAprovadoOuEncerradoCanceladoERejeitadoFicamDeFora() {
        var lista = List.of(
                manifesto(1, 905, "123", "100", LocalDate.of(2026, 10, 3), "10"),     // aprovado (aberto)
                manifesto(2, 905, "123", "102", LocalDate.of(2026, 10, 3), "10"),     // encerrado
                manifesto(3, 905, "123", "101", LocalDate.of(2026, 10, 3), "10"),     // cancelado
                manifesto(4, 905, "123", "228", LocalDate.of(2026, 10, 3), "10"),     // rejeitado pela SEFAZ
                manifesto(5, 905, "123", "0", LocalDate.of(2026, 10, 3), "10"));      // nunca enviado

        assertEquals(List.of(1L, 2L), converter(lista, List.of(), List.of()).stream().map(CargaWinThorDto::getNumMdfe).toList());
        assertEquals(5, ManifestosSemNotaWinThorService.comoCargas(lista, Set.of(), List.of(), List.of()).size(), "conjunto vazio = não filtra");
        assertEquals(2, ManifestosSemNotaWinThorService.apenasValidos(lista, Set.of("100", "102")).size());
    }

    @Test
    void manifestoIncompletoFicaDeFora() {
        var lista = List.of(
                manifesto(2, null, "123", "100", LocalDate.of(2026, 10, 3), "10"),    // sem motorista
                manifesto(3, 905, null, "100", LocalDate.of(2026, 10, 3), "10"),      // sem veículo
                manifesto(4, 905, "123", "100", null, "10"),                          // sem data
                manifesto(5, 905, "123", "100", LocalDate.of(2026, 10, 3), null));    // sem peso: entra, peso nulo

        var cargas = converter(lista, List.of(), List.of());

        assertEquals(1, cargas.size());
        assertEquals(5L, cargas.get(0).getNumMdfe());
        assertNull(cargas.get(0).getPesoTotalKg());
    }

    @Test
    void respeitaOsFiltrosDeCaminhaoEMotoristaDoPedidoDeSync() {
        var lista = List.of(
                manifesto(1, 905, "123", "100", LocalDate.of(2026, 10, 3), "10"),
                manifesto(2, 906, "456", "100", LocalDate.of(2026, 10, 3), "10"));

        assertEquals(List.of(1L), converter(lista, List.of(123), List.of()).stream().map(CargaWinThorDto::getNumMdfe).toList());
        assertEquals(List.of(2L), converter(lista, List.of(), List.of(906)).stream().map(CargaWinThorDto::getNumMdfe).toList());
    }
}

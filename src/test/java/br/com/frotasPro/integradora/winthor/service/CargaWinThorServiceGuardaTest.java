package br.com.frotasPro.integradora.winthor.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CargaWinThorServiceGuardaTest {

    @Test
    void erroNaEtapaNovaNaoDerrubaOSync() {
        assertDoesNotThrow(() -> CargaWinThorService.semDerrubarOSync("teste", () -> {
            throw new org.springframework.dao.DataAccessResourceFailureException("ORA-00942: tabela ou view inexistente");
        }));
    }

    @Test
    void etapaQueDaCertoRodaNormalmente() {
        AtomicInteger chamadas = new AtomicInteger();

        CargaWinThorService.semDerrubarOSync("teste", chamadas::incrementAndGet);

        assertEquals(1, chamadas.get());
    }
}

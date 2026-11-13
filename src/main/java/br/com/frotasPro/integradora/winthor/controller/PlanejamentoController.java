package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.PlanejamentoCarregamentoResponse;
import br.com.frotasPro.integradora.winthor.service.PlanejamentoWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Endpoint interno (consumido pela frota-pro-api) do planejamento de cargas. Só leitura. */
@RestController
@RequestMapping("/winthor/planejamento")
@RequiredArgsConstructor
public class PlanejamentoController {

    private final PlanejamentoWinThorService service;

    /** Carregamentos montados no período (datamon) OU os números informados. */
    @GetMapping("/carregamentos")
    public PlanejamentoCarregamentoResponse carregamentos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(required = false) List<Integer> numCars) {

        boolean temPeriodo = dataInicial != null && dataFinal != null;
        boolean temCarregamentos = numCars != null && !numCars.isEmpty();
        if (!temPeriodo && !temCarregamentos) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe dataInicial e dataFinal, ou numCars.");
        }
        return PlanejamentoCarregamentoResponse.builder()
                .itens(service.buscar(dataInicial, dataFinal, numCars))
                .build();
    }
}

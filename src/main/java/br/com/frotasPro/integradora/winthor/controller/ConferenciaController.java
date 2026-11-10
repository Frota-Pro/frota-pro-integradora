package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.ConferenciaMotoristaCaminhaoResponse;
import br.com.frotasPro.integradora.winthor.service.ConferenciaMotoristaCaminhaoWinThorService;
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

/**
 * Endpoints internos (consumidos pela frota-pro-api) das conferências. Só leitura: nada aqui altera o WinThor
 * nem a sincronização.
 */
@RestController
@RequestMapping("/winthor/conferencias")
@RequiredArgsConstructor
public class ConferenciaController {

    private final ConferenciaMotoristaCaminhaoWinThorService motoristaCaminhaoService;

    /**
     * Carregamentos cujas notas entraram depois da geração do MDF-e e cujo motorista/veículo diferem do manifesto.
     * Informe o período (pela data em que a nota entrou no carregamento) OU os números dos carregamentos.
     */
    @GetMapping("/reentrega-motorista-caminhao")
    public ConferenciaMotoristaCaminhaoResponse reentregaMotoristaCaminhao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(required = false) List<Integer> numCars) {

        boolean temPeriodo = dataInicial != null && dataFinal != null;
        boolean temCarregamentos = numCars != null && !numCars.isEmpty();
        if (!temPeriodo && !temCarregamentos) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe dataInicial e dataFinal, ou numCars.");
        }

        return ConferenciaMotoristaCaminhaoResponse.builder()
                .itens(motoristaCaminhaoService.buscar(dataInicial, dataFinal, numCars))
                .build();
    }
}

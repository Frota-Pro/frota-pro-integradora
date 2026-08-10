package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.DevolucaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.TransferenciaWinThorDto;
import br.com.frotasPro.integradora.winthor.service.DevolucaoTransferenciaWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints internos, consumidos pela frota-pro-api — não expostos
 * diretamente pra internet. Nada aqui fica guardado em disco/banco.
 */
@RestController
@RequestMapping("/winthor/cargas/{numeroCarga}")
@RequiredArgsConstructor
public class DevolucaoTransferenciaController {

    private final DevolucaoTransferenciaWinThorService service;

    @GetMapping("/devolucoes")
    public List<DevolucaoWinThorDto> devolucoes(@PathVariable Integer numeroCarga) {
        return service.buscarDevolucoes(numeroCarga);
    }

    @GetMapping("/transferencias")
    public List<TransferenciaWinThorDto> transferencias(@PathVariable Integer numeroCarga) {
        return service.buscarTransferencias(numeroCarga);
    }
}

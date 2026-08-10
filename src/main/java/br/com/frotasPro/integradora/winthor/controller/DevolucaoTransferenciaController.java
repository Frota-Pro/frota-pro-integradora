package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.DevolucaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.TransferenciaWinThorDto;
import br.com.frotasPro.integradora.winthor.exception.WinThorIndisponivelException;
import br.com.frotasPro.integradora.winthor.service.DevolucaoTransferenciaWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint interno, consumido pela frota-pro-api — não exposto diretamente
 * pra internet. Detalhe (produto a produto / nota a nota) por trás dos
 * resumos que já viajam no sync de carga. Nada aqui fica guardado em
 * disco/banco, buscado ao vivo no WinThor a cada chamada.
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

    @ExceptionHandler(WinThorIndisponivelException.class)
    public ResponseEntity<String> aoFicarIndisponivel(WinThorIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}

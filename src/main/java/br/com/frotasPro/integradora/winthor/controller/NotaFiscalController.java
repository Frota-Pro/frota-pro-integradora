package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.NotaFiscalWinThorDto;
import br.com.frotasPro.integradora.winthor.exception.WinThorIndisponivelException;
import br.com.frotasPro.integradora.winthor.service.DanfeGeneratorService;
import br.com.frotasPro.integradora.winthor.service.NotaFiscalWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Endpoints internos, consumidos pela frota-pro-api — não expostos
 * diretamente pra internet. Só devolvem dados enquanto o carregamento
 * ainda existir no WinThor; nada aqui fica guardado em disco/banco.
 */
@RestController
@RequestMapping("/winthor/cargas/{numeroCarga}/notas")
@RequiredArgsConstructor
public class NotaFiscalController {

    private final NotaFiscalWinThorService notaFiscalWinThorService;
    private final DanfeGeneratorService danfeGeneratorService;

    @GetMapping
    public List<NotaFiscalWinThorDto> listar(
            @PathVariable Integer numeroCarga,
            @RequestParam(value = "codigoCliente", required = false) Integer codigoCliente
    ) {
        return notaFiscalWinThorService.listarNotas(numeroCarga, codigoCliente);
    }

    @GetMapping(value = "/{numeroNota}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> xml(@PathVariable Integer numeroCarga, @PathVariable Long numeroNota) {
        String xml = notaFiscalWinThorService.buscarXmlDaNota(numeroCarga, numeroNota)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "XML não encontrado para esta nota (pode estar cancelada ou não autorizada)."));
        return ResponseEntity.ok(xml);
    }

    @GetMapping(value = "/{numeroNota}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Integer numeroCarga, @PathVariable Long numeroNota) {
        String xml = notaFiscalWinThorService.buscarXmlDaNota(numeroCarga, numeroNota)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "XML não encontrado para esta nota (pode estar cancelada ou não autorizada)."));

        byte[] pdf = danfeGeneratorService.gerarPdf(xml);
        return ResponseEntity.ok(pdf);
    }

    @ExceptionHandler(WinThorIndisponivelException.class)
    public ResponseEntity<String> aoFicarIndisponivel(WinThorIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}

package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.GerarPdfNotaFiscalRequest;
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

import java.util.Base64;
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

    // POST (em vez de GET) porque a logo da empresa vai no corpo — em base64,
    // pode passar bem do limite prático de uma query string.
    @PostMapping(value = "/{numeroNota}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @PathVariable Integer numeroCarga,
            @PathVariable Long numeroNota,
            @RequestBody(required = false) GerarPdfNotaFiscalRequest request
    ) {
        String xml = notaFiscalWinThorService.buscarXmlDaNota(numeroCarga, numeroNota)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "XML não encontrado para esta nota (pode estar cancelada ou não autorizada)."));

        byte[] logoBytes = null;
        String logoBase64 = request != null ? request.getLogoBase64() : null;
        if (logoBase64 != null && !logoBase64.isBlank()) {
            logoBytes = Base64.getDecoder().decode(logoBase64);
        }

        byte[] pdf = danfeGeneratorService.gerarPdf(xml, logoBytes);
        return ResponseEntity.ok(pdf);
    }

    @ExceptionHandler(WinThorIndisponivelException.class)
    public ResponseEntity<String> aoFicarIndisponivel(WinThorIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}

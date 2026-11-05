package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.ConferenciaNotasResponse;
import br.com.frotasPro.integradora.winthor.service.ConferenciaNotasWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Endpoint interno, consumido pela frota-pro-api: as notas do WinThor de um período, com as marcas
 * da conferência do valor transportado. Só leitura.
 */
@RestController
@RequestMapping("/winthor/conferencia")
@RequiredArgsConstructor
public class ConferenciaNotasController {

    private final ConferenciaNotasWinThorService service;

    @GetMapping("/notas")
    public ResponseEntity<?> notas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            return ResponseEntity.ok(ConferenciaNotasResponse.builder()
                    .inicio(inicio)
                    .fim(fim)
                    .notas(service.buscarNotas(inicio, fim))
                    .transferencias(service.buscarTransferencias(inicio, fim))
                    .manifestosSemNota(service.buscarManifestosSemNota(inicio, fim))
                    .devolucoes(service.buscarDevolucoes(inicio, fim))
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}

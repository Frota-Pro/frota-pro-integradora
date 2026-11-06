package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.service.LogArquivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno, consumido pela frota-pro-api para mostrar os logs da integradora na tela de Logs.
 * Protegido pelo mesmo token das demais rotas (X-Integradora-Token).
 */
@RestController
@RequestMapping("/winthor/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogArquivoService logArquivoService;

    /** Últimas linhas, uma por linha, em texto puro. arquivo = PRINCIPAL, SINCRONIZACAO ou ERROS. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String ultimasLinhas(
            @RequestParam(value = "arquivo", defaultValue = "PRINCIPAL") String arquivo,
            @RequestParam(value = "linhas", defaultValue = "300") int linhas
    ) {
        return String.join("\n", logArquivoService.ultimasLinhas(arquivo, linhas));
    }
}

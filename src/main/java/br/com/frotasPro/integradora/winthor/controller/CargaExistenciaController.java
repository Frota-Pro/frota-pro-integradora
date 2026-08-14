package br.com.frotasPro.integradora.winthor.controller;

import br.com.frotasPro.integradora.winthor.dto.VerificarExistenciaCargasRequest;
import br.com.frotasPro.integradora.winthor.dto.VerificarExistenciaCargasResponse;
import br.com.frotasPro.integradora.winthor.service.CargaWinThorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno, consumido pela frota-pro-api — reconciliação de cargas
 * já sincronizadas que podem ter sido apagadas/desvinculadas no WinThor
 * depois (ex.: pedido cancelado, minuta refeita). Sem isso, uma carga que
 * some do WinThor continua parecendo válida no FrotaPRO indefinidamente.
 */
@RestController
@RequestMapping("/winthor/cargas")
@RequiredArgsConstructor
public class CargaExistenciaController {

    private final CargaWinThorService cargaWinThorService;

    @PostMapping("/verificar-existencia")
    public VerificarExistenciaCargasResponse verificarExistencia(@RequestBody VerificarExistenciaCargasRequest request) {
        var existentes = cargaWinThorService.filtrarExistentes(request.getCodigosCargas());
        return VerificarExistenciaCargasResponse.builder()
                .codigosExistentes(existentes)
                .build();
    }
}

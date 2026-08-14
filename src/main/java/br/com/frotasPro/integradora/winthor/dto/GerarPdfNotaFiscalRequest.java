package br.com.frotasPro.integradora.winthor.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Corpo opcional do pedido de PDF (DANFE) — carrega a logo da empresa (se
 * configurada no frota-pro-api) pra ser embutida no documento.
 */
@Getter
@Setter
public class GerarPdfNotaFiscalRequest {

    private String logoBase64;
}

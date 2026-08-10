package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Um registro de transferência de pedido entre carregamentos — mesmo shape
 * do TransferenciaResponse da frota-pro-api.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferenciaWinThorDto {

    private Long numNota;
    private Integer numCarAtual;
    private Integer numCarAnterior;
    private OffsetDateTime dtTransferencia;
    private String motivo;

    /** "PERDIDA" (essa carga perdeu o pedido) ou "RECEBIDA" (essa carga recebeu o pedido). */
    private String direcao;
}

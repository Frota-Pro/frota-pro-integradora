package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Um registro de transferência de pedido entre carregamentos
 * (PCLOGTRANSFNFCARREG), relativo ao numCar consultado.
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

    /**
     * "PERDIDA" quando o numCar consultado é o numCarAnterior (esse
     * carregamento perdeu o pedido pro numCarAtual). "RECEBIDA" quando o
     * numCar consultado é o numCarAtual (esse carregamento recebeu o pedido
     * que veio do numCarAnterior).
     */
    private String direcao;

    /** Peso (kg) e valor (R$) da nota transferida, vindos de pcnfsaid.totpeso/vltotal. */
    private BigDecimal pesoKg;
    private BigDecimal valorTotal;
}

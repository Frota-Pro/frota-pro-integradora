package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uma linha do log de transferência de nota entre carregamentos do WinThor (PCLOGTRANSFNFCARREG):
 * a nota saiu de {@code numCarAnterior} e foi para {@code numCarAtual}, naquele dia e hora, por aquele motivo.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferenciaNotaDto {

    private Long numNota;
    private Integer numCarAnterior;
    private Integer numCarAtual;
    private LocalDateTime dtTransferencia;
    /** Ex.: "TRANSF. DE PEDIDO ENTRE CARGAS", "PEDIDO PARA REENTREGA". */
    private String motivo;
}

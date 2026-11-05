package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Devolução (valor e peso) de uma nota de saída, somada de todos os produtos devolvidos. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevolucaoNotaDto {

    private Long numNota;
    private BigDecimal valor;
    private BigDecimal pesoKg;
}

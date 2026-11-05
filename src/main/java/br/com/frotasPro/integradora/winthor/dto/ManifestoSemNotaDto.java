package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manifesto (MDF-e) da filial 1 que não tem nenhuma nota: o caminhão saiu, mas não há valor, só o peso do
 * manifesto. É viagem real quando o caminhão é da frota.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestoSemNotaDto {

    private Long numMdfe;
    private Integer codMotorista;
    private String codVeiculo;
    private String situacao;
    private LocalDate data;
    /** Peso informado no manifesto, em kg; nulo se a coluna do peso não foi identificada. */
    private BigDecimal pesoKg;
}

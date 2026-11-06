package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargasReentregaGenericaResponse {

    /** Dos códigos perguntados, quais estão num carregamento com motorista/veículo genérico de reentrega. */
    private List<Integer> codigosReentrega;
}

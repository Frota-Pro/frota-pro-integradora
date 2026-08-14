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
public class VerificarExistenciaCargasResponse {

    /** Dos códigos perguntados, quais ainda existem (têm nota fiscal vinculada) no WinThor. */
    private List<Integer> codigosExistentes;
}

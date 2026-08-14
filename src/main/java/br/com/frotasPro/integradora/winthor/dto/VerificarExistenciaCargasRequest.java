package br.com.frotasPro.integradora.winthor.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class VerificarExistenciaCargasRequest {

    private List<Integer> codigosCargas;
}

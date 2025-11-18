package br.com.frotasPro.integradora.winthor.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClienteCargaWinThorDto {

    private Integer codCli;
    private String nomeCli;
    private List<Long> notas;
}

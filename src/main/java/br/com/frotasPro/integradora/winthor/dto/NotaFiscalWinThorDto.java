package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaFiscalWinThorDto {

    private Long numeroNota;
    private String serie;
    private Integer codigoCliente;
    private String nomeCliente;

    /** E-mail cadastrado do cliente no WinThor (pcclient.emailnfe), se houver. */
    private String emailCliente;

    /** XML autorizado da NF-e (CLOB do PCDOCELETRONICO). Só preenchido quando pedido explicitamente. */
    private String xml;
}

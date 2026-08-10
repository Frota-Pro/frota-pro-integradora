package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Um item devolvido (uma linha de produto) de uma nota fiscal de um
 * carregamento — mesmo shape do DevolucaoResponse da frota-pro-api (o nome
 * dos campos precisa bater, é desserializado por JSON puro via RestTemplate).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevolucaoWinThorDto {

    private Integer codDevolucao;
    private Long numNota;
    private Long numPedido;
    private OffsetDateTime dtEntrada;
    private String motivo;

    private Integer codCliente;
    private String nomeCliente;

    private Integer codProduto;
    private String descricaoProduto;
    private BigDecimal quantidade;
    private String unidade;
    private String embalagem;

    private BigDecimal valorDevolucao;
    private Double pesoTotalKg;

    private String fornecedor;
    private String nomeMotoristaDevolucao;
}

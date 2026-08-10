package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CargaWinThorDto {

    private Long numMdfe;
    private Integer numCar;

    private String codVeiculo;
    private Integer codMotorista;

    private OffsetDateTime dtSaida;
    private String destino;

    private Double pesoTotalKg;
    private BigDecimal valorTotal;
    private String situacaoMdfe;

    private Integer totalClientes;
    private List<ClienteCargaWinThorDto> clientes;

    /**
     * Códigos de devolução (CODDEVOL, da view_devol_resumo_faturamento) encontrados
     * pra notas desse carregamento, sem duplicar. Vazio = nenhuma devolução associada.
     */
    private List<String> codigosDevolucao;

    /**
     * true se esse carregamento aparece como NUMCARANTERIOR em PCLOGTRANSFNFCARREG,
     * ou seja, perdeu pedido(s) transferido(s) pra outro carregamento.
     */
    private Boolean temTransferencia;
}

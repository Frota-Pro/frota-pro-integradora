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
    /** Nulo quando é manifesto sem nota ({@link #semNota}): a chave da carga é então o número do manifesto. */
    private Integer numCar;

    /** Manifesto sem nenhuma nota: viagem sem valor, só com o peso do manifesto. */
    private Boolean semNota;

    private String codVeiculo;
    private Integer codMotorista;

    /** Só informativos (relatório de cargas descartadas no sync) — o vínculo é sempre pelo código. */
    private String nomeMotorista;
    private String placaVeiculo;

    private OffsetDateTime dtSaida;
    private String destino;

    /** Apesar do nome, em TONELADAS (PCCARREG.TOTPESO / 1000): é a unidade de Carga.pesoCarga. */
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

    /**
     * Como o carregamento nasceu, pelo log de transferência: "REENTREGA" (pedido que voltou e ganhou carga
     * própria) ou "TRANSFERENCIA" (pedido passado de outra carga). Nulo = carga comum.
     */
    private String origemCarga;
    /** Carregamento de onde veio o primeiro pedido, quando {@link #origemCarga} está preenchida. */
    private Integer origemNumCar;

    /** Quantas notas do carregamento são transporte (não canceladas e com MDF-e). */
    private Integer notasTransportadas;
    /**
     * Todas as notas transportadas são TV10 de cliente cujo transporte é de terceiro (15575, 10073):
     * motorista ou caminhão podem não existir no FrotaPRO.
     */
    private Boolean somenteTv10Terceiro;
}

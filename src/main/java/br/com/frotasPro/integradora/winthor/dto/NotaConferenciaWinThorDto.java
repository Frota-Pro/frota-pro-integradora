package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma nota de saída do WinThor com tudo o que a conferência do "valor transportado" precisa saber:
 * a que carregamento pertence e se passou em cada exigência pra virar carga no FrotaPRO.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaConferenciaWinThorDto {

    private Long numNota;
    private String serie;
    private String especie;
    private LocalDate dtSaida;
    private Integer codCli;
    private Integer condVenda;
    /** Carregamento da nota; 0/1/nulo = sem carregamento. */
    private Integer numCar;
    private Long numTransVenda;
    private BigDecimal vlTotal;

    /** Nota cancelada (DTCANCEL preenchida): nunca conta como transportada. */
    private boolean cancelada;
    /** A nota tem motorista e veículo informados. */
    private boolean temMotoristaVeiculo;
    /** A nota consta num MDF-e da filial 1. */
    private boolean temMdfe;
    /** O carregamento tem pedido (PCPEDC). */
    private boolean temPedido;
    /** O carregamento existe em PCCARREG. */
    private boolean temCarregamento;

    /** MDF-e (da filial 1) em que a nota viajou e quem consta nele; nulos se a nota não está em manifesto. */
    private Long numMdfe;
    private Integer codMotoristaMdfe;
    private String codVeiculoMdfe;
}

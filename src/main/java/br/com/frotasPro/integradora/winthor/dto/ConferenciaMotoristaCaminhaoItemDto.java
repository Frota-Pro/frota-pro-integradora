package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Um carregamento em que as notas entraram DEPOIS de o MDF-e ser gerado e o motorista/veículo do carregamento
 * (pccarreg) é diferente do motorista/veículo do manifesto. É só informação para o usuário conferir.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConferenciaMotoristaCaminhaoItemDto {

    private Integer numCar;

    private Long numMdfe;
    private String situacaoMdfe;
    private LocalDateTime dataGeracaoMdfe;

    /** Motorista e veículo DO MANIFESTO (é o que a sincronização usa hoje). */
    private Integer codMotoristaMdfe;
    private String nomeMotoristaMdfe;
    private String codVeiculoMdfe;
    private String placaMdfe;

    /** Motorista e veículo DO CARREGAMENTO (pccarreg). */
    private Integer codMotoristaCarregamento;
    private String nomeMotoristaCarregamento;
    private String codVeiculoCarregamento;
    private String placaCarregamento;

    private boolean motoristaDiferente;
    private boolean veiculoDiferente;

    /** As notas deste manifesto que entraram no carregamento depois da geração do MDF-e. */
    private List<TransferenciaNotaDto> transferencias;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferenciaNotaDto {
        private Long numNota;
        private Integer numCarAnterior;
        private Integer numCarAtual;
        private LocalDateTime dataTransferencia;
        private String motivo;
    }
}

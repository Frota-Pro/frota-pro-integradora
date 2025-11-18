package br.com.frotasPro.integradora.winthor.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CargaSyncResponseEvent {

    private UUID jobId;
    private UUID empresaId;

    private LocalDate dataReferencia;
    private Integer totalCargas;

    private List<CargaWinThorDto> cargas;

    private OffsetDateTime timestampProcessado;
}

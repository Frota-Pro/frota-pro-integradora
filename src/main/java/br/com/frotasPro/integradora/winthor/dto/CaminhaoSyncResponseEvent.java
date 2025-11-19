package br.com.frotasPro.integradora.winthor.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaminhaoSyncResponseEvent {

    private UUID jobId;
    private UUID empresaId;

    private List<CaminhaoWinThorDto> caminhoes;

    private OffsetDateTime timestampProcessado;
}

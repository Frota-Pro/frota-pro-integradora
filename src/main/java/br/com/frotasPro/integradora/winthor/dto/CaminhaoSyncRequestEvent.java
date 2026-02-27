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
public class CaminhaoSyncRequestEvent {

    private UUID jobId;
    private UUID empresaId;

    private Integer codFilial;
    private List<Integer> codigosCaminhoes;

    private OffsetDateTime timestampSolicitacao;
}

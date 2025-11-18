package br.com.frotasPro.integradora.winthor.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class CargaSyncRequestEvent {

    private UUID jobId;
    private UUID empresaId;

    private LocalDate dataInicial;
    private LocalDate dataFinal;

    private String tipoCarga;
    private String origem;
    private String solicitadoPor;

    private OffsetDateTime timestampSolicitacao;
}

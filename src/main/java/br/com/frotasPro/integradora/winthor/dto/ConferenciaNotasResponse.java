package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConferenciaNotasResponse {

    private LocalDate inicio;
    private LocalDate fim;
    /**
     * Notas emitidas no período (todas, inclusive sem carregamento) mais as demais notas dos
     * carregamentos que tiveram nota no período, mesmo emitidas em outro mês: o valor de uma
     * carga é a soma de todas as notas dela.
     */
    private List<NotaConferenciaWinThorDto> notas;
    /**
     * Log de transferência entre carregamentos das notas acima, de qualquer data (uma nota de outubro
     * pode ter mudado de carga em novembro). Em ordem cronológica.
     */
    private List<TransferenciaNotaDto> transferencias;
    /** Manifestos do período sem nenhuma nota (viagem sem valor, só com o peso do manifesto). */
    private List<ManifestoSemNotaDto> manifestosSemNota;
    /** Devoluções (valor e peso) das notas do período. */
    private List<DevolucaoNotaDto> devolucoes;
}

package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CargaSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.CargaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.service.CargaWinThorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CargaSyncRequestConsumer {

    private final CargaWinThorService cargaWinThorService;
    private final CargaSyncResponseProducer responseProducer;

    @KafkaListener(
            topics = "${frotapro.kafka.topics.carga-sync-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumir(CargaSyncRequestEvent event) {
        log.info("📥 [INTEGRADORA] Pedido de sync recebido. jobId={} dataInicial={} dataFinal={}",
                event.getJobId(), event.getDataInicial(), event.getDataFinal());

        var cargas = cargaWinThorService.buscarCargasFaturadas(
                event.getEmpresaId(), event.getDataInicial(), event.getDataFinal()
        );

        CargaSyncResponseEvent response = CargaSyncResponseEvent.builder()
                .jobId(event.getJobId())
                .empresaId(event.getEmpresaId())
                .dataReferencia(event.getDataInicial())
                .totalCargas(cargas.size())
                .cargas(cargas)
                .timestampProcessado(OffsetDateTime.now())
                .build();

        responseProducer.enviar(response);
    }
}



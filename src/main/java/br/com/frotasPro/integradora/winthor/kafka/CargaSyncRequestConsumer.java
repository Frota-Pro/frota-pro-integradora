package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CargaSyncRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CargaSyncRequestConsumer {

    @Value("${frotapro.kafka.topics.carga-sync-request}")
    private String topic;

    @KafkaListener(
            topics = "${frotapro.kafka.topics.carga-sync-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumir(CargaSyncRequestEvent event) {
        log.info("📥 Recebido pedido de sync de cargas. jobId={} dataInicial={} dataFinal={}",
                event.getJobId(), event.getDataInicial(), event.getDataFinal());

        // Aqui depois vamos:
        // 1) Consultar Oracle (WinThor)
        // 2) Montar lista de CargaWinThorDto
        // 3) Publicar CargaSyncResponseEvent no Kafka
    }
}

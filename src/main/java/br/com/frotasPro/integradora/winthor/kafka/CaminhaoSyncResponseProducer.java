package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaminhaoSyncResponseProducer {

    private final KafkaTemplate<String, CaminhaoSyncResponseEvent> kafkaTemplate;

    @Value("${frotapro.kafka.topics.caminhao-sync-response}")
    private String topic;

    public void enviar(CaminhaoSyncResponseEvent event) {
        kafkaTemplate.send(topic, event.getJobId().toString(), event);
        log.info("📤 [INTEGRADORA] Enviando resposta de sync de caminhões. jobId={} total={}",
                event.getJobId(), event.getCaminhoes().size());
    }
}

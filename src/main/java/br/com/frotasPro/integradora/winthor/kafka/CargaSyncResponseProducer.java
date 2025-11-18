package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CargaSyncResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CargaSyncResponseProducer {

    private final KafkaTemplate<String, CargaSyncResponseEvent> kafkaTemplate;

    @Value("${frotapro.kafka.topics.carga-sync-response}")
    private String topic;

    public void enviar(CargaSyncResponseEvent event) {
        log.info("📤 Enviando resposta de sync de cargas. jobId={} totalCargas={}",
                event.getJobId(), event.getTotalCargas());

        kafkaTemplate.send(topic, event.getJobId().toString(), event);
    }
}

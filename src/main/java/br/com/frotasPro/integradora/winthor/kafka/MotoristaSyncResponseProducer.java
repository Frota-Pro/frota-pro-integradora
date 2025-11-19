package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MotoristaSyncResponseProducer {

    private final KafkaTemplate<String, MotoristaSyncResponseEvent> kafkaTemplate;

    @Value("${frotapro.kafka.topics.motorista-sync-response}")
    private String topic;

    public void enviar(MotoristaSyncResponseEvent event) {
        log.info("📤 [INTEGRADORA] Enviando resposta de sync de motoristas. jobId={} total={}",
                event.getJobId(), event.getMotoristas().size());

        kafkaTemplate.send(topic, event.getJobId().toString(), event);
    }
}

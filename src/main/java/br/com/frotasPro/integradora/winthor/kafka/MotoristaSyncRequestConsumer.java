package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaWinThorDto;
import br.com.frotasPro.integradora.winthor.service.MotoristaWinThorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MotoristaSyncRequestConsumer {

    private final MotoristaWinThorService motoristaWinThorService;
    private final MotoristaSyncResponseProducer responseProducer;

    @KafkaListener(
            topics = "${frotapro.kafka.topics.motorista-sync-request}",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type=br.com.frotasPro.integradora.winthor.dto.MotoristaSyncRequestEvent"
            }
    )
    public void consumir(MotoristaSyncRequestEvent event) {

        log.info("📥 [INTEGRADORA] Pedido de sync de motoristas recebido. jobId={} empresaId={}",
                event.getJobId(), event.getEmpresaId());

        // aqui, se você quiser, pode mapear empresaId -> filial WinThor
        Integer codFilial = null; // por enquanto, null = todos

        List<MotoristaWinThorDto> motoristas = motoristaWinThorService.buscarMotoristas(codFilial);

        MotoristaSyncResponseEvent response = MotoristaSyncResponseEvent.builder()
                .jobId(event.getJobId())
                .empresaId(event.getEmpresaId())
                .motoristas(motoristas)
                .timestampProcessado(OffsetDateTime.now())
                .build();

        responseProducer.enviar(response);
    }
}

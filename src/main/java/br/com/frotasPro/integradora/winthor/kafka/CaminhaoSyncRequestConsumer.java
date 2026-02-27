package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.CaminhaoWinThorDto;
import br.com.frotasPro.integradora.winthor.service.CaminhaoWinThorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaminhaoSyncRequestConsumer {

    private final CaminhaoWinThorService caminhaoWinThorService;
    private final CaminhaoSyncResponseProducer responseProducer;

    @KafkaListener(
            topics = "${frotapro.kafka.topics.caminhao-sync-request}",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = {
                    "spring.json.value.default.type=br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncRequestEvent"
            }
    )
    public void consumir(CaminhaoSyncRequestEvent event) {

        log.info("📥 [INTEGRADORA] Pedido de sync de caminhões recebido. jobId={} empresaId={}",
                event.getJobId(), event.getEmpresaId());

        Integer codFilial = event.getCodFilial();
        var codigosCaminhoes = event.getCodigosCaminhoes();

        List<CaminhaoWinThorDto> caminhoes = caminhaoWinThorService.buscarCaminhoes(codFilial, codigosCaminhoes);

        CaminhaoSyncResponseEvent response = CaminhaoSyncResponseEvent.builder()
                .jobId(event.getJobId())
                .empresaId(event.getEmpresaId())
                .caminhoes(caminhoes)
                .timestampProcessado(OffsetDateTime.now())
                .build();

        responseProducer.enviar(response);
    }
}

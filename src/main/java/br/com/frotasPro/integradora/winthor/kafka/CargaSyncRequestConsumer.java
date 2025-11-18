package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CargaSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.CargaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.ClienteCargaWinThorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CargaSyncRequestConsumer {

    private final CargaSyncResponseProducer responseProducer;

    @KafkaListener(
            topics = "${frotapro.kafka.topics.carga-sync-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumir(CargaSyncRequestEvent event) {
        log.info("📥 [INTEGRADORA] Pedido de sync recebido. jobId={} dataInicial={} dataFinal={}",
                event.getJobId(), event.getDataInicial(), event.getDataFinal());

        // 👉 Aqui, por enquanto, vamos montar uma resposta fake:

        ClienteCargaWinThorDto cliente = ClienteCargaWinThorDto.builder()
                .codCli(1001)
                .nomeCli("CLIENTE FAKE LTDA")
                .notas(List.of(123456L, 123457L))
                .build();

        CargaWinThorDto carga = CargaWinThorDto.builder()
                .numMdfe(99999L)
                .numCar(123)
                .codVeiculo("TRK-001")
                .codMotorista(321)
                .dtSaida(OffsetDateTime.now())
                .destino("FORTALEZA - CE")
                .pesoTotalKg(10250.0)
                .situacaoMdfe("AUTORIZADO")
                .totalClientes(1)
                .clientes(List.of(cliente))
                .build();

        CargaSyncResponseEvent response = CargaSyncResponseEvent.builder()
                .jobId(event.getJobId())
                .empresaId(event.getEmpresaId())
                .dataReferencia(event.getDataInicial())
                .totalCargas(1)
                .cargas(List.of(carga))
                .timestampProcessado(OffsetDateTime.now())
                .build();

        responseProducer.enviar(response);
    }
}


package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.CaminhaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.CargaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaWinThorDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/** As respostas voltam para a API no topico certo, com o jobId como chave da mensagem. */
@ExtendWith(MockitoExtension.class)
class SyncResponseProducersTest {

    @Mock
    private KafkaTemplate<String, CargaSyncResponseEvent> cargaTemplate;
    @Mock
    private KafkaTemplate<String, MotoristaSyncResponseEvent> motoristaTemplate;
    @Mock
    private KafkaTemplate<String, CaminhaoSyncResponseEvent> caminhaoTemplate;

    @Test
    void respostaDeCargasVaiNoTopicoDeCargasComOJobIdComoChave() {
        CargaSyncResponseProducer producer = new CargaSyncResponseProducer(cargaTemplate);
        ReflectionTestUtils.setField(producer, "topic", "frotapro.carga.sync.response");
        UUID job = UUID.randomUUID();
        CargaSyncResponseEvent evento = CargaSyncResponseEvent.builder().jobId(job).totalCargas(5).build();

        producer.enviar(evento);

        verify(cargaTemplate).send("frotapro.carga.sync.response", job.toString(), evento);
    }

    @Test
    void respostaDeMotoristasVaiNoTopicoDeMotoristasComOJobIdComoChave() {
        MotoristaSyncResponseProducer producer = new MotoristaSyncResponseProducer(motoristaTemplate);
        ReflectionTestUtils.setField(producer, "topic", "frotapro.motorista.sync.response");
        UUID job = UUID.randomUUID();
        MotoristaSyncResponseEvent evento = MotoristaSyncResponseEvent.builder()
                .jobId(job).motoristas(List.of(new MotoristaWinThorDto())).build();

        producer.enviar(evento);

        verify(motoristaTemplate).send("frotapro.motorista.sync.response", job.toString(), evento);
    }

    @Test
    void respostaDeCaminhoesVaiNoTopicoDeCaminhoesComOJobIdComoChave() {
        CaminhaoSyncResponseProducer producer = new CaminhaoSyncResponseProducer(caminhaoTemplate);
        ReflectionTestUtils.setField(producer, "topic", "frotapro.caminhao.sync.response");
        UUID job = UUID.randomUUID();
        CaminhaoSyncResponseEvent evento = CaminhaoSyncResponseEvent.builder()
                .jobId(job).caminhoes(List.of(new CaminhaoWinThorDto())).build();

        producer.enviar(evento);

        verify(caminhaoTemplate).send("frotapro.caminhao.sync.response", job.toString(), evento);
    }
}

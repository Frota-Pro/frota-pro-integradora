package br.com.frotasPro.integradora.winthor.kafka;

import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.CaminhaoSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.CaminhaoWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.CargaSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.CargaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.CargaWinThorDto;
import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncRequestEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaSyncResponseEvent;
import br.com.frotasPro.integradora.winthor.dto.MotoristaWinThorDto;
import br.com.frotasPro.integradora.winthor.service.CaminhaoWinThorService;
import br.com.frotasPro.integradora.winthor.service.CargaWinThorService;
import br.com.frotasPro.integradora.winthor.service.MotoristaWinThorService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Consumidores dos pedidos de sync que a API manda pelo Kafka: consultam o WinThor com
 * o filtro do pedido e devolvem a resposta com o mesmo jobId.
 */
class SyncRequestConsumersTest {

    private static final UUID JOB = UUID.randomUUID();
    private static final UUID EMPRESA = UUID.randomUUID();

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Cargas {
        @Mock
        CargaWinThorService service;
        @Mock
        CargaSyncResponseProducer producer;
        @InjectMocks
        CargaSyncRequestConsumer consumer;

        @Test
        void consultaOWinThorComOsFiltrosDoPedidoEDevolveARespostaComOMesmoJob() {
            LocalDate ini = LocalDate.of(2026, 10, 14);
            LocalDate fim = LocalDate.of(2026, 10, 20);
            List<CargaWinThorDto> cargas = List.of(new CargaWinThorDto(), new CargaWinThorDto(), new CargaWinThorDto());
            when(service.buscarCargasFaturadas(EMPRESA, ini, fim, List.of(1, 2), List.of(10), List.of(20, 21))).thenReturn(cargas);

            consumer.consumir(CargaSyncRequestEvent.builder()
                    .jobId(JOB).empresaId(EMPRESA).dataInicial(ini).dataFinal(fim)
                    .codigosCargas(List.of(1, 2)).codigosCaminhoes(List.of(10)).codigosMotoristas(List.of(20, 21))
                    .build());

            ArgumentCaptor<CargaSyncResponseEvent> resposta = ArgumentCaptor.forClass(CargaSyncResponseEvent.class);
            verify(producer).enviar(resposta.capture());
            assertEquals(JOB, resposta.getValue().getJobId());
            assertEquals(EMPRESA, resposta.getValue().getEmpresaId());
            assertEquals(ini, resposta.getValue().getDataReferencia());
            assertEquals(3, resposta.getValue().getTotalCargas());
            assertSame(cargas, resposta.getValue().getCargas());
            assertNotNull(resposta.getValue().getTimestampProcessado());
        }

        @Test
        void semCargasNoPeriodoAindaResponde() {
            when(service.buscarCargasFaturadas(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            consumer.consumir(CargaSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA)
                    .dataInicial(LocalDate.of(2026, 10, 21)).dataFinal(LocalDate.of(2026, 10, 21)).build());

            ArgumentCaptor<CargaSyncResponseEvent> resposta = ArgumentCaptor.forClass(CargaSyncResponseEvent.class);
            verify(producer).enviar(resposta.capture());
            assertEquals(0, resposta.getValue().getTotalCargas());
        }

        @Test
        void seOWinThorFalharNenhumaRespostaESaiEOErroSobe() {
            when(service.buscarCargasFaturadas(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("Oracle fora do ar"));

            assertThrows(IllegalStateException.class, () -> consumer.consumir(
                    CargaSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA).build()));

            verifyNoInteractions(producer);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Motoristas {
        @Mock
        MotoristaWinThorService service;
        @Mock
        MotoristaSyncResponseProducer producer;
        @InjectMocks
        MotoristaSyncRequestConsumer consumer;

        @Test
        void buscaOsMotoristasPedidosDeTodasAsFiliais() {
            List<MotoristaWinThorDto> motoristas = List.of(new MotoristaWinThorDto(), new MotoristaWinThorDto());
            when(service.buscarMotoristas(null, List.of(28, 30))).thenReturn(motoristas);

            consumer.consumir(MotoristaSyncRequestEvent.builder()
                    .jobId(JOB).empresaId(EMPRESA).codigosMotoristas(List.of(28, 30)).build());

            ArgumentCaptor<MotoristaSyncResponseEvent> resposta = ArgumentCaptor.forClass(MotoristaSyncResponseEvent.class);
            verify(producer).enviar(resposta.capture());
            assertEquals(JOB, resposta.getValue().getJobId());
            assertEquals(EMPRESA, resposta.getValue().getEmpresaId());
            assertSame(motoristas, resposta.getValue().getMotoristas());
            assertNotNull(resposta.getValue().getTimestampProcessado());
        }

        @Test
        void semListaDeCodigosPedeTodosOsMotoristas() {
            when(service.buscarMotoristas(null, null)).thenReturn(List.of());

            consumer.consumir(MotoristaSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA).build());

            verify(service).buscarMotoristas(null, null);
            verify(producer).enviar(any());
        }

        @Test
        void seOWinThorFalharNenhumaRespostaESai() {
            when(service.buscarMotoristas(any(), any())).thenThrow(new IllegalStateException("Oracle fora do ar"));

            assertThrows(IllegalStateException.class, () -> consumer.consumir(
                    MotoristaSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA).build()));

            verifyNoInteractions(producer);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Caminhoes {
        @Mock
        CaminhaoWinThorService service;
        @Mock
        CaminhaoSyncResponseProducer producer;
        @InjectMocks
        CaminhaoSyncRequestConsumer consumer;

        @Test
        void repassaAFilialEOsCodigosDoPedido() {
            List<CaminhaoWinThorDto> caminhoes = List.of(new CaminhaoWinThorDto());
            when(service.buscarCaminhoes(3, List.of(100, 101))).thenReturn(caminhoes);

            consumer.consumir(CaminhaoSyncRequestEvent.builder()
                    .jobId(JOB).empresaId(EMPRESA).codFilial(3).codigosCaminhoes(List.of(100, 101)).build());

            ArgumentCaptor<CaminhaoSyncResponseEvent> resposta = ArgumentCaptor.forClass(CaminhaoSyncResponseEvent.class);
            verify(producer).enviar(resposta.capture());
            assertEquals(JOB, resposta.getValue().getJobId());
            assertSame(caminhoes, resposta.getValue().getCaminhoes());
        }

        @Test
        void semFilialPedeDeTodas() {
            when(service.buscarCaminhoes(null, null)).thenReturn(List.of());

            consumer.consumir(CaminhaoSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA).build());

            verify(service).buscarCaminhoes(null, null);
        }

        @Test
        void seOWinThorFalharNenhumaRespostaESai() {
            when(service.buscarCaminhoes(any(), any())).thenThrow(new IllegalStateException("Oracle fora do ar"));

            assertThrows(IllegalStateException.class, () -> consumer.consumir(
                    CaminhaoSyncRequestEvent.builder().jobId(JOB).empresaId(EMPRESA).build()));

            verifyNoInteractions(producer);
        }
    }
}

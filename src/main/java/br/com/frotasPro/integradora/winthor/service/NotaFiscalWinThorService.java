package br.com.frotasPro.integradora.winthor.service;

import br.com.frotasPro.integradora.winthor.dto.NotaFiscalWinThorDto;
import br.com.frotasPro.integradora.winthor.exception.WinThorIndisponivelException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Busca sob demanda de notas fiscais (e o XML já autorizado pela SEFAZ)
 * vinculadas a um carregamento no WinThor. Usa um pool de conexão próprio,
 * pequeno, protegido por circuit breaker + bulkhead — nunca o pool do job
 * de sincronização em lote.
 */
@Slf4j
@Service
public class NotaFiscalWinThorService {

    private static final String RESILIENCE_INSTANCE = "winthorInterativo";

    private final JdbcTemplate jdbcTemplate;

    public NotaFiscalWinThorService(@Qualifier("winThorInterativoJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackListarNotas")
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public List<NotaFiscalWinThorDto> listarNotas(Integer numeroCarga, Integer codigoCliente) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                nf.numnota,
                nf.serie,
                nf.codcli,
                cli.cliente AS nome_cliente,
                cli.emailnfe AS email_cliente
            FROM pcnfsaid nf
            JOIN pcclient cli ON cli.codcli = nf.codcli
            JOIN pcdoceletronico doc ON doc.numtransacao = nf.numtransvenda
            WHERE nf.numcar = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(numeroCarga);

        if (codigoCliente != null) {
            sql.append(" AND nf.codcli = ?\n");
            params.add(codigoCliente);
        }

        sql.append(" ORDER BY cli.cliente, nf.numnota");

        log.info("Buscando notas fiscais do carregamento {} (cliente={})", numeroCarga, codigoCliente);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
                NotaFiscalWinThorDto.builder()
                        .numeroNota(rs.getLong("numnota"))
                        .serie(rs.getString("serie"))
                        .codigoCliente(rs.getInt("codcli"))
                        .nomeCliente(rs.getString("nome_cliente"))
                        .emailCliente(rs.getString("email_cliente"))
                        .build()
        );
    }

    @SuppressWarnings("unused")
    private List<NotaFiscalWinThorDto> fallbackListarNotas(Integer numeroCarga, Integer codigoCliente, Throwable t) {
        throw indisponivel(t);
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallbackBuscarXml")
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public Optional<String> buscarXmlDaNota(Integer numeroCarga, Long numeroNota) {
        String sql = """
            SELECT doc.xmlnfe
            FROM pcnfsaid nf
            JOIN pcdoceletronico doc ON doc.numtransacao = nf.numtransvenda
            WHERE nf.numcar = ?
              AND nf.numnota = ?
            """;

        log.info("Buscando XML da nota {} do carregamento {}", numeroNota, numeroCarga);

        List<String> resultado = jdbcTemplate.query(sql, new Object[]{numeroCarga, numeroNota},
                (rs, rowNum) -> rs.getString("xmlnfe"));

        return resultado.stream().findFirst();
    }

    @SuppressWarnings("unused")
    private Optional<String> fallbackBuscarXml(Integer numeroCarga, Long numeroNota, Throwable t) {
        throw indisponivel(t);
    }

    private WinThorIndisponivelException indisponivel(Throwable t) {
        log.error("WinThor indisponível para consulta interativa de nota fiscal", t);
        return new WinThorIndisponivelException(
                "Sistema do WinThor indisponível no momento. Tente novamente em alguns minutos.", t);
    }
}

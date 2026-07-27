package br.com.frotasPro.integradora.winthor.exception;

/**
 * Lançada quando o banco do WinThor está lento/indisponível (circuit
 * breaker aberto) ou o limite de conexões simultâneas foi atingido
 * (bulkhead cheio). Sinaliza pro chamador que é um problema temporário,
 * não um erro de dados.
 */
public class WinThorIndisponivelException extends RuntimeException {

    public WinThorIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}

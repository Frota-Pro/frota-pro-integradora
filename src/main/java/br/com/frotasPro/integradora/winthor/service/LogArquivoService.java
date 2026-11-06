package br.com.frotasPro.integradora.winthor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê o final dos arquivos de log da integradora para a tela de Logs do painel (via frota-pro-api).
 * Só aceita os nomes da lista abaixo, nunca um caminho vindo da requisição.
 */
@Service
public class LogArquivoService {

    /** O quanto do fim do arquivo é lido (suficiente para milhares de linhas, sem carregar um arquivo de GB). */
    static final int BYTES_MAXIMOS = 4 * 1024 * 1024;

    private static final int LINHAS_MIN = 10;
    private static final int LINHAS_MAX = 2000;

    private final Map<String, Path> arquivos;

    public LogArquivoService(@Value("${logging.file.name:logs/frotapro-integradora.log}") String arquivoPrincipal) {
        Path principal = Paths.get(arquivoPrincipal);
        this.arquivos = Map.of(
                "PRINCIPAL", principal,
                // mesmos nomes de logback-spring.xml, na mesma pasta do log principal
                "SINCRONIZACAO", principal.resolveSibling("integradora-sincronizacao.log"),
                "ERROS", principal.resolveSibling("integradora-erros.log")
        );
    }

    /** @param arquivo PRINCIPAL, SINCRONIZACAO ou ERROS (qualquer outro valor cai em PRINCIPAL) */
    public List<String> ultimasLinhas(String arquivo, int linhas) {
        String chave = arquivo == null ? "PRINCIPAL" : arquivo.trim().toUpperCase(Locale.ROOT);
        Path path = arquivos.getOrDefault(chave, arquivos.get("PRINCIPAL"));
        int n = Math.min(Math.max(linhas, LINHAS_MIN), LINHAS_MAX);
        return ultimasLinhas(path, n);
    }

    static List<String> ultimasLinhas(Path path, int n) {
        if (!Files.exists(path)) {
            return List.of("[LOG] Arquivo não encontrado: " + path.toAbsolutePath());
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long tamanho = raf.length();
            long inicio = Math.max(0, tamanho - BYTES_MAXIMOS);
            byte[] bytes = new byte[(int) (tamanho - inicio)];
            raf.seek(inicio);
            raf.readFully(bytes);

            String texto = new String(bytes, StandardCharsets.UTF_8);
            String[] todas = texto.split("\\r?\\n");
            int de = 0;
            if (inicio > 0 && todas.length > 1) {
                de = 1; // a primeira linha lida pode estar cortada no meio
            }
            int fim = todas.length;
            if (fim > de && todas[fim - 1].isEmpty()) {
                fim--;
            }
            int comeco = Math.max(de, fim - n);
            List<String> saida = new ArrayList<>(Math.max(0, fim - comeco));
            for (int i = comeco; i < fim; i++) {
                saida.add(todas[i]);
            }
            return saida;
        } catch (IOException e) {
            return List.of("[LOG] Erro ao ler arquivo: " + e.getMessage());
        }
    }
}

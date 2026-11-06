package br.com.frotasPro.integradora.winthor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogArquivoServiceTest {

    @TempDir
    Path pasta;

    private Path escrever(String nome, String conteudo) throws IOException {
        Path p = pasta.resolve(nome);
        Files.writeString(p, conteudo, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void devolveSoAsUltimasLinhasNaOrdemDoArquivo() throws IOException {
        String conteudo = IntStream.rangeClosed(1, 50).mapToObj(i -> "linha " + i).collect(Collectors.joining("\n")) + "\n";
        Path p = escrever("frotapro-integradora.log", conteudo);

        List<String> linhas = LogArquivoService.ultimasLinhas(p, 3);

        assertEquals(List.of("linha 48", "linha 49", "linha 50"), linhas);
    }

    @Test
    void arquivoMenorQueOPedidoDevolveTudoSemLinhaVazia() throws IOException {
        Path p = escrever("a.log", "um\ndois\n");

        assertEquals(List.of("um", "dois"), LogArquivoService.ultimasLinhas(p, 100));
    }

    @Test
    void arquivoInexistenteAvisaEmVezDeFalhar() {
        List<String> linhas = LogArquivoService.ultimasLinhas(pasta.resolve("nao-existe.log"), 10);

        assertEquals(1, linhas.size());
        assertTrue(linhas.get(0).startsWith("[LOG] Arquivo não encontrado"));
    }

    @Test
    void arquivoGrandeLeSoOFimEDescartaALinhaCortada() throws IOException {
        StringBuilder sb = new StringBuilder();
        String linha = "x".repeat(99) + "\n"; // 100 bytes por linha
        int total = (LogArquivoService.BYTES_MAXIMOS / 100) + 5000;
        for (int i = 0; i < total; i++) {
            sb.append(linha);
        }
        sb.append("ultima");
        Path p = escrever("grande.log", sb.toString());

        List<String> linhas = LogArquivoService.ultimasLinhas(p, 5);

        assertEquals(5, linhas.size());
        assertEquals("ultima", linhas.get(4));
        assertEquals("x".repeat(99), linhas.get(0), "nenhuma linha pela metade");
    }

    @Test
    void soAceitaOsNomesConhecidosEIgnoraCaminhoVindoDeFora() throws IOException {
        Path principal = escrever("frotapro-integradora.log", "principal\n");
        escrever("integradora-sincronizacao.log", "sync 1\nsync 2\n");
        escrever("integradora-erros.log", "erro 1\n");
        LogArquivoService service = new LogArquivoService(principal.toString());

        assertEquals(List.of("sync 1", "sync 2"), service.ultimasLinhas("sincronizacao", 50));
        assertEquals(List.of("erro 1"), service.ultimasLinhas("ERROS", 50));
        assertEquals(List.of("principal"), service.ultimasLinhas("../../etc/passwd", 50));
        assertEquals(List.of("principal"), service.ultimasLinhas((String) null, 50));
    }
}

package br.com.frotasPro.integradora.winthor.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * As colunas de data e de peso do cabeçalho do MDF-e (PCMANIFESTOELETRONICOC) não estão no código da
 * integradora: são descobertas no dicionário do Oracle, entre os nomes que o WinThor costuma usar.
 * Se nenhuma for encontrada a consulta de manifestos sem nota devolve vazio, em vez de errar.
 *
 * <p>No banco da empresa (conferido em 05/11/2026) a data é DATAHORAGERACAO e a tabela NÃO tem coluna de
 * peso: o peso do manifesto sem nota fica vazio até se descobrir onde o WinThor o guarda.
 */
final class ColunasManifesto {

    private static final List<String> CANDIDATAS_DATA = List.of(
            "DATAHORAGERACAO", "DTEMISSAO", "DATAEMISSAO", "DTMDFE", "DTGERACAO", "DTSAIDA", "DTINICIOVIAGEM", "DTCADASTRO", "DATA");
    private static final List<String> CANDIDATAS_PESO = List.of(
            "TOTPESO", "PESOTOTAL", "PESOBRUTO", "VLPESO", "QTDPESO", "PESO", "QTDTOTALCARGA");

    private ColunasManifesto() {
    }

    static Optional<String> colunaData(Set<String> existentes) {
        return primeira(CANDIDATAS_DATA, existentes);
    }

    static Optional<String> colunaPeso(Set<String> existentes) {
        return primeira(CANDIDATAS_PESO, existentes);
    }

    private static Optional<String> primeira(List<String> candidatas, Set<String> existentes) {
        return candidatas.stream().filter(existentes::contains).findFirst();
    }
}

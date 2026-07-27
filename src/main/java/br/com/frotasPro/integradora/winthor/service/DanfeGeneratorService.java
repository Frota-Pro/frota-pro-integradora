package br.com.frotasPro.integradora.winthor.service;

import br.com.swconsultoria.impressao.model.Impressao;
import br.com.swconsultoria.impressao.service.ImpressaoService;
import br.com.swconsultoria.impressao.util.ImpressaoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gera o DANFE (PDF) a partir do XML da NF-e já autorizado pela SEFAZ.
 * Não existe PDF pronto em lugar nenhum do WinThor — o PDF é sempre
 * renderizado na hora a partir do XML.
 */
@Slf4j
@Service
public class DanfeGeneratorService {

    public byte[] gerarPdf(String xmlNfe) {
        try {
            Impressao impressao = ImpressaoUtil.impressaoPadraoNFe(xmlNfe);
            return ImpressaoService.impressaoPdfByte(impressao);
        } catch (Exception e) {
            log.error("Falha ao gerar DANFE a partir do XML da NF-e", e);
            throw new IllegalStateException("Não foi possível gerar o PDF (DANFE) desta nota fiscal.", e);
        }
    }
}

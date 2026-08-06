package br.com.frotasPro.integradora.winthor.service;

import br.com.swconsultoria.impressao.model.Impressao;
import br.com.swconsultoria.impressao.service.ImpressaoService;
import br.com.swconsultoria.impressao.util.ConstantesUtil;
import br.com.swconsultoria.impressao.util.ImpressaoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Gera o DANFE (PDF) a partir do XML da NF-e já autorizado pela SEFAZ.
 * Não existe PDF pronto em lugar nenhum do WinThor — o PDF é sempre
 * renderizado na hora a partir do XML.
 */
@Slf4j
@Service
public class DanfeGeneratorService {

    /**
     * @param logoBytes logo da empresa (configurada no frota-pro-api) pra substituir
     *                  a logo em branco padrão da biblioteca. Opcional — sem ela, usa o padrão.
     */
    public byte[] gerarPdf(String xmlNfe, byte[] logoBytes) {
        try {
            Impressao impressao = ImpressaoUtil.impressaoPadraoNFe(xmlNfe);

            if (logoBytes != null && logoBytes.length > 0) {
                impressao.getParametros().put(ConstantesUtil.PARAM_LOGO_NFE, new ByteArrayInputStream(logoBytes));
            }

            return ImpressaoService.impressaoPdfByte(impressao);
        } catch (Exception e) {
            log.error("Falha ao gerar DANFE a partir do XML da NF-e", e);
            throw new IllegalStateException("Não foi possível gerar o PDF (DANFE) desta nota fiscal.", e);
        }
    }
}

package br.com.frotasPro.integradora.winthor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Um carregamento do WinThor para a planilha de planejamento. Vem direto do pccarreg (+ pedidos do pcpedc), então
 * existe antes de qualquer nota: o carregamento ainda pode estar só montado, sem faturar.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanejamentoCarregamentoDto {

    private Integer numCar;

    /** Dia em que o carregamento foi montado (pccarreg.datamon). */
    private LocalDate dataMontagem;

    private String destino;

    /** Motorista e veículo gravados no carregamento (pccarreg). Vazio/zero costuma ser venda de balcão. */
    private Integer codMotorista;
    private Integer codVeiculo;

    /** Nome do motorista (pcempr) e placa do veículo (pcveicul) gravados no carregamento; null se não achar. */
    private String nomeMotorista;
    private String placaVeiculo;

    /** Peso do carregamento em QUILOS (pccarreg.totpeso). */
    private Double pesoKg;

    /** Pedidos do carregamento (sem os cancelados). */
    private Integer totalPedidos;

    /** Clientes distintos dos pedidos. */
    private Integer totalClientes;

    /** Soma do valor dos pedidos do carregamento (sem os cancelados), em reais. */
    private BigDecimal valorTotal;

    /** Códigos desses clientes: a API precisa deles para contar clientes distintos numa rota (principal + complementos). */
    private List<Integer> codigosClientes;

    /** true quando já existe nota de saída no carregamento. Depois de faturado, a planilha não mexe mais. */
    private boolean faturada;
}

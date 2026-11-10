package br.com.fluxocaixa.consolidado.domain.exception;

public class PeriodoConsultaInvalidoException extends RuntimeException {

    private final Motivo motivo;

    public PeriodoConsultaInvalidoException(Motivo motivo) {
        super(motivo.mensagem());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }

    public enum Motivo {
        DATA_OBRIGATORIA("Informe as datas de início e fim do período"),
        INICIO_APOS_FIM("A data inicial não pode ser posterior à data final"),
        INTERVALO_MAIOR_QUE_366("O período máximo da consulta é de 366 dias");

        private final String mensagem;

        Motivo(String mensagem) {
            this.mensagem = mensagem;
        }

        public String mensagem() {
            return mensagem;
        }
    }
}

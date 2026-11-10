package br.com.fluxocaixa.lancamentos.domain.lancamento;

public enum TipoLancamento {
    CREDITO,
    DEBITO;

    public TipoLancamento oposto() {
        return switch (this) {
            case CREDITO -> DEBITO;
            case DEBITO -> CREDITO;
        };
    }
}

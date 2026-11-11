package br.com.fluxocaixa.consolidado.adapters.out.cache;

class CacheIndisponivelException extends RuntimeException {

    CacheIndisponivelException(Throwable causa) {
        super(causa);
    }
}

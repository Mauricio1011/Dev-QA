package com.devqa.catalogo.exception;

public final class DatoDuplicadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}

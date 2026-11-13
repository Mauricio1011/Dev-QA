package com.devqa.catalogo.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<DetalleError> errores
) {
    public ErrorResponse {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }
}

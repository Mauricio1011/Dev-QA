package com.devqa.catalogo.dto;

import java.math.BigDecimal;

public record ProductoResponse(
        String id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String categoria,
        String sku,
        boolean activo
) {
}

package com.devqa.catalogo.support;

import com.devqa.catalogo.domain.Producto;
import com.devqa.catalogo.dto.ProductoRequest;

import java.math.BigDecimal;

public final class DatosDePrueba {

    private DatosDePrueba() {
    }

    public static ProductoRequest solicitud(String sku, Boolean activo, String descripcion) {
        return new ProductoRequest(
                "Teclado mecanico",
                descripcion,
                new BigDecimal("499.90"),
                10,
                "Perifericos",
                sku,
                activo
        );
    }

    public static Producto productoPersistido(String id, String sku) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre("Teclado mecanico");
        producto.setDescripcion("Switches rojos");
        producto.setPrecio(new BigDecimal("499.90"));
        producto.setStock(10);
        producto.setCategoria("Perifericos");
        producto.setSku(sku);
        producto.setActivo(true);
        return producto;
    }
}

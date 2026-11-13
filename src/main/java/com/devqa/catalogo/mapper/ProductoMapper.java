package com.devqa.catalogo.mapper;

import com.devqa.catalogo.domain.Producto;
import com.devqa.catalogo.dto.ProductoRequest;
import com.devqa.catalogo.dto.ProductoResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductoMapper {

    public Producto aEntidad(ProductoRequest request) {
        Producto producto = new Producto();
        aplicar(producto, request);
        return producto;
    }

    public void actualizar(Producto producto, ProductoRequest request) {
        aplicar(producto, request);
    }

    public ProductoResponse aRespuesta(Producto producto) {
        return new ProductoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getCategoria(),
                producto.getSku(),
                producto.isActivo()
        );
    }

    private void aplicar(Producto producto, ProductoRequest request) {
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(normalizarDescripcion(request.descripcion()));
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setCategoria(request.categoria().trim());
        producto.setSku(request.sku().trim());
        producto.setActivo(request.activo() == null || Boolean.TRUE.equals(request.activo()));
    }

    private static String normalizarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return null;
        }
        return descripcion.trim();
    }
}

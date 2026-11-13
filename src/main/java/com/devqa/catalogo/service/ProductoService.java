package com.devqa.catalogo.service;

import com.devqa.catalogo.domain.Producto;
import com.devqa.catalogo.dto.ProductoRequest;
import com.devqa.catalogo.dto.ProductoResponse;
import com.devqa.catalogo.exception.DatoDuplicadoException;
import com.devqa.catalogo.exception.RecursoNoEncontradoException;
import com.devqa.catalogo.mapper.ProductoMapper;
import com.devqa.catalogo.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;

    public ProductoService(ProductoRepository productoRepository, ProductoMapper productoMapper) {
        this.productoRepository = productoRepository;
        this.productoMapper = productoMapper;
    }

    public ProductoResponse crear(ProductoRequest request) {
        validarSkuDisponible(request.sku(), null);
        Producto producto = productoMapper.aEntidad(request);
        return productoMapper.aRespuesta(productoRepository.save(producto));
    }

    public ProductoResponse obtenerPorId(String id) {
        return productoMapper.aRespuesta(buscarOFallar(id));
    }

    public List<ProductoResponse> listar(String categoria) {
        List<Producto> productos = sinCategoria(categoria)
                ? productoRepository.findAll()
                : productoRepository.findByCategoriaIgnoreCase(categoria.trim());
        return productos.stream().map(productoMapper::aRespuesta).toList();
    }

    public ProductoResponse actualizar(String id, ProductoRequest request) {
        Producto existente = buscarOFallar(id);
        validarSkuDisponible(request.sku(), existente.getId());
        productoMapper.actualizar(existente, request);
        return productoMapper.aRespuesta(productoRepository.save(existente));
    }

    public void eliminar(String id) {
        productoRepository.delete(buscarOFallar(id));
    }

    private void validarSkuDisponible(String sku, String idActual) {
        String normalizado = sku.trim();
        Optional<Producto> existente = productoRepository.findBySku(normalizado);
        if (existente.isPresent() && esOtroProducto(existente.get(), idActual)) {
            throw new DatoDuplicadoException("Ya existe un producto con el SKU %s".formatted(normalizado));
        }
    }

    private static boolean esOtroProducto(Producto existente, String idActual) {
        return idActual == null || !Objects.equals(existente.getId(), idActual);
    }

    private Producto buscarOFallar(String id) {
        if (id == null || id.isBlank()) {
            throw new RecursoNoEncontradoException("No existe un producto con el id solicitado");
        }
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un producto con el id %s".formatted(id)));
    }

    private static boolean sinCategoria(String categoria) {
        return categoria == null || categoria.isBlank();
    }
}

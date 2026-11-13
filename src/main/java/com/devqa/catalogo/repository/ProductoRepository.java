package com.devqa.catalogo.repository;

import com.devqa.catalogo.domain.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findBySku(String sku);

    List<Producto> findByCategoriaIgnoreCase(String categoria);
}

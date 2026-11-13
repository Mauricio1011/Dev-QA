package com.devqa.catalogo.service;

import com.devqa.catalogo.domain.Producto;
import com.devqa.catalogo.dto.ProductoRequest;
import com.devqa.catalogo.dto.ProductoResponse;
import com.devqa.catalogo.exception.DatoDuplicadoException;
import com.devqa.catalogo.exception.RecursoNoEncontradoException;
import com.devqa.catalogo.mapper.ProductoMapper;
import com.devqa.catalogo.repository.ProductoRepository;
import com.devqa.catalogo.support.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(productoRepository, new ProductoMapper());
    }

    @Test
    @DisplayName("crear normaliza texto, activa el producto por defecto y devuelve el id")
    void crearNormalizaYGuarda() {
        when(productoRepository.findBySku("TEC-001")).thenReturn(Optional.empty());
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto producto = invocation.getArgument(0);
            producto.setId("abc");
            return producto;
        });
        ProductoRequest request = new ProductoRequest(
                "  Teclado mecanico  ",
                "  Switches rojos  ",
                new BigDecimal("499.90"),
                10,
                "  Perifericos  ",
                " TEC-001 ",
                null
        );

        ProductoResponse respuesta = productoService.crear(request);

        assertThat(respuesta.id()).isEqualTo("abc");
        assertThat(respuesta.nombre()).isEqualTo("Teclado mecanico");
        assertThat(respuesta.descripcion()).isEqualTo("Switches rojos");
        assertThat(respuesta.categoria()).isEqualTo("Perifericos");
        assertThat(respuesta.sku()).isEqualTo("TEC-001");
        assertThat(respuesta.precio()).isEqualByComparingTo("499.90");
        assertThat(respuesta.stock()).isEqualTo(10);
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    @DisplayName("crear respeta activo en falso")
    void crearConActivoFalso() {
        when(productoRepository.findBySku("TEC-002")).thenReturn(Optional.empty());
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoResponse respuesta = productoService.crear(DatosDePrueba.solicitud("TEC-002", false, "Con cable"));

        assertThat(respuesta.activo()).isFalse();
        assertThat(respuesta.descripcion()).isEqualTo("Con cable");
    }

    @Test
    @DisplayName("crear guarda la descripción nula cuando llega en blanco")
    void crearConDescripcionEnBlanco() {
        when(productoRepository.findBySku("TEC-003")).thenReturn(Optional.empty());
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoResponse respuesta = productoService.crear(DatosDePrueba.solicitud("TEC-003", true, "   "));

        assertThat(respuesta.descripcion()).isNull();
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    @DisplayName("crear rechaza un SKU ya registrado, aunque llegue con espacios")
    void crearRechazaSkuDuplicado() {
        when(productoRepository.findBySku("TEC-001"))
                .thenReturn(Optional.of(DatosDePrueba.productoPersistido("otro", "TEC-001")));

        assertThatThrownBy(() -> productoService.crear(DatosDePrueba.solicitud(" TEC-001 ", true, "x")))
                .isInstanceOf(DatoDuplicadoException.class)
                .hasMessage("Ya existe un producto con el SKU TEC-001");
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("obtenerPorId devuelve el producto persistido")
    void obtenerPorIdExistente() {
        when(productoRepository.findById("1"))
                .thenReturn(Optional.of(DatosDePrueba.productoPersistido("1", "TEC-001")));

        ProductoResponse respuesta = productoService.obtenerPorId("1");

        assertThat(respuesta.id()).isEqualTo("1");
        assertThat(respuesta.sku()).isEqualTo("TEC-001");
    }

    @Test
    @DisplayName("obtenerPorId lanza RecursoNoEncontradoException si el id no existe")
    void obtenerPorIdInexistente() {
        when(productoRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.obtenerPorId("no-existe"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe un producto con el id no-existe");
    }

    @Test
    @DisplayName("obtenerPorId rechaza un id en blanco sin consultar la base")
    void obtenerPorIdEnBlanco() {
        assertThatThrownBy(() -> productoService.obtenerPorId("   "))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe un producto con el id solicitado");
        verify(productoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("obtenerPorId rechaza un id nulo sin consultar la base")
    void obtenerPorIdNulo() {
        assertThatThrownBy(() -> productoService.obtenerPorId(null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(productoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("listar sin categoría devuelve todos los productos")
    void listarSinCategoria() {
        when(productoRepository.findAll()).thenReturn(List.of(DatosDePrueba.productoPersistido("1", "TEC-001")));

        assertThat(productoService.listar(null)).extracting(ProductoResponse::sku).containsExactly("TEC-001");
        verify(productoRepository, never()).findByCategoriaIgnoreCase(any());
    }

    @Test
    @DisplayName("listar con categoría en blanco se comporta como un listado completo")
    void listarCategoriaEnBlanco() {
        when(productoRepository.findAll()).thenReturn(List.of(DatosDePrueba.productoPersistido("1", "TEC-001")));

        assertThat(productoService.listar("   ")).hasSize(1);
        verify(productoRepository, never()).findByCategoriaIgnoreCase(any());
    }

    @Test
    @DisplayName("listar recorta la categoría y filtra sin distinguir mayúsculas")
    void listarPorCategoria() {
        when(productoRepository.findByCategoriaIgnoreCase("Audio"))
                .thenReturn(List.of(DatosDePrueba.productoPersistido("2", "AUD-001")));

        assertThat(productoService.listar("  Audio  ")).extracting(ProductoResponse::sku).containsExactly("AUD-001");
        verify(productoRepository, never()).findAll();
    }

    @Test
    @DisplayName("actualizar permite conservar el mismo SKU y aplica los datos nuevos")
    void actualizarConElMismoSku() {
        Producto actual = DatosDePrueba.productoPersistido("1", "TEC-001");
        when(productoRepository.findById("1")).thenReturn(Optional.of(actual));
        when(productoRepository.findBySku("TEC-001")).thenReturn(Optional.of(actual));
        when(productoRepository.save(actual)).thenReturn(actual);
        ProductoRequest request = new ProductoRequest(
                "Teclado actualizado",
                "Nuevo",
                new BigDecimal("10.00"),
                3,
                "Audio",
                "TEC-001",
                false
        );

        ProductoResponse respuesta = productoService.actualizar("1", request);

        assertThat(respuesta.nombre()).isEqualTo("Teclado actualizado");
        assertThat(respuesta.descripcion()).isEqualTo("Nuevo");
        assertThat(respuesta.precio()).isEqualByComparingTo("10.00");
        assertThat(respuesta.stock()).isEqualTo(3);
        assertThat(respuesta.categoria()).isEqualTo("Audio");
        assertThat(respuesta.activo()).isFalse();
    }

    @Test
    @DisplayName("actualizar rechaza el SKU de otro producto")
    void actualizarConSkuAjeno() {
        when(productoRepository.findById("1"))
                .thenReturn(Optional.of(DatosDePrueba.productoPersistido("1", "TEC-001")));
        when(productoRepository.findBySku("TEC-002"))
                .thenReturn(Optional.of(DatosDePrueba.productoPersistido("2", "TEC-002")));

        assertThatThrownBy(() -> productoService.actualizar("1", DatosDePrueba.solicitud("TEC-002", true, "x")))
                .isInstanceOf(DatoDuplicadoException.class)
                .hasMessage("Ya existe un producto con el SKU TEC-002");
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar falla si el producto no existe")
    void actualizarInexistente() {
        when(productoRepository.findById("no")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizar("no", DatosDePrueba.solicitud("TEC-009", true, "x")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(productoRepository, never()).findBySku(any());
    }

    @Test
    @DisplayName("actualizar acepta un SKU nuevo que nadie más usa")
    void actualizarConSkuLibre() {
        Producto actual = DatosDePrueba.productoPersistido("1", "TEC-001");
        when(productoRepository.findById("1")).thenReturn(Optional.of(actual));
        when(productoRepository.findBySku("TEC-009")).thenReturn(Optional.empty());
        when(productoRepository.save(actual)).thenReturn(actual);

        ProductoResponse respuesta = productoService.actualizar("1", DatosDePrueba.solicitud("TEC-009", true, "Libre"));

        assertThat(respuesta.sku()).isEqualTo("TEC-009");
    }

    @Test
    @DisplayName("eliminar borra el producto encontrado")
    void eliminarExistente() {
        Producto actual = DatosDePrueba.productoPersistido("1", "TEC-001");
        when(productoRepository.findById("1")).thenReturn(Optional.of(actual));

        productoService.eliminar("1");

        verify(productoRepository).delete(actual);
    }

    @Test
    @DisplayName("eliminar no borra nada si el producto no existe")
    void eliminarInexistente() {
        when(productoRepository.findById("no")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.eliminar("no"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(productoRepository, never()).delete(any());
    }
}

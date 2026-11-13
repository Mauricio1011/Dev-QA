package com.devqa.catalogo.integration;

import com.devqa.catalogo.dto.DetalleError;
import com.devqa.catalogo.dto.ErrorResponse;
import com.devqa.catalogo.dto.ProductoRequest;
import com.devqa.catalogo.dto.ProductoResponse;
import com.devqa.catalogo.repository.ProductoRepository;
import com.devqa.catalogo.support.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void registrarMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiar() {
        productoRepository.deleteAll();
    }

    @Test
    @DisplayName("el flujo HTTP completo crea, consulta, actualiza y elimina un producto")
    void flujoCrudCompleto() {
        ProductoResponse creado = crear(DatosDePrueba.solicitud("TEC-001", true, "Switches rojos"));

        ResponseEntity<ProductoResponse> consulta = restTemplate.getForEntity(
                "/api/productos/" + creado.id(), ProductoResponse.class);
        assertJson(consulta, HttpStatus.OK);
        assertThat(consulta.getBody()).isNotNull();
        assertThat(consulta.getBody().sku()).isEqualTo("TEC-001");
        assertThat(consulta.getBody().precio()).isEqualByComparingTo("499.90");

        ProductoRequest actualizacion = new ProductoRequest(
                "Teclado actualizado",
                "Switches marrones",
                new BigDecimal("459.00"),
                8,
                "Perifericos",
                "TEC-001",
                true
        );
        ResponseEntity<ProductoResponse> actualizado = restTemplate.exchange(
                "/api/productos/" + creado.id(),
                HttpMethod.PUT,
                new HttpEntity<>(actualizacion),
                ProductoResponse.class
        );
        assertJson(actualizado, HttpStatus.OK);
        assertThat(actualizado.getBody()).isNotNull();
        assertThat(actualizado.getBody().nombre()).isEqualTo("Teclado actualizado");
        assertThat(actualizado.getBody().precio()).isEqualByComparingTo("459.00");

        ResponseEntity<ProductoResponse[]> listado = restTemplate.getForEntity("/api/productos", ProductoResponse[].class);
        assertJson(listado, HttpStatus.OK);
        assertThat(listado.getBody()).extracting(ProductoResponse::id).contains(creado.id());

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/productos/" + creado.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> ausente = restTemplate.getForEntity(
                "/api/productos/" + creado.id(), ErrorResponse.class);
        assertJson(ausente, HttpStatus.NOT_FOUND);
        assertThat(ausente.getBody()).isNotNull();
        assertThat(ausente.getBody().message()).contains(creado.id());
    }

    @Test
    @DisplayName("un alta sin activo ni descripción queda activa y sin descripción")
    void valoresPorDefecto() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
                {
                  "nombre": "Mouse optico",
                  "precio": 199.50,
                  "stock": 5,
                  "categoria": "Perifericos",
                  "sku": "MOU-001"
                }
                """;

        ResponseEntity<ProductoResponse> respuesta = restTemplate.exchange(
                "/api/productos", HttpMethod.POST, new HttpEntity<>(json, headers), ProductoResponse.class);

        assertJson(respuesta, HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().activo()).isTrue();
        assertThat(respuesta.getBody().descripcion()).isNull();
        assertThat(respuesta.getBody().precio()).isEqualByComparingTo("199.50");
    }

    @Test
    @DisplayName("el listado filtra por categoría sin distinguir mayúsculas")
    void filtraPorCategoria() {
        crear(DatosDePrueba.solicitud("TEC-001", true, "Switches rojos"));
        crear(new ProductoRequest(
                "Audifonos",
                "Bluetooth",
                new BigDecimal("299.00"),
                4,
                "Audio",
                "AUD-001",
                true
        ));

        URI uri = UriComponentsBuilder.fromPath("/api/productos")
                .queryParam("categoria", "audio")
                .build()
                .toUri();
        ResponseEntity<ProductoResponse[]> filtrado = restTemplate.getForEntity(uri, ProductoResponse[].class);
        ResponseEntity<ProductoResponse[]> completo = restTemplate.getForEntity("/api/productos", ProductoResponse[].class);

        assertThat(filtrado.getBody()).extracting(ProductoResponse::sku).containsExactly("AUD-001");
        assertThat(completo.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("el cuerpo inválido responde 400 con el detalle de cada campo")
    void rechazaDatosInvalidos() {
        ProductoRequest invalido = new ProductoRequest(
                "",
                "x".repeat(501),
                new BigDecimal("0.00"),
                -1,
                "",
                "sku minuscula",
                true
        );

        ResponseEntity<ErrorResponse> respuesta = restTemplate.postForEntity("/api/productos", invalido, ErrorResponse.class);

        assertJson(respuesta, HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().error()).isEqualTo("Solicitud inválida");
        assertThat(respuesta.getBody().errores()).extracting(DetalleError::campo)
                .contains("nombre", "descripcion", "precio", "stock", "categoria", "sku");
    }

    @Test
    @DisplayName("un JSON malformado responde 400")
    void rechazaJsonMalformado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ErrorResponse> respuesta = restTemplate.exchange(
                "/api/productos",
                HttpMethod.POST,
                new HttpEntity<>("{", headers),
                ErrorResponse.class
        );

        assertJson(respuesta, HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).contains("JSON");
    }

    @Test
    @DisplayName("repetir un SKU responde 409")
    void rechazaSkuDuplicado() {
        crear(DatosDePrueba.solicitud("TEC-001", true, "Switches rojos"));

        ResponseEntity<ErrorResponse> respuesta = restTemplate.postForEntity(
                "/api/productos",
                DatosDePrueba.solicitud("TEC-001", true, "Otro"),
                ErrorResponse.class
        );

        assertJson(respuesta, HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).contains("TEC-001");
    }

    @Test
    @DisplayName("asignar el SKU de otro producto responde 409")
    void rechazaActualizarConSkuAjeno() {
        ProductoResponse primero = crear(DatosDePrueba.solicitud("TEC-001", true, "Uno"));
        crear(DatosDePrueba.solicitud("TEC-002", true, "Dos"));

        ResponseEntity<ErrorResponse> respuesta = restTemplate.exchange(
                "/api/productos/" + primero.id(),
                HttpMethod.PUT,
                new HttpEntity<>(DatosDePrueba.solicitud("TEC-002", true, "Cambio")),
                ErrorResponse.class
        );

        assertJson(respuesta, HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).contains("TEC-002");
    }

    @Test
    @DisplayName("consultar o borrar un id inexistente responde 404")
    void responde404SiNoExiste() {
        ResponseEntity<ErrorResponse> consulta = restTemplate.getForEntity("/api/productos/no-existe", ErrorResponse.class);
        ResponseEntity<ErrorResponse> borrado = restTemplate.exchange(
                "/api/productos/no-existe", HttpMethod.DELETE, null, ErrorResponse.class);

        assertJson(consulta, HttpStatus.NOT_FOUND);
        assertJson(borrado, HttpStatus.NOT_FOUND);
        assertThat(consulta.getBody()).isNotNull();
        assertThat(consulta.getBody().path()).isEqualTo("/api/productos/no-existe");
    }

    @Test
    @DisplayName("un id en blanco responde 400")
    void rechazaIdEnBlanco() {
        URI uri = UriComponentsBuilder.fromPath("/api/productos/{id}").build(" ");

        ResponseEntity<ErrorResponse> respuesta = restTemplate.getForEntity(uri, ErrorResponse.class);

        assertJson(respuesta, HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().errores()).isNotEmpty();
    }

    @Test
    @DisplayName("usar un método no soportado sobre un producto responde 405")
    void metodoNoPermitido() {
        ProductoResponse creado = crear(DatosDePrueba.solicitud("TEC-001", true, "Uno"));

        ResponseEntity<ErrorResponse> respuesta = restTemplate.exchange(
                "/api/productos/" + creado.id(),
                HttpMethod.POST,
                new HttpEntity<>(DatosDePrueba.solicitud("TEC-001", true, "Uno")),
                ErrorResponse.class
        );

        assertJson(respuesta, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().error()).isEqualTo("Método no permitido");
    }

    @Test
    @DisplayName("enviar texto plano donde se espera JSON responde 415")
    void contenidoNoSoportado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<ErrorResponse> respuesta = restTemplate.exchange(
                "/api/productos",
                HttpMethod.POST,
                new HttpEntity<>("no-es-json", headers),
                ErrorResponse.class
        );

        assertJson(respuesta, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().error()).isEqualTo("Tipo de contenido no soportado");
    }

    private ProductoResponse crear(ProductoRequest request) {
        ResponseEntity<ProductoResponse> respuesta = restTemplate.postForEntity(
                "/api/productos", request, ProductoResponse.class);
        assertJson(respuesta, HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().id()).isNotBlank();
        return respuesta.getBody();
    }

    private static void assertJson(ResponseEntity<?> respuesta, HttpStatus estado) {
        assertThat(respuesta.getStatusCode()).isEqualTo(estado);
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }
}

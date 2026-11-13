package com.devqa.catalogo.exception;

import com.devqa.catalogo.controller.ProductoController;
import com.devqa.catalogo.dto.DetalleError;
import com.devqa.catalogo.dto.ErrorResponse;
import com.devqa.catalogo.dto.ProductoRequest;
import com.devqa.catalogo.service.ProductoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("un recurso ausente responde 404 con la ruta solicitada")
    void recursoNoEncontrado() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRecursoNoEncontrado(
                new RecursoNoEncontradoException("No existe un producto con el id 9"),
                peticionHttp("GET", "/api/productos/9")
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.status()).isEqualTo(404);
        assertThat(cuerpo.error()).isEqualTo("Recurso no encontrado");
        assertThat(cuerpo.message()).isEqualTo("No existe un producto con el id 9");
        assertThat(cuerpo.path()).isEqualTo("/api/productos/9");
        assertThat(cuerpo.errores()).isEmpty();
        assertThat(cuerpo.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("un dato duplicado de negocio responde 409")
    void datoDuplicado() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarDatoDuplicado(
                new DatoDuplicadoException("Ya existe un producto con el SKU TEC-001"),
                peticionHttp("POST", "/api/productos")
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().error()).isEqualTo("Conflicto");
        assertThat(respuesta.getBody().message()).contains("TEC-001");
    }

    @Test
    @DisplayName("una clave duplicada de MongoDB responde 409 sin filtrar el detalle técnico")
    void claveDuplicada() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarClaveDuplicada(
                new DuplicateKeyException("E11000 duplicate key error collection"),
                peticionHttp("POST", "/api/productos")
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).isEqualTo("Ya existe un registro con un valor único duplicado");
        assertThat(respuesta.getBody().message()).doesNotContain("E11000");
    }

    @Test
    @DisplayName("una excepción inesperada responde 500 sin exponer el mensaje interno")
    void errorInesperado() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarInesperado(
                new IllegalStateException("secreto-interno"),
                peticionHttp("GET", "/api/productos")
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().error()).isEqualTo("Error interno");
        assertThat(respuesta.getBody().message()).doesNotContain("secreto-interno");
    }

    @Test
    @DisplayName("la validación del cuerpo informa cada campo y sustituye mensajes vacíos")
    void validacionDeCuerpo() throws Exception {
        ResponseEntity<Object> conMensaje = handler.handleMethodArgumentNotValid(
                excepcionDeCampo("El nombre es obligatorio"),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                peticionWeb()
        );
        ResponseEntity<Object> sinMensaje = handler.handleMethodArgumentNotValid(
                excepcionDeCampo(null),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                peticionWeb()
        );
        ResponseEntity<Object> mensajeEnBlanco = handler.handleMethodArgumentNotValid(
                excepcionDeCampo("   "),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                peticionWeb()
        );

        assertThat(detalles(conMensaje)).containsExactly(new DetalleError("nombre", "El nombre es obligatorio"));
        assertThat(detalles(sinMensaje)).containsExactly(new DetalleError("nombre", "Valor inválido"));
        assertThat(detalles(mensajeEnBlanco)).containsExactly(new DetalleError("nombre", "Valor inválido"));
    }

    @Test
    @DisplayName("la validación de un parámetro responde 400 con el nombre del argumento")
    void validacionDeParametro() throws Exception {
        Method metodo = ProductoController.class.getDeclaredMethod("obtener", String.class);
        MethodParameter parametro = new MethodParameter(metodo, 0);
        parametro.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        ParameterValidationResult resultado = new ParameterValidationResult(
                parametro,
                " ",
                List.of(new DefaultMessageSourceResolvable(null, null, "El id es obligatorio"))
        );
        HandlerMethodValidationException ex = new HandlerMethodValidationException(
                MethodValidationResult.create(
                        new ProductoController(mock(ProductoService.class)),
                        metodo,
                        List.of(resultado)
                )
        );

        ResponseEntity<Object> respuesta = handler.handleHandlerMethodValidationException(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, peticionWeb());

        assertThat(detalles(respuesta)).containsExactly(new DetalleError("id", "El id es obligatorio"));
    }

    @Test
    @DisplayName("un JSON ilegible responde 400")
    void jsonIlegible() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new MockHttpInputMessage(new byte[0])
        );

        ResponseEntity<Object> respuesta = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, peticionWeb());

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.message()).contains("JSON");
        assertThat(cuerpo.path()).isEqualTo("/api/productos");
    }

    @Test
    @DisplayName("un estado HTTP desconocido usa el título y el mensaje genéricos")
    void estadoDesconocido() {
        ResponseEntity<Object> respuesta = handler.handleExceptionInternal(
                new IllegalArgumentException("detalle interno"),
                null,
                new HttpHeaders(),
                HttpStatusCode.valueOf(499),
                peticionWeb()
        );

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(respuesta.getStatusCode().value()).isEqualTo(499);
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.error()).isEqualTo("Error");
        assertThat(cuerpo.message()).isEqualTo("No se pudo procesar la solicitud");
        assertThat(cuerpo.message()).doesNotContain("detalle interno");
    }

    @Test
    @DisplayName("un método no permitido responde 405 con el contrato de error")
    void metodoNoPermitido() {
        ResponseEntity<Object> respuesta = handler.handleExceptionInternal(
                new IllegalArgumentException("POST no soportado"),
                null,
                new HttpHeaders(),
                HttpStatus.METHOD_NOT_ALLOWED,
                peticionWeb()
        );

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.status()).isEqualTo(405);
        assertThat(cuerpo.error()).isEqualTo("Método no permitido");
        assertThat(cuerpo.message()).isEqualTo("El método HTTP no está permitido");
    }

    @Test
    @DisplayName("un estado conocido fuera del catálogo conserva la frase HTTP")
    void estadoConocidoSinTituloPropio() {
        ResponseEntity<Object> respuesta = handler.handleExceptionInternal(
                new IllegalArgumentException("ok"),
                null,
                new HttpHeaders(),
                HttpStatus.OK,
                peticionDeOtraInterfaz()
        );

        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.error()).isEqualTo("OK");
        assertThat(cuerpo.path()).isEqualTo("uri=/api/productos");
        assertThat(cuerpo.message()).isEqualTo("No se pudo procesar la solicitud");
    }

    @Test
    @DisplayName("una restricción sin ruta usable informa el campo solicitud")
    void restriccionSinCampo() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRestriccion(
                new ConstraintViolationException(Set.of(violacion(Collections.emptyIterator(), "es obligatorio"))),
                peticionHttp("POST", "/api/productos")
        );

        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().errores()).containsExactly(new DetalleError("solicitud", "es obligatorio"));
    }

    @Test
    @DisplayName("una restricción con nombre en blanco usa el campo solicitud y un mensaje sustituto")
    void restriccionConCampoEnBlanco() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRestriccion(
                new ConstraintViolationException(Set.of(violacion(List.of(nodo("   ")).iterator(), "  "))),
                peticionHttp("POST", "/api/productos")
        );

        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().errores()).containsExactly(new DetalleError("solicitud", "Valor inválido"));
    }

    @Test
    @DisplayName("una restricción informa el último nodo de la ruta")
    void restriccionConCampo() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRestriccion(
                new ConstraintViolationException(Set.of(
                        violacion(List.of(nodo("crear"), nodo("precio")).iterator(), null)
                )),
                peticionHttp("POST", "/api/productos")
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().errores()).containsExactly(new DetalleError("precio", "Valor inválido"));
    }

    @Test
    @DisplayName("las violaciones reales del modelo se traducen a errores de campo")
    void restriccionRealDelModelo() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<jakarta.validation.ConstraintViolation<ProductoRequest>> violaciones = factory.getValidator()
                    .validate(new ProductoRequest(" ", null, null, -1, "", "malo", null));
            assertThat(violaciones).isNotEmpty();

            ResponseEntity<ErrorResponse> respuesta = handler.manejarRestriccion(
                    new ConstraintViolationException(violaciones),
                    peticionHttp("POST", "/api/productos")
            );

            assertThat(respuesta.getBody()).isNotNull();
            assertThat(respuesta.getBody().errores()).extracting(DetalleError::campo)
                    .contains("nombre", "precio", "stock", "categoria", "sku");
        }
    }

    @Test
    @DisplayName("la respuesta de error tolera una lista de detalles nula y copia la que recibe")
    void errorResponseProtegeLaLista() {
        ErrorResponse sinDetalles = new ErrorResponse(Instant.parse("2026-11-12T00:00:00Z"), 400, "Error", "m", "/", null);
        ErrorResponse conDetalles = new ErrorResponse(
                Instant.parse("2026-11-12T00:00:00Z"),
                400,
                "Error",
                "m",
                "/",
                List.of(new DetalleError("sku", "obligatorio"))
        );

        assertThat(sinDetalles.errores()).isEmpty();
        assertThat(conDetalles.errores()).containsExactly(new DetalleError("sku", "obligatorio"));
    }

    private static List<DetalleError> detalles(ResponseEntity<Object> respuesta) {
        ErrorResponse cuerpo = (ErrorResponse) respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.status()).isEqualTo(400);
        assertThat(cuerpo.error()).isEqualTo("Solicitud inválida");
        return cuerpo.errores();
    }

    private static MethodArgumentNotValidException excepcionDeCampo(String mensaje) throws NoSuchMethodException {
        ProductoRequest objetivo = new ProductoRequest(null, null, null, null, null, null, null);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(objetivo, "productoRequest");
        binding.addError(new FieldError(
                "productoRequest",
                "nombre",
                null,
                false,
                null,
                null,
                mensaje
        ));
        MethodParameter parametro = new MethodParameter(
                ProductoController.class.getDeclaredMethod("crear", ProductoRequest.class),
                0
        );
        return new MethodArgumentNotValidException(parametro, binding);
    }

    private static MockHttpServletRequest peticionHttp(String metodo, String ruta) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRequestURI(ruta);
        return request;
    }

    private static WebRequest peticionWeb() {
        return new ServletWebRequest(peticionHttp("POST", "/api/productos"));
    }

    private static WebRequest peticionDeOtraInterfaz() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/productos");
        return request;
    }

    private static ConstraintViolation<?> violacion(java.util.Iterator<Path.Node> nodos, String mensaje) {
        ConstraintViolation<?> violacion = mock(ConstraintViolation.class);
        Path ruta = mock(Path.class);
        when(ruta.iterator()).thenReturn(nodos);
        when(violacion.getPropertyPath()).thenReturn(ruta);
        when(violacion.getMessage()).thenReturn(mensaje);
        return violacion;
    }

    private static Path.Node nodo(String nombre) {
        Path.Node nodo = mock(Path.Node.class);
        when(nodo.getName()).thenReturn(nombre);
        return nodo;
    }
}

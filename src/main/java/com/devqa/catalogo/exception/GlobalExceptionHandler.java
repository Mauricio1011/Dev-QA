package com.devqa.catalogo.exception;

import com.devqa.catalogo.dto.DetalleError;
import com.devqa.catalogo.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MENSAJE_GENERICO = "No se pudo procesar la solicitud";
    private static final String VALOR_INVALIDO = "Valor inválido";
    private static final String MENSAJE_JSON = "El cuerpo de la solicitud no tiene un formato JSON válido";
    private static final String MENSAJE_CLAVE_DUPLICADA = "Ya existe un registro con un valor único duplicado";

    private static final Map<HttpStatus, String> TITULOS = Map.of(
            HttpStatus.BAD_REQUEST, "Solicitud inválida",
            HttpStatus.NOT_FOUND, "Recurso no encontrado",
            HttpStatus.CONFLICT, "Conflicto",
            HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
            HttpStatus.INTERNAL_SERVER_ERROR, "Error interno"
    );

    private static final Map<HttpStatus, String> MENSAJES = Map.of(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos",
            HttpStatus.NOT_FOUND, "No existe el recurso solicitado",
            HttpStatus.CONFLICT, "La solicitud entra en conflicto con el estado actual",
            HttpStatus.METHOD_NOT_ALLOWED, "El método HTTP no está permitido",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido no está soportado",
            HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Intente nuevamente."
    );

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DatoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarDatoDuplicado(
            DatoDuplicadoException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> manejarClaveDuplicada(
            DuplicateKeyException ex, HttpServletRequest request) {
        log.warn("Intento de persistir un valor único duplicado");
        return responder(HttpStatus.CONFLICT, MENSAJE_CLAVE_DUPLICADA, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestriccion(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<DetalleError> detalles = ex.getConstraintViolations().stream()
                .map(violacion -> new DetalleError(campoDe(violacion), texto(violacion.getMessage())))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, MENSAJES.get(HttpStatus.BAD_REQUEST), request, detalles);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado al atender una solicitud", ex);
        return responder(
                HttpStatus.INTERNAL_SERVER_ERROR,
                MENSAJES.get(HttpStatus.INTERNAL_SERVER_ERROR),
                request
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<DetalleError> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new DetalleError(error.getField(), texto(error.getDefaultMessage())))
                .toList();
        return ResponseEntity.status(status)
                .body(cuerpo(status, MENSAJES.get(HttpStatus.BAD_REQUEST), request, detalles));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(status).body(cuerpo(status, MENSAJE_JSON, request, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<DetalleError> detalles = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> {
                    String nombre = Objects.requireNonNullElse(
                            resultado.getMethodParameter().getParameterName(),
                            "parametro"
                    );
                    return resultado.getResolvableErrors().stream()
                            .map(error -> new DetalleError(nombre, texto(error.getDefaultMessage())));
                })
                .toList();
        return ResponseEntity.status(status)
                .body(cuerpo(status, MENSAJES.get(HttpStatus.BAD_REQUEST), request, detalles));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        String mensaje = conocido == null
                ? MENSAJE_GENERICO
                : MENSAJES.getOrDefault(conocido, MENSAJE_GENERICO);
        return new ResponseEntity<>(cuerpo(status, mensaje, request, List.of()), headers, status);
    }

    private static String campoDe(ConstraintViolation<?> violacion) {
        String hoja = null;
        for (Path.Node nodo : violacion.getPropertyPath()) {
            hoja = nodo.getName();
        }
        if (hoja == null || hoja.isBlank()) {
            return "solicitud";
        }
        return hoja;
    }

    private static String texto(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return VALOR_INVALIDO;
        }
        return mensaje;
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, HttpServletRequest request) {
        return responder(status, mensaje, request, List.of());
    }

    private ResponseEntity<ErrorResponse> responder(
            HttpStatus status, String mensaje, HttpServletRequest request, List<DetalleError> errores) {
        ErrorResponse cuerpo = new ErrorResponse(
                Instant.now(),
                status.value(),
                titulo(status),
                mensaje,
                request.getRequestURI(),
                errores
        );
        return ResponseEntity.status(status).body(cuerpo);
    }

    private ErrorResponse cuerpo(
            HttpStatusCode status, String mensaje, WebRequest request, List<DetalleError> errores) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                titulo(status),
                mensaje,
                ruta(request),
                errores
        );
    }

    private static String titulo(HttpStatusCode status) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        if (conocido == null) {
            return "Error";
        }
        return TITULOS.getOrDefault(conocido, conocido.getReasonPhrase());
    }

    private static String ruta(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}

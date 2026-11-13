# Catálogo de productos (HU-01)

Microservicio REST con operaciones CRUD sobre productos, persistencia en MongoDB y las prácticas de calidad pedidas en la historia: excepciones de negocio, pruebas unitarias y de integración, cobertura con JaCoCo y análisis estático con SonarQube.

La línea base del repositorio permanece en `main`. Esta implementación vive en `feature/hu-01-microservicio-crud`.

## Requisitos

- JDK 17
- Maven 3.9 o superior
- Docker, en ejecución, para las pruebas de integración y para levantar MongoDB o SonarQube

## Arranque

```bash
docker compose up -d mongodb
mvn spring-boot:run
```

La API queda en `http://localhost:8080`. La base por defecto es `mongodb://localhost:27017/catalogo`. Se puede cambiar con la variable `MONGODB_URI`.

## API

| Método | Ruta | Éxito |
| --- | --- | --- |
| `POST` | `/api/productos` | 201 |
| `GET` | `/api/productos` | 200 |
| `GET` | `/api/productos?categoria=Audio` | 200 |
| `GET` | `/api/productos/{id}` | 200 |
| `PUT` | `/api/productos/{id}` | 200 |
| `DELETE` | `/api/productos/{id}` | 204 |

Ejemplo de alta:

```json
{
  "nombre": "Teclado mecanico",
  "descripcion": "Switches rojos",
  "precio": 499.90,
  "stock": 10,
  "categoria": "Perifericos",
  "sku": "TEC-001",
  "activo": true
}
```

Reglas de entrada:

- `nombre`, `precio`, `stock`, `categoria` y `sku` son obligatorios.
- `precio` debe ser mayor que cero y admite hasta 2 decimales.
- `stock` no puede ser negativo.
- `sku` tiene de 3 a 20 caracteres en mayúsculas, dígitos o guion, y es único.
- Si `activo` no viene, el producto queda activo.
- Una descripción en blanco se guarda como nula.

## Errores

`GlobalExceptionHandler` intercepta las excepciones de negocio, las de validación y los fallos inesperados. El cuerpo siempre tiene esta forma:

```json
{
  "timestamp": "2026-11-12T18:00:00Z",
  "status": 409,
  "error": "Conflicto",
  "message": "Ya existe un producto con el SKU TEC-001",
  "path": "/api/productos",
  "errores": []
}
```

| Situación | HTTP |
| --- | --- |
| Datos de entrada inválidos o JSON malformado | 400 |
| Producto inexistente | 404 |
| SKU duplicado | 409 |
| Método no permitido | 405 |
| Contenido distinto de JSON | 415 |
| Fallo no controlado | 500 |

En un 400 de validación, `errores` lista `campo` y `mensaje`. Un 500 no devuelve el detalle interno.

## Pruebas y cobertura

```bash
mvn verify
```

- `ProductoServiceTest` cubre la lógica de negocio con Mockito, incluidos los casos de éxito, ausencia y duplicado.
- `ProductoIntegrationTest` recorre los endpoints contra un MongoDB real levantado por Testcontainers y comprueba estado HTTP y JSON.
- `GlobalExceptionHandlerTest` fija el contrato de las respuestas de error.

JaCoCo publica el reporte HTML en `target/site/jacoco/index.html` y el XML en `target/site/jacoco/jacoco.xml`. `mvn verify` falla si la cobertura de líneas o de ramas del proyecto baja de 80 %. La clase de arranque queda fuera de esa medición porque no tiene lógica de negocio.

## SonarQube

```bash
docker compose up -d sonarqube
```

Cuando `http://localhost:9000` responda, entra con `admin` / `admin`, cambia la contraseña y genera un token. Después:

```bash
mvn verify sonar:sonar "-Dsonar.host.url=http://localhost:9000" "-Dsonar.token=EL_TOKEN"
```

El análisis espera el Quality Gate. El proyecto no debe reportar bugs ni vulnerabilidades, y la cobertura que consume SonarQube es la de JaCoCo.

## Estructura

```text
src/main/java/com/devqa/catalogo
├── controller        API REST
├── service           reglas de negocio
├── repository        Spring Data MongoDB
├── domain            documento Producto
├── dto               solicitud, respuesta y error
├── mapper            traducción entre capas
└── exception         RecursoNoEncontradoException, DatoDuplicadoException
                      y el manejador global
```

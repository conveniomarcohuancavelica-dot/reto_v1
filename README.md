# Reto Backend — API Gateway + Microservicios + OAuth2

Arquitectura de microservicios en Java 17 / Spring Boot para registrar y consultar pedidos,
con API Gateway como único punto público, autenticación basada en JWT y trazabilidad
end-to-end mediante `X-Trace-Id`.

## Arquitectura

```
Postman ──> API Gateway (8443) ──> Order Service (8081) ──> Inventory Service (8082)
                 │                                                    │
                 └──────────────> Auth Service (9000)          MySQL (inventorydb)
                                                                       
                                   MySQL (orderdb)
```

- **API Gateway** (Spring Cloud Gateway): único punto público. Valida el JWT, enruta y
  propaga `X-Trace-Id`. No contiene lógica de negocio.
- **Order Service** (WebFlux + JPA): administra pedidos, estados e historial. Llama a
  Inventory Service vía WebClient (HTTP reactivo).
- **Inventory Service** (WebFlux + JPA): administra productos, stock y disponibilidad.
- **Auth Service**: emite JWT firmados con HS256 (OAuth2/JWT simplificado, sin depender
  de un Identity Provider externo como Keycloak).
- **MySQL**: una base de datos por microservicio (`orderdb`, `inventorydb`), cada uno
  dueño de su propia persistencia.

## Por qué estas decisiones técnicas

- **Auth Service propio en vez de Keycloak**: mismo estándar (OAuth2 + JWT), pero con
  control total del flujo de emisión/validación del token, sin depender de un componente
  externo adicional para un reto de este alcance.
- **MySQL**: motor relacional estable y ampliamente soportado, adecuado para persistir
  pedidos, historial e inventario con integridad transaccional.
- **WebClient (reactivo)** en vez de RestTemplate: se pide explícitamente comunicación
  síncrona mediante HTTP reactivo; WebClient es no bloqueante y es el estándar en WebFlux.
- **Persistencia JPA envuelta en `Mono.fromCallable` + `Schedulers.boundedElastic()`**:
  Spring Data JPA (blocking) no tiene una alternativa reactiva 100% madura para MySQL
  (R2DBC), así que las operaciones bloqueantes se ejecutan en un scheduler dedicado para
  no bloquear el event loop de Netty.
- **Sin Saga/Kafka/Outbox/CQRS**: no se exige y el flujo es síncrono de bajo acoplamiento
  distribuido (Order Service solo depende de Inventory Service en el momento de crear
  el pedido).

## Estados de un pedido

```
PENDING ──> CONFIRMED ──> CANCELLED
   │                          ↑
   └──> FAILED     PENDING ───┘ (también se puede cancelar desde PENDING)
```

- `PENDING`: recién creado, aún no confirmado.
- `CONFIRMED`: hay stock, el pedido se confirmó y se reservó el stock.
- `CANCELLED`: cancelado (solo permitido desde `PENDING` o `CONFIRMED`).
- `FAILED`: no se pudo confirmar por falta de stock (estado terminal, opcional).

Las transiciones inválidas (ej. cancelar un pedido ya `CANCELLED`) se rechazan con
`409 INVALID_TRANSITION`. Todo cambio de estado se persiste en `order_history`, junto
con el `X-Trace-Id` de la operación que lo originó.

## Endpoints (todos a través del API Gateway, puerto 8443)

| Método | Ruta                                  | Descripción                          | Auth |
|--------|----------------------------------------|---------------------------------------|------|
| POST   | `/api/v1/auth/login`                  | Login, devuelve JWT                   | No   |
| POST   | `/api/v1/orders`                      | Crear pedido                          | Sí   |
| GET    | `/api/v1/orders/{orderId}`            | Consultar estado de un pedido         | Sí   |
| POST   | `/api/v1/orders/{orderId}/cancel`     | Cancelar pedido                       | Sí   |
| GET    | `/api/v1/orders/{orderId}/history`    | Historial de cambios de estado        | Sí   |
| GET    | `/api/v1/inventory/availability`      | Consultar disponibilidad de un producto | Sí |

Usuarios de prueba (Auth Service): `client / client123` y `admin / admin123`.

### Formato de error estándar

```json
{
  "timestamp": "2026-08-12T10:00:00Z",
  "status": 409,
  "code": "STOCK_INSUFFICIENT",
  "message": "Stock insuficiente para el producto: PROD-003",
  "traceId": "a1b2c3d4-..."
}
```

### Trazabilidad (`X-Trace-Id`)

- El Gateway genera un `X-Trace-Id` si el cliente no lo envía, o respeta el que llega.
- Se propaga como header hacia Order Service e Inventory Service.
- Aparece en cada línea de log (vía MDC/Logback) y en toda respuesta (éxito o error).

## Cómo ejecutar

Requiere Docker y Docker Compose.

```bash
docker compose up --build
```

Servicios expuestos:
- API Gateway: `http://localhost:8443` (único punto público a usar desde Postman)
- Auth Service: `http://localhost:9000` (accesible también vía Gateway en `/api/v1/auth/login`)
- Order Service: `http://localhost:8081` (uso interno / debug)
- Inventory Service: `http://localhost:8082` (uso interno / debug)
- MySQL order: `localhost:3316` | MySQL inventory: `localhost:3317`
  (se evita a propósito el puerto 3306 por defecto, para no chocar con una
  instalación local de MySQL en tu máquina)

## Probar con Postman

1. Importar `postman/Reto-Backend.postman_collection.json`.
2. Ejecutar en orden: **1. Login** (guarda el token automáticamente en una variable de
   colección) → **3. Crear pedido** → **5. Consultar estado** → **6. Historial** →
   **7. Cancelar**.
3. La request **4** usa `PROD-003` (sin stock) para ver el flujo de `FAILED`.
4. La request **9** demuestra el `401` por token ausente.

Productos precargados en Inventory Service: `PROD-001` (stock 15), `PROD-002` (stock 50),
`PROD-003` (stock 0).

## Tests

```bash
cd order-service && mvn test
cd inventory-service && mvn test
cd auth-service && mvn test
```

## Documentación de API (OpenAPI/Swagger)

- Auth Service: `http://localhost:9000/swagger-ui.html`
- Order Service: `http://localhost:8081/swagger-ui.html`
- Inventory Service: `http://localhost:8082/swagger-ui.html`

## Auditoría y correcciones aplicadas (12 ago 2026)

Se hizo una revisión completa de funcionalidad, Lombok, dependencias y
dockerización. Cambios aplicados:

- **MapStruct eliminado**: estaba declarado en `order-service` e
  `inventory-service` (dependencia + processor) pero nunca se usaba —
  el mapeo siempre se hizo a mano con `toResponse()`.
- **`@Transactional` inefectivo corregido en `order-service`**: estaba en
  un método privado invocado como `this.saveHistory(...)` (auto-invocación),
  lo que hace que el proxy de Spring nunca lo intercepte. Se movió a un
  bean nuevo, `OrderTransitionWriter`, para que el pedido y su historial se
  guarden de forma atómica de verdad.
- **`@Transactional` inefectivo corregido en `inventory-service`**: estaba
  sobre un método que devuelve `Mono` y ejecuta el trabajo real en otro
  hilo (`subscribeOn(boundedElastic)`) — Spring cerraba la transacción
  antes de que el trabajo ocurriera. Se quitó (la atomicidad real la da el
  único `repository.save()`, que ya es transaccional por sí mismo, más el
  bloqueo optimista vía `@Version`).
- **Actuator agregado a los 3 servicios** (auth/order/inventory) que ya
  referenciaban `/actuator/health` en su configuración de seguridad pero no
  tenían la dependencia — el endpoint no existía.
- **`docker-compose.yml` reescrito** con healthchecks reales (via
  `/actuator/health`) en los 4 servicios Java y `depends_on` con
  `condition: service_healthy` en toda la cadena
  (`mysql → inventory-service → order-service → api-gateway`), en vez de
  solo esperar a que el contenedor arrancara.
- **Puerto de `mysql-order` movido de 3306 a 3316** (host) para evitar
  choques con instalaciones locales de MySQL, que casi siempre usan 3306
  por defecto.
- **Fuga de MDC corregida en `api-gateway`**: `TraceIdGlobalFilter` ponía el
  `traceId` en el MDC pero nunca lo limpiaba al terminar la petición, a
  diferencia de `order-service`/`inventory-service`. En el pool de hilos
  compartido de Netty esto podía filtrar el `traceId` de una petición hacia
  los logs de otra petición concurrente.
- Tests de `order-service` actualizados para reflejar el nuevo
  `OrderTransitionWriter`.
- **Columna `id` con tipo inconsistente**: `UUID id` con
  `GenerationType.UUID` mapea a `BINARY(16)` por defecto en Hibernate 6 +
  MySQL, pero `data.sql` inserta con la función `UUID()` de MySQL (string
  de 36 caracteres) — se corrompía al chocar con `BINARY(16)`. Se forzó
  `hibernate.type.preferred_uuid_jdbc_type: CHAR` en order-service e
  inventory-service. **Si ya tenías un volumen de Docker levantado con el
  tipo viejo, hay que recrearlo**: `docker compose down -v && docker
  compose up --build` (`ddl-auto: update` no corrige el tipo de una
  columna ya existente).
- **`lombok.config` agregado en la raíz** fijando el uso de Lombok a lo
  estándar en los 4 servicios (ya se usaba solo
  `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor/@Builder` en
  entidades — nunca `@Data`/`@Value`/`@EqualsAndHashCode`/`@ToString`, que
  son las que suelen chocar con JPA).

Verificado: los 53 archivos `.java` compilan sin errores de sintaxis
(`javac 21`, sin classpath de Spring por restricciones de red del entorno de
desarrollo). Postman collection revisada endpoint por endpoint contra los
controllers/DTOs/`SecurityConfig` — sin inconsistencias.

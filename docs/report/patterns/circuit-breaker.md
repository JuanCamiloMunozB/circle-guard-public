# Patrón: Circuit Breaker

> Estado: implementado en `auth-service` (IdentityClient → identity-service) y
> `dashboard-service` (PromotionClient → promotion-service). Historia de Usuario: HU-06.
> Biblioteca: Resilience4j 2.2.0 (`resilience4j-spring-boot3`) + Spring AOP.

## 1. Problema que resuelve

Las llamadas REST síncronas entre microservicios introducen acoplamiento temporal:
cuando un servicio downstream se degrada, los hilos del servicio upstream se quedan
bloqueados esperando timeouts. En pocos segundos:

1. **Agotamiento del thread pool del Tomcat upstream.** Las peticiones de los
   usuarios encolan, ninguna llega a completarse.
2. **Cascada hacia los clientes.** El gateway/mobile recibe 504s y el problema se
   atribuye al upstream, no al verdadero culpable.
3. **Retry tormenta sobre el downstream caído.** Sin freno, cada usuario reintenta
   y el downstream nunca se recupera porque sigue recibiendo más carga.

En CircleGuard hay dos puntos donde esto es crítico:

- **auth-service → identity-service** en el flujo de login. Si identity-service no
  resuelve el `anonymousId`, el login se queda colgado y el usuario no puede entrar.
- **dashboard-service → promotion-service** para los stats agregados. Si promotion
  no responde, el dashboard se cuelga al cargar.

## 2. Cómo se implementó

### 2.1 Mecanismo — Resilience4j con AOP

Cada llamada remota va anotada con `@CircuitBreaker`. El aspecto de Spring AOP
proxy-ea el bean: invocar el método pasa por el Circuit Breaker, que decide si
ejecutarlo (CLOSED), saltárselo (OPEN) o sondear (HALF_OPEN). Cuando algo falla
o el circuito está OPEN, se invoca el `fallbackMethod` declarado.

```java
@CircuitBreaker(name = "identityService", fallbackMethod = "fallbackGetAnonymousId")
public Optional<UUID> getAnonymousId(String realIdentity) {
    ...
}

private Optional<UUID> fallbackGetAnonymousId(String realIdentity, Throwable cause) {
    log.warn("identity-service unavailable, returning empty");
    return Optional.empty();
}
```

Regla obligatoria de la firma: el fallback tiene **la misma firma + un Throwable
extra al final**. Si no coincide, R4j la rechaza en tiempo de arranque.

### 2.2 Dos instancias independientes del breaker

| Instancia | Servicio que la consume | Punto protegido |
|---|---|---|
| `identityService` | auth-service | `IdentityClient.getAnonymousId` |
| `promotionService` | dashboard-service | `PromotionClient.getHealthStats` y `getHealthStatsByDepartment` |

Separadas a propósito: que identity-service se caiga no debe abrir el breaker que
protege a promotion. Cada una tiene su propia ventana, umbrales y `waitDurationInOpenState`.

### 2.3 Parámetros del breaker — parametrizados vía env vars

Cada umbral se define en `application.yml` como `${CB_*:default}` para respetar el
patrón **External Configuration Store** (Ops puede afinar por ambiente sin recompilar):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      identityService:
        slidingWindowSize: ${CB_IDENTITY_WINDOW_SIZE:10}
        minimumNumberOfCalls: ${CB_IDENTITY_MIN_CALLS:5}
        failureRateThreshold: ${CB_IDENTITY_FAILURE_RATE:50}
        slowCallRateThreshold: ${CB_IDENTITY_SLOW_RATE:50}
        slowCallDurationThreshold: ${CB_IDENTITY_SLOW_DURATION:2s}
        permittedNumberOfCallsInHalfOpenState: ${CB_IDENTITY_HALF_OPEN_CALLS:3}
        waitDurationInOpenState: ${CB_IDENTITY_WAIT_OPEN:10s}
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - org.springframework.web.client.RestClientException
          - java.io.IOException
          - java.lang.IllegalStateException
```

**Justificación de los defaults:**

- `slidingWindowSize=10`, `minimumNumberOfCalls=5`: ventana corta para reaccionar
  rápido en ambientes con poco tráfico (escenario de demo y dev).
- `failureRateThreshold=50%`: estándar de la industria — abre cuando la mitad de
  las llamadas en la ventana fallan.
- `slowCallDurationThreshold=2s`: cualquier llamada que tarde más de 2s cuenta
  como lenta. Útil para detectar el patrón "el servicio responde pero arrastra".
- `waitDurationInOpenState=10s`: período de cuarentena antes de sondear. Lo
  suficiente para que un restart o un autoscale termine.
- `permittedNumberOfCallsInHalfOpenState=3`: tres llamadas de prueba evitan
  reabrir el breaker por una flap aislada.
- `recordExceptions`: solo errores transitorios reales abren el breaker. Una
  `IllegalArgumentException` por input mal formado **no** debería abrir nada.

### 2.4 Respuesta degradada controlada

| Cliente | Tipo de retorno | Fallback | Cómo lo trata el caller |
|---|---|---|---|
| `IdentityClient.getAnonymousId` | `Optional<UUID>` | `Optional.empty()` | `LoginController` lo trata como **HTTP 503** — nunca se emite un JWT con identidad vacía |
| `PromotionClient.getHealthStats` | `Map<String,Object>` | `{ "error": "Service unavailable", "timestamp": ... }` | El dashboard renderiza estado degradado con la clave `error` |
| `PromotionClient.getHealthStatsByDepartment` | `Map<String,Object>` | `{ "error": "Service unavailable", "department": "<dept>", "timestamp": ... }` | Mismo manejo + el label del departamento se preserva para la UI |

**Regla de oro:** el fallback NUNCA propaga la excepción original. Tampoco lanza
una nueva del tipo del bug original. Devuelve un valor controlado que el caller
puede inspeccionar.

## 3. Diagrama de estados

Ver `docs/diagrams/circuit-breaker.puml` — máquina de estados CLOSED → OPEN →
HALF_OPEN con las transiciones gobernadas por los umbrales.

## 4. Anti-patrones evitados

- **`try/catch` que oculta la excepción al CB.** El `PromotionClient` original
  envolvía la llamada en `try { ... } catch (Exception e) { return errorMap; }`.
  Con CB anotado encima, esto sería un **bug crítico**: R4j nunca vería un
  fallo, jamás abriría el circuito, y el downstream seguiría siendo
  bombardeado. Solución: dejar que la excepción se propague hacia R4j; el
  `fallbackMethod` la atrapa después.
- **Compartir una sola instancia del breaker entre downstreams distintos.** Si
  identity-service y promotion-service compartieran breaker, una caída en uno
  abriría llamadas al otro innecesariamente. Cada downstream tiene su
  `instances.<nombre>` propio.
- **`recordExceptions` con `Exception.class`.** Atrapar *todo* hace que un
  `IllegalArgumentException` por validación abra el breaker. Solo se registran
  excepciones de **red/timeout/contrato** transitorias.
- **Fallback que devuelve un valor "válido" pero falso** (e.g. UUID cero para
  identity). El caller podría no detectarlo y emitir un JWT con anonymousId
  nulo — fuga de seguridad. Por eso `Optional<UUID>` y no UUID sentinela.
- **Retry sin Circuit Breaker delante.** Reintentar contra un downstream caído
  empeora la caída. El CB se evalúa **primero**; si está OPEN, ni siquiera se
  intenta. Retry interno (cuando se añada) irá *después* del CB en la cadena.

## 5. Validación — pruebas unitarias

### 5.1 `IdentityClientCircuitBreakerTest`
- `downstreamFailure_runsFallback_doesNotPropagateException`: mockea
  `RestTemplate` para lanzar `RestClientException`. Verifica que
  `getAnonymousId` devuelve `Optional.empty()` **sin propagar la excepción**.
- `repeatedFailures_openTheCircuit_andStopHittingTheDownstream`: con
  `slidingWindowSize=4` y todas las llamadas fallando, verifica:
  1. El estado del breaker es CLOSED al inicio.
  2. Tras 4 fallos, el estado pasa a OPEN.
  3. 10 llamadas posteriores van al fallback **sin** invocar a RestTemplate
     (`verify(times(4))` confirma que el downstream no recibió más tráfico
     después de abrirse el circuito).

### 5.2 `PromotionClientCircuitBreakerTest`
- `downstreamFailure_runsFallback_returnsDegradedMap`: verifica el mapa de
  fallback (`error=Service unavailable`, `timestamp` presente).
- `departmentVariant_preservesDepartmentInFallback`: el label del
  departamento debe sobrevivir en la respuesta degradada para que la UI siga
  rotulando bien.
- `repeatedFailures_openTheCircuit_andStopHittingTheDownstream`: análogo al
  anterior, demuestra que el breaker corta el bombardeo.

### 5.3 Tests de regresión actualizados
- `LoginControllerTest.shouldLoginSuccessfullyAndReturnAnonymizedToken` — ahora
  mockea `Optional.of(uuid)` (la nueva firma).
- `LoginControllerTest.shouldReturn503WhenIdentityServiceIsDegraded` (nuevo) —
  cuando `IdentityClient` devuelve `Optional.empty()`, el endpoint responde 503
  y `JwtTokenService.generateToken` **nunca** se llama.

## 6. Pendiente para Ops (futura iteración de observabilidad)

- Exponer los breakers en `/actuator/health` (`registerHealthIndicator: true` ya
  habilitado en YAML).
- Dashboard de Grafana con las métricas R4j: estado del circuito por instancia,
  tasa de fallos, latencia p50/p95, número de llamadas no permitidas (OPEN).
- Alertas: si un breaker permanece OPEN más de X minutos, notificar a Ops.

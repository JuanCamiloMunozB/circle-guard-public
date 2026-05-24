# Especificación — Pruebas de Integración

> Documento generado para HU-09 (paso 9.4). Cada caso de prueba sigue el
> formato estandarizado en la sección 4 del `taller2-report.md`: identificador
> único, nombre, descripción, prerrequisitos, entradas, acciones, salida
> esperada y criterios de aceptación.
>
> **Total: 21 pruebas de integración** en 9 clases bajo
> `services/<svc>/src/test/java/.../**`. Todas tagged `@Tag("integration")` —
> excluidas del task `test` por defecto, ejecutables vía
> `./gradlew integrationTest` (con Docker disponible para los Testcontainers).

## Convenciones

- **Identificadores PI-xxx** correlativos al inventario heredado de Taller 2.
- Infraestructura embebida usada por cada test:
  - **EmbeddedKafka** (`spring-kafka-test`) para los topics inter-servicio.
  - **Neo4j Harness** (`org.neo4j.harness:neo4j-harness`) para el grafo.
  - **jedis-mock** para Redis.
  - **H2** en modo PostgreSQL para JPA.
  - **MockServerContainer** (Testcontainers) para el enlace REST auth→identity
    (PI-020, PI-021, introducido en HU-09).

---

# form-service

## Form Service — Survey Submission & Questionnaire Management

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-001 |
| **Nombre** | Payload de survey.submitted contiene campos obligatorios |
| **Descripción** | Verifica, con el contexto Spring completo cargado y un broker Kafka embebido (`@EmbeddedKafka`), que al invocar `HealthSurveyService.submitSurvey()` el `KafkaTemplate` real publica un registro en el topic `survey.submitted` cuya clave es el `anonymousId` y cuyo payload contiene los campos `anonymousId`, `hasSymptoms=true` y `timestamp`. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado con perfil `test`; broker Kafka embebido con el topic creado; repositorio, `QuestionnaireService` y `SymptomMapper` mockeados como `@MockBean`; consumidor de prueba suscrito al topic vía `KafkaTestUtils.consumerProps()`. |
| **Entradas** | `HealthSurvey` con `anonymousId` válido; cuestionario con una pregunta de fiebre; `SymptomMapper` configurado para retornar `true`. |
| **Acciones** | Se invoca `submitSurvey()`; el consumidor del test recupera el registro con `KafkaTestUtils.getSingleRecord(topic, Duration.ofSeconds(10))`. |
| **Salida Esperada** | El registro consumido tiene `key = anonymousId.toString()` y el payload contiene `anonymousId`, `hasSymptoms=true` y `timestamp` no nulo. |
| **Criterios de Aceptación** | El registro llega al topic dentro del timeout y los tres campos del payload coinciden con los esperados. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-002 |
| **Nombre** | Payload de certificate.validated contiene adminId y estado APPROVED |
| **Descripción** | Verifica que al aprobar un certificado, el evento `certificate.validated` publicado en el broker Kafka embebido contiene el `adminId` del aprobador y el campo `status="APPROVED"`, garantizando la trazabilidad de la validación. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado con broker Kafka embebido y topic `certificate.validated`; encuesta en estado `PENDING` disponible en el repositorio mockeado; consumidor de prueba suscrito al topic. |
| **Entradas** | `surveyId`, `ValidationStatus.APPROVED`, `adminId` del administrador. |
| **Acciones** | Se invoca `validateSurvey()`; el consumidor del test lee el registro emitido al topic. |
| **Salida Esperada** | El payload contiene `status="APPROVED"` y `adminId` coincide con el identificador serializado del administrador. |
| **Criterios de Aceptación** | `KafkaTestUtils.getSingleRecord()` retorna el registro dentro del timeout y ambos campos coinciden. |

---

# promotion-service

## Promotion Service — Health Status Transitions & Contact Tracing

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-003 |
| **Nombre** | SurveyListener con síntomas invoca actualización a estado SUSPECT |
| **Descripción** | Verifica el flujo Kafka completo en `promotion-service`: un evento publicado al topic `survey.submitted` en el broker embebido es consumido por el `@KafkaListener` real (`SurveyListener`), que delega la actualización de estado al `HealthStatusService` cuando `hasSymptoms=true`. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado con perfil `test`; broker Kafka embebido con topic `survey.submitted`; `HealthStatusService` mockeado como `@MockBean`; el listener real registrado en el `KafkaListenerEndpointRegistry`; espera a la asignación de partición vía `ContainerTestUtils.waitForAssignment()`. |
| **Entradas** | Mapa de evento con `anonymousId="integration-user-001"` y `hasSymptoms=true` publicado al topic con `KafkaTemplate`. |
| **Acciones** | Se publica el evento al topic; `Awaitility.await()` espera a que el listener consuma y delegue. |
| **Salida Esperada** | `healthStatusService.updateStatus("integration-user-001", "SUSPECT")` es invocado dentro del timeout. |
| **Criterios de Aceptación** | `await().atMost(10s).untilAsserted(verify(...))` completa sin timeout. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-004 |
| **Nombre** | SurveyListener sin síntomas no actualiza estado de salud |
| **Descripción** | Verifica que cuando el evento `survey.submitted` indica `hasSymptoms=false`, el listener consume el mensaje del broker pero no invoca `HealthStatusService`, evitando cambios de estado innecesarios y reduciendo la carga en el grafo Neo4j. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado; broker Kafka embebido; `HealthStatusService` mockeado; listener registrado y con partición asignada. |
| **Entradas** | Mapa de evento con `anonymousId="integration-user-002"` y `hasSymptoms=false` publicado al topic. |
| **Acciones** | Se publica el evento; tras un `pollDelay` de 3 segundos se verifica que el mock no fue invocado. |
| **Salida Esperada** | `healthStatusService.updateStatus()` nunca es invocado. |
| **Criterios de Aceptación** | `verify(healthStatusService, never()).updateStatus(anyString(), anyString())` pasa después del polling. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-013 |
| **Nombre** | Invalidación de círculo previene propagación de estado de riesgo |
| **Descripción** | Verifica con contexto Spring + Neo4j embebido + Redis embebido (jedis-mock) que cuando un administrador marca un círculo como inválido, una promoción posterior de uno de sus miembros a CONFIRMED no propaga el estado de riesgo al resto del círculo. Esta es la herramienta de corrección manual usada por el centro de salud cuando se detectan falsos positivos. |
| **Prerrequisitos/Condiciones** | Contexto Spring completo con `@SpringBootTest`; Neo4j embebido y Redis embebido configurados vía `@DynamicPropertySource`; `KafkaTemplate` mockeado; grafo limpio en `@BeforeEach`. |
| **Entradas** | Dos `UserNode` A y B en estado ACTIVE; círculo `"RiskGroup"` con ambos como miembros; el círculo se invalida con `toggleCircleValidity`. |
| **Acciones** | Se invalida el círculo, se purgan encounters previos, y se invoca `statusService.updateStatus("A", "CONFIRMED")`. |
| **Salida Esperada** | Tras la promoción de A, el usuario B mantiene su estado ACTIVE en Neo4j; la invalidación del círculo bloqueó la propagación. |
| **Criterios de Aceptación** | `assertThat(userRepository.findById("B").get().getStatus()).isEqualTo("ACTIVE")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-014 |
| **Nombre** | Force-fence de un círculo promueve todos sus miembros a PROBABLE |
| **Descripción** | Verifica que cuando un administrador ejecuta `forceFenceCircle()` sobre un círculo activo, todos sus miembros ACTIVE son promovidos a PROBABLE simultáneamente. Esta operación implementa la decisión manual del centro de salud de poner en cuarentena un grupo completo (e.g. el círculo de una clase con un brote). |
| **Prerrequisitos/Condiciones** | Contexto Spring + Neo4j embebido + Redis embebido; `KafkaTemplate` mockeado; usuarios A y B en estado ACTIVE como miembros del círculo `"Forced containment"`. |
| **Entradas** | UUID del círculo creado mediante `circleService.createCircle()`. |
| **Acciones** | Se invoca `circleService.forceFenceCircle(circleId)`. |
| **Salida Esperada** | Ambos usuarios A y B aparecen en Neo4j con `status=PROBABLE`. |
| **Criterios de Aceptación** | `assertThat(userRepository.findById("A").get().getStatus()).isEqualTo("PROBABLE")` y lo mismo para B. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-015 |
| **Nombre** | Resolución individual libera contacto directo si era el único riesgo |
| **Descripción** | Verifica que cuando el único usuario CONFIRMED (A) en una cadena `A → B` se resuelve, el usuario B en estado SUSPECT pasa automáticamente a ACTIVE. Es el caso más simple de re-evaluación del grafo: B solo estaba en riesgo por A. |
| **Prerrequisitos/Condiciones** | Contexto Spring con Neo4j embebido y Redis embebido; `KafkaTemplate` mockeado; grafo limpio en `@BeforeEach`. |
| **Entradas** | Nodos `A` (CONFIRMED) y `B` (SUSPECT) creados con relación `ENCOUNTERED` entre ellos vía Cypher. |
| **Acciones** | Se invoca `healthStatusService.resolveStatus("A")`. |
| **Salida Esperada** | `getStatus("B")` retorna `"ACTIVE"`. |
| **Criterios de Aceptación** | `assertEquals("ACTIVE", getStatus("B"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-016 |
| **Nombre** | Resolución no libera contacto si existe otra fuente de riesgo |
| **Descripción** | Verifica que cuando un usuario en SUSPECT tiene múltiples contactos con CONFIRMED (en este caso A y C), resolver únicamente a A NO basta para liberarlo: B mantiene su estado SUSPECT porque C todavía representa riesgo. |
| **Prerrequisitos/Condiciones** | Contexto Spring + Neo4j embebido + Redis embebido; KafkaTemplate mockeado. |
| **Entradas** | Tres nodos: A y C en CONFIRMED, B en SUSPECT; relaciones `A→B` y `C→B`. |
| **Acciones** | Se invoca `healthStatusService.resolveStatus("A")`. |
| **Salida Esperada** | `getStatus("B")` retorna `"SUSPECT"`. |
| **Criterios de Aceptación** | `assertEquals("SUSPECT", getStatus("B"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-017 |
| **Nombre** | Resolución multi-salto libera la cadena completa |
| **Descripción** | Verifica que cuando se resuelve el único nodo CONFIRMED de una cadena `A → B → C` con todos los demás en estado de riesgo, ambos B (SUSPECT) y C (PROBABLE) son liberados a ACTIVE. Esto valida el recorrido transitivo del grafo durante la re-evaluación. |
| **Prerrequisitos/Condiciones** | Contexto Spring + Neo4j embebido + Redis embebido; KafkaTemplate mockeado. |
| **Entradas** | A (CONFIRMED) → B (SUSPECT) → C (PROBABLE). |
| **Acciones** | Se invoca `healthStatusService.resolveStatus("A")`. |
| **Salida Esperada** | `getStatus("B")` y `getStatus("C")` retornan `"ACTIVE"`. |
| **Criterios de Aceptación** | Ambos `assertEquals("ACTIVE", ...)` pasan. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-018 |
| **Nombre** | Liberación parcial en malla preserva el riesgo restante |
| **Descripción** | Verifica el caso mixto: en una malla `A→B→C` con un riesgo lateral adicional `D→C`, resolver A debe liberar a B (cuyo único riesgo era A) pero NO debe liberar a C, porque D sigue siendo fuente de riesgo. Garantiza que el algoritmo de re-evaluación distingue correctamente entre nodos liberables y no liberables en mallas complejas. |
| **Prerrequisitos/Condiciones** | Contexto Spring + Neo4j embebido + Redis embebido; KafkaTemplate mockeado. |
| **Entradas** | A (CONFIRMED), B (SUSPECT), C (PROBABLE), D (SUSPECT); relaciones `A→B`, `B→C`, `D→C`. |
| **Acciones** | Se invoca `healthStatusService.resolveStatus("A")`. |
| **Salida Esperada** | B pasa a ACTIVE; C mantiene PROBABLE. |
| **Criterios de Aceptación** | `assertEquals("ACTIVE", getStatus("B"))` y `assertEquals("PROBABLE", getStatus("C"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-019 |
| **Nombre** | Cascada de promoción sobre 10k nodos finaliza dentro del umbral relajado |
| **Descripción** | Benchmark de integración que mide el tiempo de propagación de un cambio de estado a CONFIRMED sobre un grafo con 10.000 nodos y ~15.000 relaciones aleatorias en Neo4j embebido. Verifica el requisito no funcional NFR-1 (cascada en menos de 1s en producción) con el umbral relajado a 3s por correr en embedded Neo4j (que comparte heap con la JVM del test). Adicionalmente confirma propagación multi-tier: contactos directos pasan a SUSPECT (L1) y contactos de contactos pasan a PROBABLE (L2). |
| **Prerrequisitos/Condiciones** | Contexto Spring + Neo4j embebido + Redis embebido; KafkaTemplate mockeado; grafo limpio cargado con 10.000 nodos User, 50 relaciones desde el rootUser y hasta 15.000 relaciones aleatorias en `@BeforeEach`. |
| **Entradas** | UUID del `rootUser` creado durante el setup; warmup previo sobre `user-1` para activar JIT e índices de Neo4j. |
| **Acciones** | Se mide `System.currentTimeMillis()` antes y después de `healthStatusService.updateStatus(rootUser, "CONFIRMED")`. Luego se consultan dos Cypher: contactos directos en SUSPECT (L1) y contactos de contactos en PROBABLE (L2). |
| **Salida Esperada** | Duración total menor a 3.000 ms; al menos un contacto L1 en SUSPECT; al menos un contacto L2 en PROBABLE. |
| **Criterios de Aceptación** | `assertTrue(duration < 3000)`; `assertTrue(suspectCount > 0)`; `assertTrue(probableCount > 0)`. |

---

# dashboard-service

## Dashboard Service — Analytics & K-Anonymity Enforcement

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-005 |
| **Nombre** | getCampusSummary retorna datos del PromotionClient en contexto Spring |
| **Descripción** | Verifica que con el contexto Spring completo cargado, `AnalyticsService.getCampusSummary()` delega correctamente al `PromotionClient` y retorna la respuesta sin modificación, validando que el bean `PromotionClient` está correctamente inyectado. |
| **Prerrequisitos/Condiciones** | `PromotionClient` mockeado como `@MockBean` en contexto Spring; retorna un mapa con `totalUsers=350`. |
| **Entradas** | Ninguna entrada; consulta sin parámetros. |
| **Acciones** | Se invoca `analyticsService.getCampusSummary()` a través del bean inyectado por Spring. |
| **Salida Esperada** | El resultado tiene `totalUsers=350`; `promotionClient.getHealthStats()` es invocado exactamente una vez. |
| **Criterios de Aceptación** | `assertEquals(350, result.get("totalUsers"))`; `verify(promotionClient, times(1)).getHealthStats()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-006 |
| **Nombre** | Departamento con población menor a K es enmascarado en contexto Spring |
| **Descripción** | Verifica que con el contexto Spring completo, la cadena `AnalyticsService` que invoca a `PromotionClient` y luego pasa por `KAnonymityFilter` aplica correctamente el enmascaramiento cuando la población del departamento es inferior al umbral de privacidad. |
| **Prerrequisitos/Condiciones** | `PromotionClient` mockeado para retornar `{totalUsers: 3, department: "Philosophy"}`. |
| **Entradas** | Nombre de departamento `"Philosophy"`. |
| **Acciones** | Se invoca `getDepartmentStats("Philosophy")`. |
| **Salida Esperada** | `totalUsers` es `"<5"` en el resultado; el mapa contiene la clave `"note"`. |
| **Criterios de Aceptación** | `assertEquals("<5", result.get("totalUsers"))`; `assertTrue(result.containsKey("note"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-007 |
| **Nombre** | Fallo del PromotionClient retorna mapa de error sin excepción |
| **Descripción** | Verifica que cuando el `PromotionClient` retorna un mapa con la clave `"error"` (comportamiento de fallback ante fallo HTTP), el `AnalyticsService` no lanza excepción y retorna el mapa de error al cliente del dashboard. |
| **Prerrequisitos/Condiciones** | `PromotionClient` mockeado para retornar `{error: "Service unavailable"}`. |
| **Entradas** | Ninguna entrada; consulta sin parámetros. |
| **Acciones** | Se invoca `getCampusSummary()`; se inspecciona el resultado. |
| **Salida Esperada** | El resultado no es nulo y contiene la clave `"error"`. |
| **Criterios de Aceptación** | `assertTrue(result.containsKey("error"))`; no se lanza ninguna excepción. |

---

# notification-service

## Notification Service — Event-Driven Alert Dispatch

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-008 |
| **Nombre** | Evento de estado SUSPECT despacha notificación y sincroniza LMS |
| **Descripción** | Verifica que un evento JSON con `status=SUSPECT` publicado en el broker Kafka embebido al topic `promotion.status.changed` es consumido por el `ExposureNotificationListener` real, que delega tanto al `NotificationDispatcher` como al `LmsService`, integrando en un único test el flujo completo desde Kafka hasta el listener y sus dependencias downstream. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado; broker Kafka embebido con el topic `promotion.status.changed`; `NotificationDispatcher`, `LmsService`, servicios de email/sms/push mockeados como `@MockBean`; espera a la asignación de partición. |
| **Entradas** | Cadena JSON `{"anonymousId":"user-int-001","status":"SUSPECT","timestamp":1234567890}` publicada al topic con `KafkaTemplate<String,String>`. |
| **Acciones** | Se publica el mensaje; `Awaitility` espera a que ambas dependencias mockeadas sean invocadas. |
| **Salida Esperada** | `dispatcher.dispatch("user-int-001", "SUSPECT")` invocado una vez; `lmsService.syncRemoteAttendance("user-int-001", "SUSPECT")` invocado una vez. |
| **Criterios de Aceptación** | `await().atMost(10s).untilAsserted(...)` completa sin timeout y ambas verificaciones de Mockito pasan. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-009 |
| **Nombre** | Evento de estado ACTIVE no genera notificaciones |
| **Descripción** | Verifica que el estado `ACTIVE`, que es el estado normal del sistema, no genera notificaciones ni sincronizaciones con el LMS, evitando ruido en los canales de comunicación. El mensaje se publica al broker embebido y el listener lo consume; la lógica del listener no debe disparar las dependencias downstream. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado; broker Kafka embebido; `NotificationDispatcher` y `LmsService` mockeados; listener con partición asignada. |
| **Entradas** | Cadena JSON `{"anonymousId":"user-int-002","status":"ACTIVE","timestamp":1234567890}` publicada al topic. |
| **Acciones** | Se publica el mensaje; tras un `pollDelay` de 3 segundos se verifican los mocks. |
| **Salida Esperada** | `dispatcher.dispatch()` nunca es invocado; `lmsService.syncRemoteAttendance()` nunca es invocado. |
| **Criterios de Aceptación** | Ambas verificaciones `verify(..., never())` pasan después del polling. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-010 |
| **Nombre** | JSON malformado no lanza excepción ni despacha notificación |
| **Descripción** | Verifica la resiliencia del consumidor Kafka ante mensajes corruptos: cuando el listener consume un mensaje del broker embebido cuyo contenido no es JSON válido, lo maneja silenciosamente (try/catch interno) sin propagar la excepción, garantizando que el pod no falla y el consumer group no queda bloqueado. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado; broker Kafka embebido; `NotificationDispatcher` mockeado; listener con partición asignada. |
| **Entradas** | Cadena malformada `"THIS IS NOT JSON {{{}}}"` publicada al topic con `KafkaTemplate<String,String>`. |
| **Acciones** | Se publica el mensaje al topic; tras un `pollDelay` se verifica que el dispatcher no fue invocado y que el consumer no quedó atascado. |
| **Salida Esperada** | El listener no propaga excepción y `dispatcher.dispatch()` nunca es invocado. |
| **Criterios de Aceptación** | El test no falla por excepción y `verify(dispatcher, never()).dispatch(...)` pasa después del polling. |

---

# identity-service

## Identity Service — Anonymous ID Vault

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-011 |
| **Nombre** | POST /identities/map retorna anonymousId UUID válido |
| **Descripción** | Verifica la capa HTTP completa del `IdentityVaultController` mediante `MockMvc`: que una petición `POST` al endpoint de mapeo con un cuerpo JSON válido recibe una respuesta 200 con el campo `anonymousId` en el body. |
| **Prerrequisitos/Condiciones** | Contexto Spring cargado con `@WebMvcTest`; `IdentityVaultService` y `KafkaTemplate` mockeados; usuario autenticado con `@WithMockUser`. |
| **Entradas** | Body JSON `{"realIdentity":"student@university.edu"}`; token CSRF incluido. |
| **Acciones** | Se realiza `mockMvc.perform(post("/api/v1/identities/map"))` con el body. |
| **Salida Esperada** | Estado HTTP 200; el JSON de respuesta contiene el campo `anonymousId`. |
| **Criterios de Aceptación** | `status().isOk()` y `jsonPath("$.anonymousId").exists()` pasan. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-012 |
| **Nombre** | POST /identities/visitor crea mapping compuesto con prefijo VISITOR |
| **Descripción** | Verifica que el endpoint de registro de visitantes construye el identificador compuesto con el prefijo `VISITOR\|` concatenando email, nombre y motivo de visita, antes de delegar al servicio de bóveda. Esta construcción garantiza que los visitantes externos no colisionen con el espacio de identidades de estudiantes y docentes. |
| **Prerrequisitos/Condiciones** | Mismo contexto que PI-011; `vaultService` configurado para retornar un UUID al recibir cualquier cadena. |
| **Entradas** | Body JSON con `name`, `email` y `reason_for_visit`; token CSRF. |
| **Acciones** | Se realiza `mockMvc.perform(post("/api/v1/identities/visitor"))`. |
| **Salida Esperada** | Estado HTTP 200; `vaultService.getOrCreateAnonymousId()` es invocado con un argumento que comienza con `"VISITOR\|"` y contiene el email del visitante. |
| **Criterios de Aceptación** | `status().isOk()` pasa; `verify(vaultService).getOrCreateAnonymousId(argThat(id -> id.startsWith("VISITOR\|") && id.contains("visitor@external.com")))` pasa. |

---

# auth-service

## Auth Service — LDAP Authentication & Identity Integration

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-020 |
| **Nombre** | Llamada HTTP real al contenedor MockServer resuelve UUID anónimo |
| **Descripción** | Verifica con un contenedor Docker real (Testcontainers `MockServerContainer`) que `IdentityClient.getAnonymousId()` ejecuta un POST HTTP real a `/api/v1/identities/map`, deserializa el cuerpo JSON `{"anonymousId":"<uuid>"}` y retorna `Optional.of(uuid)`. Cubre el enlace inter-servicio auth→identity por REST que las pruebas unitarias mockeaban con stubs de RestTemplate. |
| **Prerrequisitos/Condiciones** | Docker disponible en el host; imagen `mockserver/mockserver:5.15.0` descargable; expectativa MockServer configurada para responder 200 al path esperado. |
| **Entradas** | Cadena `"student@university.edu"`; el contenedor responde con `{"anonymousId":"<UUID generado>"}`. |
| **Acciones** | Se construye `IdentityClient(new RestTemplate(), "http://<host>:<port>")` apuntando al contenedor; se invoca `getAnonymousId(realIdentity)`. |
| **Salida Esperada** | `Optional<UUID>` con valor presente igual al UUID que el contenedor devolvió. |
| **Criterios de Aceptación** | `assertTrue(result.isPresent())` y `assertEquals(expected, result.get())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-021 |
| **Nombre** | Respuesta 500 del contenedor engancha el fallback del Circuit Breaker |
| **Descripción** | Verifica end-to-end que cuando identity-service responde 500 (simulado por el contenedor MockServer), el Circuit Breaker de Resilience4j introducido en HU-06 atrapa la `RestClientException` y la fallback `fallbackGetAnonymousId` devuelve `Optional.empty()`. Confirma que el patrón Circuit Breaker funciona contra HTTP real, no solo contra stubs. |
| **Prerrequisitos/Condiciones** | Docker disponible; `mockServerClient.reset()` invocado para limpiar expectativas previas; nueva expectativa que responde 500 a `POST /api/v1/identities/map`. |
| **Entradas** | Cadena `"student@university.edu"`; contenedor configurado para responder HTTP 500 con body `"upstream failure"`. |
| **Acciones** | Se invoca `identityClient.getAnonymousId(realIdentity)`; el RestTemplate lanza `RestClientException`, R4j la atrapa y ejecuta la fallback. |
| **Salida Esperada** | `Optional<UUID>` vacío; **no se propaga ninguna excepción** al caller. |
| **Criterios de Aceptación** | `assertTrue(result.isEmpty())`. |

---

# file-service

## File Service — Upload & Storage

*Pruebas de integración para file-service se requieren para cubrir:*
- Multipart file upload handling con validación de tipo MIME.
- Almacenamiento persistente en S3/disco y verificación de metadata.
- Integración con identity-service para registro de uploader anónimo.

*Actualmente se cubre en tests unitarios; las pruebas de integración I2I se pueden extender mediante Testcontainers (S3Mock/LocalStack) en futuras iteraciones de HU-09.*

---

# gateway-service

## Gateway Service — QR Validation & Campus Entry

*Pruebas de integración para gateway-service se requieren para cubrir:*
- Validación de tokens JWT contra auth-service en un contexto Spring real.
- Sincronización de estado de salud desde promotion-service vía cache Redis (jedis-mock).
- Rate-limiting y throttling en caso de múltiples intentos fallidos.

*Actualmente se cubre en tests unitarios; las pruebas de integración I2I se pueden extender en futuras iteraciones de HU-09.*

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-020 |
| **Nombre** | Llamada HTTP real al contenedor MockServer resuelve UUID anónimo |
| **Descripción** | Verifica con un contenedor Docker real (Testcontainers `MockServerContainer`) que `IdentityClient.getAnonymousId()` ejecuta un POST HTTP real a `/api/v1/identities/map`, deserializa el cuerpo JSON `{"anonymousId":"<uuid>"}` y retorna `Optional.of(uuid)`. Cubre el enlace inter-servicio auth→identity por REST que las pruebas unitarias mockeaban con stubs de RestTemplate. |
| **Prerrequisitos/Condiciones** | Docker disponible en el host; imagen `mockserver/mockserver:5.15.0` descargable; expectativa MockServer configurada para responder 200 al path esperado. |
| **Entradas** | Cadena `"student@university.edu"`; el contenedor responde con `{"anonymousId":"<UUID generado>"}`. |
| **Acciones** | Se construye `IdentityClient(new RestTemplate(), "http://<host>:<port>")` apuntando al contenedor; se invoca `getAnonymousId(realIdentity)`. |
| **Salida Esperada** | `Optional<UUID>` con valor presente igual al UUID que el contenedor devolvió. |
| **Criterios de Aceptación** | `assertTrue(result.isPresent())` y `assertEquals(expected, result.get())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PI-021 |
| **Nombre** | Respuesta 500 del contenedor engancha el fallback del Circuit Breaker |
| **Descripción** | Verifica end-to-end que cuando identity-service responde 500 (simulado por el contenedor MockServer), el Circuit Breaker de Resilience4j introducido en HU-06 atrapa la `RestClientException` y la fallback `fallbackGetAnonymousId` devuelve `Optional.empty()`. Confirma que el patrón Circuit Breaker funciona contra HTTP real, no solo contra stubs. |
| **Prerrequisitos/Condiciones** | Docker disponible; `mockServerClient.reset()` invocado para limpiar expectativas previas; nueva expectativa que responde 500 a `POST /api/v1/identities/map`. |
| **Entradas** | Cadena `"student@university.edu"`; contenedor configurado para responder HTTP 500 con body `"upstream failure"`. |
| **Acciones** | Se invoca `identityClient.getAnonymousId(realIdentity)`; el RestTemplate lanza `RestClientException`, R4j la atrapa y ejecuta la fallback. |
| **Salida Esperada** | `Optional<UUID>` vacío; **no se propaga ninguna excepción** al caller. |
| **Criterios de Aceptación** | `assertTrue(result.isEmpty())`. |

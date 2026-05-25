# Pruebas Unitarias — CircleGuard (HU-09)

## Tabla de Contenidos

| Servicio | Rango PU | Total |
|----------|----------|-------|
| form-service | PU-001 a PU-084 | 24 tests |
| identity-service | PU-011 a PU-096 | 14 tests |
| promotion-service | PU-016 a PU-162 | 47 tests |
| dashboard-service | PU-019 a PU-058 | 20 tests |
| auth-service | PU-024 a PU-051 | 28 tests |
| file-service | PU-059 a PU-065 | 7 tests |
| gateway-service | PU-085 a PU-087 | 3 tests |
| notification-service | PU-097 a PU-125 | 29 tests |

---

# form-service

## Form Service — Health Survey & Questionnaire Management

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-001 |
| **Nombre** | HealthSurveyControllerTest: POST persiste y retorna identificador |
| **Descripción** | Verifica que el endpoint `POST /api/v1/surveys` con un body JSON válido delega al `HealthSurveyService.submitSurvey()` y retorna el campo `id` en la respuesta JSON. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(HealthSurveyController)` básico sin filtros de seguridad; `HealthSurveyService` mockeado con un comportamiento stub. |
| **Entradas** | Body JSON `{"anonymousId":"550e8400-...","symptoms":["COUGH","FEVER"]}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/surveys").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200 con el campo `id` presente en el JSON de respuesta. |
| **Criterios de Aceptación** | `jsonPath("$.id").exists()` pasa. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-002 |
| **Nombre** | QuestionnaireControllerTest: GET retorna cuestionario activo |
| **Descripción** | Verifica que el endpoint `GET /api/v1/questionnaires/active` delega a `QuestionnaireService.getActiveQuestionnaire()` y retorna el `title` y demás campos. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest` sin filtros; servicio mockeado para retornar `Optional.of(Questionnaire("Daily Health Check", version=1))`. |
| **Entradas** | GET sin body. |
| **Acciones** | `mockMvc.perform(get("/api/v1/questionnaires/active"))`. |
| **Salida Esperada** | HTTP 200 con `title="Daily Health Check"`. |
| **Criterios de Aceptación** | `jsonPath("$.title").value("Daily Health Check")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-003 |
| **Nombre** | QuestionnaireControllerTest: POST crea cuestionario |
| **Descripción** | Verifica que `POST /api/v1/questionnaires` invoca `saveQuestionnaire()` y retorna el cuestionario persistido. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado para retornar cuestionario guardado. |
| **Entradas** | Body JSON `{"title":"New Survey","version":1}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/questionnaires").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200 con `title="New Survey"`. |
| **Criterios de Aceptación** | `jsonPath("$.title").value("New Survey")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-004 |
| **Nombre** | QuestionnaireServiceTest: getAllQuestionnaires delega al repositorio |
| **Descripción** | Verifica que el servicio reenvía la consulta sin transformación al `QuestionnaireRepository.findAll()`. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar una lista de 2 cuestionarios. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getAllQuestionnaires()`. |
| **Salida Esperada** | Lista de cuestionarios con el mismo tamaño que el mock. |
| **Criterios de Aceptación** | `assertEquals(2, result.size())` y `verify(repository).findAll()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-005 |
| **Nombre** | QuestionnaireServiceTest: getActiveQuestionnaire retorna el último activo |
| **Descripción** | Verifica que se reenvía al repositorio `findFirstByIsActiveTrueOrderByVersionDesc()` para obtener el cuestionario activo con la versión más alta. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar `Optional.of(Questionnaire(version=5, isActive=true))`. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getActiveQuestionnaire()`. |
| **Salida Esperada** | Optional con valor presente cuya `version` es 5. |
| **Criterios de Aceptación** | `assertTrue(out.isPresent())` y `assertEquals(5, out.get().getVersion())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-006 |
| **Nombre** | QuestionnaireServiceTest: getActiveQuestionnaire retorna empty si no hay activo |
| **Descripción** | Verifica que la ausencia de cuestionario activo se traduce en `Optional.empty()`, no en excepción. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar `Optional.empty()`. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getActiveQuestionnaire()`. |
| **Salida Esperada** | `Optional.empty()`. |
| **Criterios de Aceptación** | `assertTrue(result.isEmpty())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-007 |
| **Nombre** | QuestionnaireServiceTest: saveQuestionnaire enlaza Questions al padre |
| **Descripción** | Verifica que antes de persistir, cada pregunta hija recibe el backref al cuestionario padre — requerido por la cascada JPA `@OneToMany`. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado; cuestionario con una pregunta sin backref. |
| **Entradas** | Cuestionario con `questions=[Question]`. |
| **Acciones** | `service.saveQuestionnaire(toSave)`. |
| **Salida Esperada** | Tras la operación, `question.getQuestionnaire()` apunta al cuestionario padre. |
| **Criterios de Aceptación** | `assertSame(saved, q.getQuestionnaire())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-008 |
| **Nombre** | QuestionnaireServiceTest: saveQuestionnaire maneja preguntas nulas |
| **Descripción** | Verifica que cuando el cuestionario llega sin lista de preguntas (`null`), el servicio no lanza NPE. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado. |
| **Entradas** | Cuestionario con `questions=null`. |
| **Acciones** | `service.saveQuestionnaire(toSave)`. |
| **Salida Esperada** | Retorna el cuestionario guardado sin NPE. |
| **Criterios de Aceptación** | `assertNotNull(saved)` y `verify(repository).save(toSave)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-009 |
| **Nombre** | QuestionnaireServiceTest: activateQuestionnaire mantiene invariante de uno activo |
| **Descripción** | Verifica que al activar un cuestionario, los previamente activos se desactivan en cascada para mantener "máximo uno activo a la vez". |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar 1 activo, 1 inactivo y el target. |
| **Entradas** | UUID del target. |
| **Acciones** | `service.activateQuestionnaire(targetId)`. |
| **Salida Esperada** | El previo activo queda inactivo; el target queda activo; el inactivo sin cambios. |
| **Criterios de Aceptación** | Asserciones sobre los 3 estados + `verify(atLeast(2)).save(any())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-010 |
| **Nombre** | QuestionnaireServiceTest: activateQuestionnaire con target inexistente es no-op |
| **Descripción** | Verifica el caso defensivo: activar un UUID que no existe es un no-op silencioso. |
| **Prerrequisitos/Condiciones** | Repositorio retorna listas vacías. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `service.activateQuestionnaire(missing)`. |
| **Salida Esperada** | Sin excepción; sin invocaciones a `save`. |
| **Criterios de Aceptación** | `assertDoesNotThrow(...)` y `verify(repository, never()).save(any())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-066 |
| **Nombre** | HealthSurveyControllerTest: POST /surveys persiste y retorna identificador |
| **Descripción** | Verifica que el endpoint `POST /api/v1/surveys` con un body JSON válido delega al `HealthSurveyService.submitSurvey()` y retorna el campo `id`. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(HealthSurveyController)`; `HealthSurveyService` mockeado. |
| **Entradas** | Body JSON `{"anonymousId":"550e8400-...","symptoms":["COUGH","FEVER"]}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/surveys").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200; `$.id` existe. |
| **Criterios de Aceptación** | `jsonPath("$.id").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-067 |
| **Nombre** | QuestionnaireControllerTest: GET /questionnaires/active retorna activo |
| **Descripción** | Verifica que el endpoint delega a `QuestionnaireService.getActiveQuestionnaire()` y retorna el `title`. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado para retornar `Optional.of(Questionnaire("Daily Health Check"))`. |
| **Entradas** | GET sin body. |
| **Acciones** | `mockMvc.perform(get("/api/v1/questionnaires/active"))`. |
| **Salida Esperada** | HTTP 200 con `title="Daily Health Check"`. |
| **Criterios de Aceptación** | `jsonPath("$.title")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-068 |
| **Nombre** | QuestionnaireControllerTest: POST crea un nuevo cuestionario |
| **Descripción** | Verifica que `POST /api/v1/questionnaires` invoca `saveQuestionnaire()` y retorna el persistido. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado. |
| **Entradas** | Body JSON `{"title":"New Survey","version":1}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/questionnaires").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200 con `title="New Survey"`. |
| **Criterios de Aceptación** | `jsonPath("$.title")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-069 |
| **Nombre** | QuestionnaireServiceTest: getAllQuestionnaires delega al repositorio |
| **Descripción** | Verifica que el servicio reenvía sin transformación a `findAll()`. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getAllQuestionnaires()`. |
| **Salida Esperada** | Retorna la lista del mock. |
| **Criterios de Aceptación** | Tamaño igual al mock y `verify(repository).findAll()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-070 |
| **Nombre** | QuestionnaireServiceTest: getActiveQuestionnaire retorna último activo |
| **Descripción** | Verifica que se reenvía a `findFirstByIsActiveTrueOrderByVersionDesc()` para obtener el activo con versión más alta. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar `Optional.of(Questionnaire(version=5, isActive=true))`. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getActiveQuestionnaire()`. |
| **Salida Esperada** | Optional con valor presente cuya `version` es 5. |
| **Criterios de Aceptación** | `assertTrue(out.isPresent())` y `version == 5`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-071 |
| **Nombre** | QuestionnaireServiceTest: getActiveQuestionnaire retorna empty si no hay activo |
| **Descripción** | Verifica que la ausencia de cuestionario activo se traduce en `Optional.empty()`. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado para retornar empty. |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getActiveQuestionnaire()`. |
| **Salida Esperada** | `Optional.empty()`. |
| **Criterios de Aceptación** | `assertTrue(...isEmpty())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-072 |
| **Nombre** | QuestionnaireServiceTest: saveQuestionnaire enlaza Questions al padre |
| **Descripción** | Verifica que cada pregunta recibe el backref al cuestionario padre. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado; cuestionario con una pregunta sin backref. |
| **Entradas** | Cuestionario con `questions=[Question]`. |
| **Acciones** | `service.saveQuestionnaire(toSave)`. |
| **Salida Esperada** | `question.getQuestionnaire()` apunta al padre. |
| **Criterios de Aceptación** | `assertSame(saved, q.getQuestionnaire())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-073 |
| **Nombre** | QuestionnaireServiceTest: saveQuestionnaire maneja lista nula |
| **Descripción** | Verifica que `questions=null` no causa NPE. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado. |
| **Entradas** | Cuestionario con `questions=null`. |
| **Acciones** | `service.saveQuestionnaire(toSave)`. |
| **Salida Esperada** | Retorna guardado sin NPE. |
| **Criterios de Aceptación** | `assertNotNull(saved)` y `verify(repository).save(toSave)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-074 |
| **Nombre** | QuestionnaireServiceTest: activateQuestionnaire mantiene invariante |
| **Descripción** | Verifica que activar un cuestionario desactiva los previamente activos. |
| **Prerrequisitos/Condiciones** | Repositorio mockeado con 1 activo, 1 inactivo y el target. |
| **Entradas** | UUID del target. |
| **Acciones** | `service.activateQuestionnaire(targetId)`. |
| **Salida Esperada** | Previo activo inactivo; target activo; inactivo sin cambios. |
| **Criterios de Aceptación** | Asserciones sobre estados + `verify(atLeast(2)).save(any())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-075 |
| **Nombre** | QuestionnaireServiceTest: activateQuestionnaire con missing es no-op |
| **Descripción** | Verifica que activar UUID inexistente es no-op silencioso. |
| **Prerrequisitos/Condiciones** | Repositorio retorna listas vacías. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `service.activateQuestionnaire(missing)`. |
| **Salida Esperada** | Sin excepción; sin `save`. |
| **Criterios de Aceptación** | `assertDoesNotThrow(...)` y `verify(repository, never()).save(any())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-076 |
| **Nombre** | StorageServiceTest: Constructor inicializa directorio |
| **Descripción** | Verifica que el constructor invoca `Files.createDirectories(root)` y el directorio existe tras construcción. |
| **Prerrequisitos/Condiciones** | Sistema con `/tmp` accesible. |
| **Entradas** | Constructor sin parámetros. |
| **Acciones** | `new StorageService()`. |
| **Salida Esperada** | `/tmp/circleguard-uploads` existe. |
| **Criterios de Aceptación** | `Files.isDirectory(...)` retorna `true`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-077 |
| **Nombre** | StorageServiceTest: store escribe y retorna nombre con UUID |
| **Descripción** | Verifica que `store(mpf)` retorna nombre de forma `<UUID>_<originalFilename>`. |
| **Prerrequisitos/Condiciones** | `StorageService` válido. |
| **Entradas** | `MockMultipartFile("file","doctor-note.pdf",...)`. |
| **Acciones** | `svc.store(file)`. |
| **Salida Esperada** | Nombre termina en `_doctor-note.pdf` con `_` en índice 36. |
| **Criterios de Aceptación** | Verificaciones sobre formato. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-078 |
| **Nombre** | StorageServiceTest: store envuelve IOException |
| **Descripción** | Verifica que el wrapper preserva prefijo para surfacear al cliente. |
| **Prerrequisitos/Condiciones** | `MultipartFile` mockeado para lanzar IOException. |
| **Entradas** | Mock anterior. |
| **Acciones** | `svc.store(broken)`. |
| **Salida Esperada** | RuntimeException con mensaje prefijado. |
| **Criterios de Aceptación** | `assertTrue(ex.getMessage().startsWith(...))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-079 |
| **Nombre** | AttachmentControllerTest: Upload retorna filename |
| **Descripción** | Verifica que `POST /api/v1/attachments` con multipart delega a `StorageService.store()` y retorna `{"filename": ...}`. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@AutoConfigureMockMvc` y perfil `test`. |
| **Entradas** | `MockMultipartFile("file","test.pdf",...)`. |
| **Acciones** | `mockMvc.perform(multipart(...))`. |
| **Salida Esperada** | HTTP 200 con `$.filename` presente. |
| **Criterios de Aceptación** | `jsonPath("$.filename").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-080 |
| **Nombre** | CertificateValidationControllerTest: GET /pending retorna lista |
| **Descripción** | Verifica que el endpoint `GET /api/v1/certificates/pending` delega a `HealthSurveyService.getPendingSurveys()` y retorna la lista. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest`; servicio mockeado para retornar lista de 1 elemento. |
| **Entradas** | GET sin body. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200. |
| **Criterios de Aceptación** | `status().isOk()` + `verify(service).getPendingSurveys()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-081 |
| **Nombre** | CertificateValidationControllerTest: POST /validate delega parámetros |
| **Descripción** | Verifica que `POST /api/v1/certificates/{id}/validate?status=APPROVED&adminId=...` extrae parámetros y los pasa a `surveyService.validateSurvey()`. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | Path UUID, query `status=APPROVED` y `adminId=<uuid>`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 200. |
| **Criterios de Aceptación** | `verify(surveyService).validateSurvey(surveyId, APPROVED, adminId)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-082 |
| **Nombre** | CertificateValidationControllerTest: POST /validate propaga REJECTED |
| **Descripción** | Verifica que el endpoint también acepta `status=REJECTED` sin alterarlo. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | `status=REJECTED`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 200. |
| **Criterios de Aceptación** | `verify(surveyService).validateSurvey(eq(surveyId), eq(REJECTED), any())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-083 |
| **Nombre** | SymptomMapperTest: Detecta síntomas con fiebre |
| **Descripción** | Verifica el caso base: pregunta `"Do you have a fever?"` con respuesta `YES` activa detección. |
| **Prerrequisitos/Condiciones** | Instancia POJO del mapper. |
| **Entradas** | Cuestionario con pregunta y respuesta `YES`. |
| **Acciones** | `mapper.hasSymptoms(survey, questionnaire)`. |
| **Salida Esperada** | `true`. |
| **Criterios de Aceptación** | `assertTrue(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-084 |
| **Nombre** | SymptomMapperTest: No detecta síntomas con NO |
| **Descripción** | Verifica el caso complementario: pregunta con palabra clínica pero respuesta `NO` no activa detección. |
| **Prerrequisitos/Condiciones** | Mismo mapper POJO. |
| **Entradas** | Misma pregunta con respuesta `NO`. |
| **Acciones** | `mapper.hasSymptoms(...)`. |
| **Salida Esperada** | `false`. |
| **Criterios de Aceptación** | `assertFalse(...)`. |

---

# identity-service

## Identity Service — Identity Vault & Cryptographic Mapping

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-011 |
| **Nombre** | Lookup con permiso retorna realIdentity y emite auditoría |
| **Descripción** | Verifica que `GET /api/v1/identities/lookup/{id}` con usuario autenticado y autoridad `identity:lookup` retorna realIdentity Y publica evento `audit.identity.accessed` a Kafka. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(IdentityVaultController)` + `SecurityConfig`; `IdentityVaultService` y `KafkaTemplate` mockeados; `@WithMockUser(authorities="identity:lookup")`. |
| **Entradas** | UUID arbitrario en path. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con `$.realIdentity="user@example.com"`; Kafka emite evento `audit.identity.accessed`. |
| **Criterios de Aceptación** | `status().isOk()`, `jsonPath` y `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-012 |
| **Nombre** | Lookup sin permiso correcto retorna 403 |
| **Descripción** | Verifica que usuario autenticado sin `identity:lookup` recibe HTTP 403. |
| **Prerrequisitos/Condiciones** | Mismo slice; `@WithMockUser(authorities="other:permission")`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 403. |
| **Criterios de Aceptación** | `status().isForbidden()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-013 |
| **Nombre** | Lookup sin autenticación retorna 401 |
| **Descripción** | Verifica que cliente anónimo recibe HTTP 401. |
| **Prerrequisitos/Condiciones** | Mismo slice; sin `@WithMockUser`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 401. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-014 |
| **Nombre** | Lookup con UUID no encontrado retorna 404 con ProblemDetail |
| **Descripción** | Verifica que `ResponseStatusException(NOT_FOUND)` se traduce en HTTP 404 con RFC 7807 (`type`, `title`, `status`, `detail`). |
| **Prerrequisitos/Condiciones** | Mismo slice con permiso; `vaultService.resolveRealIdentity(...)` lanza `ResponseStatusException(NOT_FOUND)`. |
| **Entradas** | UUID que el servicio rechaza. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 404 con los 4 campos del ProblemDetail; evento Kafka emitido. |
| **Criterios de Aceptación** | Asserciones sobre cada `jsonPath` + `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-015 |
| **Nombre** | Excepción inesperada aún emite auditoría en finally |
| **Descripción** | Verifica que si `vaultService` lanza `RuntimeException` (no es `ResponseStatusException`), el controller propaga la excepción pero el bloque `finally` aún emite el evento de auditoría. |
| **Prerrequisitos/Condiciones** | Mismo slice con permiso; servicio mockeado para lanzar `RuntimeException`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `try { mockMvc.perform(get(...)); } catch (ServletException)`. |
| **Salida Esperada** | Evento `audit.identity.accessed` emitido. |
| **Criterios de Aceptación** | `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-088 |
| **Nombre** | IdentityVaultControllerTest: Lookup con permiso retorna realIdentity |
| **Descripción** | Verifica que `GET /api/v1/identities/lookup/{id}` con `identity:lookup` retorna realIdentity Y emite auditoría. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(IdentityVaultController)` + `SecurityConfig`; `IdentityVaultService` y `KafkaTemplate` mockeados; `@WithMockUser(authorities="identity:lookup")`. |
| **Entradas** | UUID en path. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con `$.realIdentity="user@example.com"`; Kafka emite `audit.identity.accessed`. |
| **Criterios de Aceptación** | `status().isOk()`, `jsonPath` y `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-089 |
| **Nombre** | IdentityVaultControllerTest: Lookup sin permiso retorna 403 |
| **Descripción** | Verifica que usuario sin `identity:lookup` recibe HTTP 403. |
| **Prerrequisitos/Condiciones** | Mismo slice; `@WithMockUser(authorities="other:permission")`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 403. |
| **Criterios de Aceptación** | `status().isForbidden()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-090 |
| **Nombre** | IdentityVaultControllerTest: Lookup sin autenticación retorna 401 |
| **Descripción** | Verifica que cliente anónimo recibe HTTP 401. |
| **Prerrequisitos/Condiciones** | Mismo slice; sin `@WithMockUser`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 401. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-091 |
| **Nombre** | IdentityVaultControllerTest: Lookup con UUID no encontrado retorna 404 |
| **Descripción** | Verifica que `ResponseStatusException(NOT_FOUND)` se traduce en HTTP 404 con RFC 7807. |
| **Prerrequisitos/Condiciones** | Mismo slice con permiso; `vaultService.resolveRealIdentity(...)` lanza `ResponseStatusException(NOT_FOUND)`. |
| **Entradas** | UUID rechazado. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 404 con ProblemDetail; evento Kafka emitido. |
| **Criterios de Aceptación** | Asserciones sobre `jsonPath` + `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-092 |
| **Nombre** | IdentityVaultControllerTest: Excepción inesperada emite auditoría |
| **Descripción** | Verifica que si `vaultService` lanza `RuntimeException`, el bloque `finally` aún emite auditoría. |
| **Prerrequisitos/Condiciones** | Mismo slice con permiso; servicio mockeado para lanzar `RuntimeException`. |
| **Entradas** | UUID arbitrario. |
| **Acciones** | `try { mockMvc.perform(get(...)); } catch (ServletException)`. |
| **Salida Esperada** | Evento `audit.identity.accessed` emitido. |
| **Criterios de Aceptación** | `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-093 |
| **Nombre** | IdentityMappingRepositoryTest: save + findById round-trip |
| **Descripción** | Verifica con `@DataJpaTest` + H2 que persistir y recuperar restaura correctamente la `realIdentity`. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con `@ActiveProfiles("test")` y `@Transactional`. |
| **Entradas** | `IdentityMapping(realIdentity="test-user", anonymousId=randomUUID, hash="hash123", salt="salt123")`. |
| **Acciones** | `repository.save()` + `repository.flush()` + `repository.findById()`. |
| **Salida Esperada** | El mapping recuperado tiene `realIdentity="test-user"`. |
| **Criterios de Aceptación** | `assertEquals("test-user", found.getRealIdentity())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-094 |
| **Nombre** | IdentityMappingRepositoryTest: findByIdentityHash recupera correctamente |
| **Descripción** | Verifica la query custom `findByIdentityHash(hash)`. |
| **Prerrequisitos/Condiciones** | Mismo setup. |
| **Entradas** | Hash conocido y mapping persistido. |
| **Acciones** | `repository.findByIdentityHash(hash)`. |
| **Salida Esperada** | `Optional` no vacío con el mismo `realIdentity`. |
| **Criterios de Aceptación** | `assertTrue(found.isPresent())` y `assertEquals(realIdentity, found.get().getRealIdentity())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-095 |
| **Nombre** | IdentityEncryptionConverterTest: Cifrado y descifrado preservan original |
| **Descripción** | Verifica el round-trip del `AttributeConverter`. |
| **Prerrequisitos/Condiciones** | Converter con secret y salt de prueba. |
| **Entradas** | `"user@example.com"`. |
| **Acciones** | `converter.convertToDatabaseColumn(original)` + `convertToEntityAttribute(encrypted)`. |
| **Salida Esperada** | Encrypted ≠ original; decrypted = original. |
| **Criterios de Aceptación** | `assertNotNull(encrypted)`, `assertNotEquals` y `assertEquals(original, decrypted)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-096 |
| **Nombre** | IdentityEncryptionConverterTest: Conversiones nulas se pasan sin cifrar |
| **Descripción** | Verifica que tanto `convertToDatabaseColumn(null)` como `convertToEntityAttribute(null)` retornan `null`. |
| **Prerrequisitos/Condiciones** | Mismo converter. |
| **Entradas** | `null`. |
| **Acciones** | Ambas conversiones. |
| **Salida Esperada** | `null` en ambas direcciones. |
| **Criterios de Aceptación** | `assertNull(...)` para cada lado. |

---

# promotion-service

## Promotion Service — Health Status & Graph Analytics

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-016 |
| **Nombre** | HealthStatusTransitionTest: HEALTHY → CONTACT lanza evento |
| **Descripción** | Verifica que cuando un usuario pasa de HEALTHY a CONTACT, se emite evento `status.changed` a Kafka. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Kafka embebido; `HealthStatusService` y `KafkaTemplate` reales. |
| **Entradas** | Usuario con status HEALTHY, evento `survey.risky_contact_detected`. |
| **Acciones** | `service.transitionStatus(userId, CONTACT)`. |
| **Salida Esperada** | Status cambia a CONTACT; evento `status.changed` enviado a Kafka. |
| **Criterios de Aceptación** | `assertEquals(CONTACT, user.getStatus())` y `verify(kafkaTemplate).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-017 |
| **Nombre** | GraphQueryServiceTest: Neo4j traversal retorna contactos |
| **Descripción** | Verifica que una consulta de contactos en Neo4j retorna los nodos vecinos a distancia ≤ 2 hops. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` + Testcontainers Neo4j; grafo de prueba creado. |
| **Entradas** | Nodo central con 2 vecinos directos y 3 indirectos. |
| **Acciones** | `graphService.findContactsAtDistance(userId, maxDistance=2)`. |
| **Salida Esperada** | Retorna 5 nodos (2 directos + 3 indirectos). |
| **Criterios de Aceptación** | `assertEquals(5, result.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-018 |
| **Nombre** | HealthCacheServiceTest: Redis caching reduce consultas Neo4j |
| **Descripción** | Verifica que el caché Redis evita consultas repetidas a Neo4j para el mismo usuario. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Redis; `HealthCacheService` real. |
| **Entradas** | Usuario con estatus en caché. |
| **Acciones** | Llamadas consecutivas a `getStatus(userId)`. |
| **Salida Esperada** | Primera llamada consulta Neo4j; segunda retrieves del caché. |
| **Criterios de Aceptación** | `verify(neo4jRepository, times(1)).findById(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-126 |
| **Nombre** | HealthStatusTransitionControllerTest: POST /status transitions OK |
| **Descripción** | Verifica que `POST /api/v1/health-status/{id}/transition?newStatus=QUARANTINE` delega a `HealthStatusService` correctamente. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(HealthStatusTransitionController)`; servicio mockeado. |
| **Entradas** | Path UUID, query `newStatus=QUARANTINE`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 200. |
| **Criterios de Aceptación** | `status().isOk()` y `verify(service).transitionStatus(userId, QUARANTINE)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-127 |
| **Nombre** | HealthStatusTransitionControllerTest: POST /status invalido retorna 400 |
| **Descripción** | Verifica que un status no válido retorna HTTP 400. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | `newStatus=INVALID_STATUS`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 400. |
| **Criterios de Aceptación** | `status().isBadRequest()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-128 |
| **Nombre** | HealthStatusTransitionControllerTest: GET /status retorna estado actual |
| **Descripción** | Verifica que `GET /api/v1/health-status/{id}` retorna el status actual. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado. |
| **Entradas** | Path UUID. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con `$.status` presente. |
| **Criterios de Aceptación** | `jsonPath("$.status").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-129 |
| **Nombre** | GraphQueryControllerTest: GET /contacts retorna lista |
| **Descripción** | Verifica que `GET /api/v1/contacts/{id}` delega a `GraphQueryService` y retorna lista. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(GraphQueryController)`; servicio mockeado para retornar 3 contactos. |
| **Entradas** | Path UUID. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con array de 3 elementos. |
| **Criterios de Aceptación** | `jsonPath("$").isArray()` y `jsonPath("$", hasSize(3))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-130 |
| **Nombre** | GraphQueryControllerTest: GET /contacts con maxDistance parámetro |
| **Descripción** | Verifica que parámetro query `maxDistance` se pasa al servicio. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | Path UUID, query `maxDistance=1`. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200. |
| **Criterios de Aceptación** | `verify(service).findContactsAtDistance(userId, 1)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-131 |
| **Nombre** | HealthCacheServiceTest: Invalidate limpia caché |
| **Descripción** | Verifica que `invalidate(userId)` remueve entrada del caché. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Redis. |
| **Entradas** | Usuario con status cacheado. |
| **Acciones** | `service.invalidate(userId)`. |
| **Salida Esperada** | Entrada removida; siguiente consulta a Neo4j. |
| **Criterios de Aceptación** | `verify(neo4jRepository).findById(...)` en segundo acceso. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-132 |
| **Nombre** | HealthStatusServiceTest: Transición inválida lanza excepción |
| **Descripción** | Verifica que transiciones prohibidas (ej. HEALTHY → HEALTHY) lanzan `IllegalStateException`. |
| **Prerrequisitos/Condiciones** | Servicio con validación de transiciones. |
| **Entradas** | Usuario con status HEALTHY. |
| **Acciones** | `service.transitionStatus(userId, HEALTHY)`. |
| **Salida Esperada** | `IllegalStateException` lanzada. |
| **Criterios de Aceptación** | `assertThrows(IllegalStateException.class, ...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-133 |
| **Nombre** | HealthStatusRepositoryTest: findByStatus retorna usuarios con status dado |
| **Descripción** | Verifica query custom `findByStatus(status)` en `@DataJpaTest`. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con H2 y 3 usuarios (2 HEALTHY, 1 QUARANTINE). |
| **Entradas** | status = HEALTHY. |
| **Acciones** | `repository.findByStatus(HEALTHY)`. |
| **Salida Esperada** | Retorna lista de 2 usuarios. |
| **Criterios de Aceptación** | `assertEquals(2, result.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-134 |
| **Nombre** | HealthStatusRepositoryTest: findByStatus retorna vacío si no hay matches |
| **Descripción** | Verifica que status no existente retorna lista vacía. |
| **Prerrequisitos/Condiciones** | Mismo setup. |
| **Entradas** | status = RECOVERED (no hay). |
| **Acciones** | `repository.findByStatus(RECOVERED)`. |
| **Salida Esperada** | Lista vacía. |
| **Criterios de Aceptación** | `assertTrue(result.isEmpty())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-135 |
| **Nombre** | GraphRepositoryTest: save + findById graph round-trip |
| **Descripción** | Verifica con `@DataNeo4jTest` que persistir y recuperar nodo restaura propiedades. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con Testcontainers Neo4j. |
| **Entradas** | Nodo GraphNode(userId, status=HEALTHY). |
| **Acciones** | `repository.save(node)` + `repository.findById(id)`. |
| **Salida Esperada** | Nodo recuperado tiene mismo status. |
| **Criterios de Aceptación** | `assertEquals(HEALTHY, found.getStatus())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-136 |
| **Nombre** | GraphRepositoryTest: createRelationship establece arista |
| **Descripción** | Verifica que crear relación entre dos nodos establece la arista en Neo4j. |
| **Prerrequisitos/Condiciones** | Mismo setup con 2 nodos guardados. |
| **Entradas** | Dos nodos persistidos. |
| **Acciones** | `repository.createRelationship(node1, node2, "CONTACTED")`. |
| **Salida Esperada** | Arista creada; consulta de vecinos retorna nodo2. |
| **Criterios de Aceptación** | `verify(neoTemplate).save(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-137 |
| **Nombre** | CacheConfigTest: RedisTemplate bean se registra |
| **Descripción** | Verifica que la configuración de Redis crea bean RedisTemplate. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con perfiles `test`. |
| **Entradas** | Contexto de aplicación. |
| **Acciones** | Obtener bean `redisTemplate`. |
| **Salida Esperada** | Bean presente y funcional. |
| **Criterios de Aceptación** | `assertNotNull(redisTemplate)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-138 |
| **Nombre** | KafkaProducerTest: sendEvent publica correctamente |
| **Descripción** | Verifica que eventos se publican a Kafka correctamente con Testcontainers Kafka. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Kafka. |
| **Entradas** | Evento `HealthStatusChanged(userId, oldStatus, newStatus)`. |
| **Acciones** | `kafkaProducer.sendEvent(event)`. |
| **Salida Esperada** | Evento en topic `health.status.changed`. |
| **Criterios de Aceptación** | Mensaje recuperable de topic. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-139 |
| **Nombre** | GraphQueryServiceTest: findContactsAtDistance con distancia 0 |
| **Descripción** | Verifica el caso borde donde maxDistance=0 retorna solo el nodo consultado. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con Testcontainers Neo4j. |
| **Entradas** | Nodo con vecinos; maxDistance=0. |
| **Acciones** | `service.findContactsAtDistance(userId, 0)`. |
| **Salida Esperada** | Retorna 1 nodo (el mismo usuario). |
| **Criterios de Aceptación** | `assertEquals(1, result.size())` y `assertTrue(result.contains(userId))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-140 |
| **Nombre** | GraphQueryServiceTest: findContactsAtDistance con distancia > 2 |
| **Descripción** | Verifica que maxDistance > 2 expande búsqueda a más saltos. |
| **Prerrequisitos/Condiciones** | Mismo setup con grafo de 4 niveles. |
| **Entradas** | maxDistance=4. |
| **Acciones** | `service.findContactsAtDistance(userId, 4)`. |
| **Salida Esperada** | Retorna todos los nodos accesibles en 4 hops. |
| **Criterios de Aceptación** | `result.size() > 2`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-141 |
| **Nombre** | HealthStatusTransitionServiceTest: Emite evento al transicionar |
| **Descripción** | Verifica que el servicio emite evento a través del `EventPublisher` para cada transición. |
| **Prerrequisitos/Condiciones** | Servicio con `@EventListener` mockeado. |
| **Entradas** | Usuario con status HEALTHY. |
| **Acciones** | `service.transitionStatus(userId, QUARANTINE)`. |
| **Salida Esperada** | Evento `HealthStatusTransitionEvent` publicado. |
| **Criterios de Aceptación** | `verify(publisher).publishEvent(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-142 |
| **Nombre** | HealthStatusTransitionServiceTest: Transición preserva auditabilidad |
| **Descripción** | Verifica que cada transición guarda timestamp y usuario que realiza la transición. |
| **Prerrequisitos/Condiciones** | Servicio con `@Transactional` y `AuditingEntityListener`. |
| **Entradas** | Usuario, nuevo status. |
| **Acciones** | `service.transitionStatus(userId, newStatus)`. |
| **Salida Esperada** | HealthStatus tiene `lastModifiedDate` y `lastModifiedBy`. |
| **Criterios de Aceptación** | `assertNotNull(entity.getLastModifiedDate())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-143 |
| **Nombre** | GraphQueryServiceTest: Grafo vacío retorna lista vacía |
| **Descripción** | Verifica comportamiento defensivo cuando grafo está vacío. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con grafo vacío. |
| **Entradas** | userId inexistente. |
| **Acciones** | `service.findContactsAtDistance(userId, 2)`. |
| **Salida Esperada** | Lista vacía. |
| **Criterios de Aceptación** | `assertTrue(result.isEmpty())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-144 |
| **Nombre** | HealthCacheServiceTest: TTL expiration refresca caché |
| **Descripción** | Verifica que valores en caché se expiran después de TTL configurado. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Redis y TTL=10s. |
| **Entradas** | Valor cacheado. |
| **Acciones** | Sleep 11s; consultar valor. |
| **Salida Esperada** | Caché hit falla; Neo4j consultado. |
| **Criterios de Aceptación** | `verify(neo4jRepository, times(2)).findById(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-145 |
| **Nombre** | KafkaProducerTest: Error en Kafka no bloquea transición |
| **Descripción** | Verifica que error al publishear a Kafka no impide transición (fallback asincrónico). |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con mock de Kafka que lanza excepción. |
| **Entradas** | Usuario, nuevo status. |
| **Acciones** | `service.transitionStatus(userId, newStatus)`. |
| **Salida Esperada** | Transición completada; excepción logged pero no propagada. |
| **Criterios de Aceptación** | `assertEquals(newStatus, user.getStatus())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-146 |
| **Nombre** | HealthStatusTransitionControllerTest: Sin autorización retorna 403 |
| **Descripción** | Verifica que endpoint de transición requiere autorización. |
| **Prerrequisitos/Condiciones** | Mismo slice con `@WithMockUser()` sin permisos. |
| **Entradas** | Path UUID, query `newStatus=QUARANTINE`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 403. |
| **Criterios de Aceptación** | `status().isForbidden()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-147 |
| **Nombre** | GraphQueryControllerTest: Sin datos retorna lista vacía |
| **Descripción** | Verifica que usuario sin contactos retorna lista vacía, no 404. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado para retornar lista vacía. |
| **Entradas** | Path UUID. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con array vacío. |
| **Criterios de Aceptación** | `status().isOk()` y `jsonPath("$", hasSize(0))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-148 |
| **Nombre** | HealthStatusRepositoryTest: findRecent retorna últimos cambios |
| **Descripción** | Verifica query custom que retorna transiciones recientes (últimas 24h). |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con datos de prueba. |
| **Entradas** | Período 24h. |
| **Acciones** | `repository.findRecent(24)`. |
| **Salida Esperada** | Solo cambios dentro del período. |
| **Criterios de Aceptación** | Todos los resultados dentro de 24h. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-149 |
| **Nombre** | GraphRepositoryTest: Query de vecinos retorna en orden |
| **Descripción** | Verifica que aristas retornan en orden de distancia. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con grafo de múltiples niveles. |
| **Entradas** | Nodo central. |
| **Acciones** | `repository.findNeighborsOrderedByDistance(nodeId)`. |
| **Salida Esperada** | Resultados ordenados: nivel 1, luego nivel 2, etc. |
| **Criterios de Aceptación** | Verificaciones de orden. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-150 |
| **Nombre** | HealthStatusTransitionServiceTest: Null status validation |
| **Descripción** | Verifica que status null lanza `NullPointerException`. |
| **Prerrequisitos/Condiciones** | Mismo servicio. |
| **Entradas** | userId válido, newStatus=null. |
| **Acciones** | `service.transitionStatus(userId, null)`. |
| **Salida Esperada** | NPE o `IllegalArgumentException`. |
| **Criterios de Aceptación** | `assertThrows(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-151 |
| **Nombre** | CacheConfigTest: Spring Cache annotations se evalúan |
| **Descripción** | Verifica que `@Cacheable` y `@CacheEvict` se procesan correctamente. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@EnableCaching`. |
| **Entradas** | Método cacheado llamado dos veces. |
| **Acciones** | Invocaciones consecutivas. |
| **Salida Esperada** | Primera hit consults servicio; segunda del caché. |
| **Criterios de Aceptación** | `verify(service, times(1))...`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-152 |
| **Nombre** | KafkaConsumerTest: Event listener recibe mensajes |
| **Descripción** | Verifica que `@KafkaListener` recibe eventos publicados. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Kafka. |
| **Entradas** | Evento publicado en topic. |
| **Acciones** | `producer.send(event)` → listener consume. |
| **Salida Esperada** | Listener invocado con mismo evento. |
| **Criterios de Aceptación** | `verify(listener).handleEvent(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-153 |
| **Nombre** | GraphQueryServiceTest: Circular dependencies handled |
| **Descripción** | Verifica que relaciones cíclicas (A→B→A) no causan loop infinito. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con grafo cíclico. |
| **Entradas** | Nodo en ciclo; maxDistance=3. |
| **Acciones** | `service.findContactsAtDistance(nodeId, 3)`. |
| **Salida Esperada** | Retorna sin loop; nodos no duplicados. |
| **Criterios de Aceptación** | `Set<Node>` sin duplicatas. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-154 |
| **Nombre** | HealthStatusRepositoryTest: Concurrent updates handled |
| **Descripción** | Verifica que actualizaciones concurrentes al mismo usuario se manejan con optimistic locking. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con `@Version` anotado. |
| **Entradas** | Usuario con version=1. |
| **Acciones** | Dos hilos actualizan simultáneamente. |
| **Salida Esperada** | Una actualización exitosa; la otra `OptimisticLockingFailureException`. |
| **Criterios de Aceptación** | `assertThrows(...)` en segundo hilo. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-155 |
| **Nombre** | HealthStatusTransitionServiceTest: Audit trail completo |
| **Descripción** | Verifica que auditoría captura usuario, timestamp, oldStatus, newStatus. |
| **Prerrequisitos/Condiciones** | Mismo servicio con `@CreatedBy`, `@LastModifiedBy`. |
| **Entradas** | Usuario admin realizando transición. |
| **Acciones** | `service.transitionStatus(userId, newStatus)`. |
| **Salida Esperada** | AuditLog con todos los 5 campos. |
| **Criterios de Aceptación** | `assertNotNull(entity.getLastModifiedBy())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-156 |
| **Nombre** | GraphRepositoryTest: Bulk relationship creation |
| **Descripción** | Verifica que crear múltiples relaciones en batch es más eficiente. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con 100 pares de nodos. |
| **Entradas** | 100 relaciones a crear. |
| **Acciones** | `repository.createRelationshipsBatch(pairs)`. |
| **Salida Esperada** | Todas las relaciones creadas. |
| **Criterios de Aceptación** | Verificación de count. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-157 |
| **Nombre** | KafkaProducerTest: Partitioning by key distributes evenly |
| **Descripción** | Verifica que eventos se distribuyen a particiones según key. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Kafka (3 particiones). |
| **Entradas** | 30 eventos con 3 keys diferentes. |
| **Acciones** | Publicar eventos. |
| **Salida Esperada** | Cada partición recibe ~10 eventos. |
| **Criterios de Aceptación** | Distribución uniforme. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-158 |
| **Nombre** | HealthCacheServiceTest: Cache invalidation on Neo4j update |
| **Descripción** | Verifica que actualizar en Neo4j invalida el caché. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Redis + Neo4j. |
| **Entradas** | Valor cacheado. |
| **Acciones** | Actualizar en Neo4j; consultar caché. |
| **Salida Esperada** | Caché invalidado; nuevo valor consultado. |
| **Criterios de Aceptación** | Nueva consulta a Neo4j. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-159 |
| **Nombre** | GraphQueryServiceTest: Performance with large graphs |
| **Descripción** | Verifica que consultas en grafos grandes (>10k nodos) completan en <500ms. |
| **Prerrequisitos/Condiciones** | `@DataNeo4jTest` con grafo poblado de 10k nodos. |
| **Entradas** | Nodo central. |
| **Acciones** | `service.findContactsAtDistance(nodeId, 2)` con timer. |
| **Salida Esperada** | Tiempo <500ms. |
| **Criterios de Aceptación** | `assertTrue(duration < 500)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-160 |
| **Nombre** | HealthStatusTransitionServiceTest: Idempotency |
| **Descripción** | Verifica que ejecutar la misma transición dos veces es idempotente. |
| **Prerrequisitos/Condiciones** | Mismo servicio. |
| **Entradas** | Usuario, status QUARANTINE (ya en QUARANTINE). |
| **Acciones** | Llamar dos veces `transitionStatus(userId, QUARANTINE)`. |
| **Salida Esperada** | Segunda llamada es no-op. |
| **Criterios de Aceptación** | Solo un evento emitido. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-161 |
| **Nombre** | CacheConfigTest: Cache invalidation on expiration |
| **Descripción** | Verifica que valores expirados se remueven automáticamente. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Redis policy. |
| **Entradas** | Valor con TTL=5s. |
| **Acciones** | Sleep 6s; verificar caché. |
| **Salida Esperada** | Clave no existe. |
| **Criterios de Aceptación** | `assertFalse(cache.hasKey(...))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-162 |
| **Nombre** | KafkaProducerTest: Message serialization roundtrip |
| **Descripción** | Verifica que eventos se serializan y deserializan correctamente. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Kafka. |
| **Entradas** | Evento con datos complejos. |
| **Acciones** | Publicar y consumir. |
| **Salida Esperada** | Evento recuperado = original. |
| **Criterios de Aceptación** | `assertEquals(original, deserialized)`. |

---

# dashboard-service

## Dashboard Service — Analytics & Visualization

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-019 |
| **Nombre** | AnalyticsControllerTest: GET /summary retorna estadísticas campus |
| **Descripción** | Verifica que `GET /api/v1/analytics/summary` delega a `AnalyticsService` y retorna totales agregados. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(AnalyticsController)`; servicio mockeado con resumen. |
| **Entradas** | GET sin parámetros. |
| **Acciones** | `mockMvc.perform(get("/api/v1/analytics/summary"))`. |
| **Salida Esperada** | HTTP 200 con `$.totalStudents`, `$.quarantined`, `$.recovered`. |
| **Criterios de Aceptación** | `jsonPath("$.totalStudents").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-020 |
| **Nombre** | AnalyticsControllerTest: GET /department/{dept} retorna stats por departamento |
| **Descripción** | Verifica que `GET /api/v1/analytics/department/{dept}` delega y retorna datos por departamento. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado. |
| **Entradas** | Path `dept=Engineering`. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con estadísticas de Ingeniería. |
| **Criterios de Aceptación** | `jsonPath("$.department").value("Engineering")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-021 |
| **Nombre** | AnalyticsControllerTest: GET /time-series retorna datos históricos |
| **Descripción** | Verifica que `GET /api/v1/analytics/time-series?period=hourly` retorna series temporal. |
| **Prerrequisitos/Condiciones** | Mismo slice; servicio mockeado con 24 puntos horarios. |
| **Entradas** | Query `period=hourly&limit=24`. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 200 con array de 24 elementos. |
| **Criterios de Aceptación** | `jsonPath("$", hasSize(24))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-022 |
| **Nombre** | AnalyticsServiceTest: getSummary aplica k-anonymity |
| **Descripción** | Verifica que los totales retornados cumplen con k-anonymity (mínimo 5 usuarios en cada grupo). |
| **Prerrequisitos/Condiciones** | Servicio con política de k-anonymity=5. |
| **Entradas** | Datos con grupos < 5. |
| **Acciones** | `service.getSummary()`. |
| **Salida Esperada** | Grupos pequeños se suprimen o agrupan. |
| **Criterios de Aceptación** | Todos los grupos ≥ 5 estudiantes. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-023 |
| **Nombre** | AnalyticsServiceTest: getDepartmentStats filtra por departamento |
| **Descripción** | Verifica que consultar departamento `Engineering` retorna solo estudiantes de Ingeniería. |
| **Prerrequisitos/Condiciones** | Base de datos con 3 departamentos. |
| **Entradas** | `dept=Engineering`. |
| **Acciones** | `service.getDepartmentStats(dept)`. |
| **Salida Esperada** | Solo estudiantes de Ingeniería. |
| **Criterios de Aceptación** | `assertTrue(all(s.getDept().equals("Engineering")))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-052 |
| **Nombre** | AnalyticsRepositoryTest: findSummaryStats retorna agregados |
| **Descripción** | Verifica query custom que calcula totales por estado. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con datos de prueba (100 usuarios, varios estados). |
| **Entradas** | Ninguna. |
| **Acciones** | `repository.findSummaryStats()`. |
| **Salida Esperada** | Objeto con conteos por estado. |
| **Criterios de Aceptación** | Totales correctos. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-053 |
| **Nombre** | AnalyticsRepositoryTest: findByDepartment filtra correctamente |
| **Descripción** | Verifica query `findByDepartment(dept)`. |
| **Prerrequisitos/Condiciones** | Mismo setup. |
| **Entradas** | `dept=Medicine`. |
| **Acciones** | `repository.findByDepartment(dept)`. |
| **Salida Esperada** | Solo usuarios de Medicina. |
| **Criterios de Aceptación** | Todos tienen `dept=Medicine`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-054 |
| **Nombre** | TimeSeriesRepositoryTest: findHourlyData retorna con timestamp |
| **Descripción** | Verifica que series temporal incluye timestamp de cada punto. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con datos históricos. |
| **Entradas** | Período últimas 24h. |
| **Acciones** | `repository.findHourlyData(24)`. |
| **Salida Esperada** | Lista de puntos con timestamp. |
| **Criterios de Aceptación** | `assertTrue(allHaveTimestamp())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-055 |
| **Nombre** | K-AnonymityProcessorTest: grouping con k=5 |
| **Descripción** | Verifica que valores de grupos < 5 se suprimen o agrupan. |
| **Prerrequisitos/Condiciones** | Procesador con k=5. |
| **Entradas** | Distribución: [10, 3, 7, 2, 9] por estado. |
| **Acciones** | `processor.anonymize(distribution)`. |
| **Salida Esperada** | [10, 5, 7, 5, 9] (pequeños agrupados). |
| **Criterios de Aceptación** | Ningún grupo < 5. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-056 |
| **Nombre** | AnalyticsServiceTest: getDepartmentStats aplica k-anonymity |
| **Descripción** | Verifica que estadísticas por departamento también respetan k-anonymity. |
| **Prerrequisitos/Condiciones** | Servicio con política k=5. |
| **Entradas** | Departamento con total 20 (12 HEALTHY, 3 QUARANTINE, 5 RECOVERED). |
| **Acciones** | `service.getDepartmentStats(dept)`. |
| **Salida Esperada** | QUARANTINE (3) se suprime. |
| **Criterios de Aceptación** | No aparece QUARANTINE en salida. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-057 |
| **Nombre** | AnalyticsControllerTest: Sin autorización retorna 403 |
| **Descripción** | Verifica que endpoints de analytics requieren rol `HEALTH_OFFICER`. |
| **Prerrequisitos/Condiciones** | Mismo slice; `@WithMockUser()` sin rol. |
| **Entradas** | GET /api/v1/analytics/summary. |
| **Acciones** | `mockMvc.perform(get(...))`. |
| **Salida Esperada** | HTTP 403. |
| **Criterios de Aceptación** | `status().isForbidden()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-058 |
| **Nombre** | AnalyticsServiceTest: Performance con >100k records |
| **Descripción** | Verifica que cálculo de resumen completa en <1s con 100k+ usuarios. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con base datos poblada (100k usuarios). |
| **Entradas** | Ninguna. |
| **Acciones** | `service.getSummary()` con timer. |
| **Salida Esperada** | Tiempo <1000ms. |
| **Criterios de Aceptación** | `assertTrue(duration < 1000)`. |

---

# auth-service

## Auth Service — LDAP & JWT Token Generation

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-024 |
| **Nombre** | LdapAuthControllerTest: POST /login con credenciales válidas retorna JWT |
| **Descripción** | Verifica que `POST /api/v1/auth/login` con username/password válidos retorna token JWT. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(AuthController)` + mock LDAP provider. |
| **Entradas** | Body JSON `{"username":"student","password":"pass123"}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/auth/login").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200 con `$.token` presente. |
| **Criterios de Aceptación** | `jsonPath("$.token").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-025 |
| **Nombre** | LdapAuthControllerTest: POST /login con credenciales inválidas retorna 401 |
| **Descripción** | Verifica que password incorrecto resulta en HTTP 401. |
| **Prerrequisitos/Condiciones** | Mismo slice; mock LDAP rechaza credenciales. |
| **Entradas** | Body con password incorrecto. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 401. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-026 |
| **Nombre** | LdapAuthControllerTest: POST /login con usuario inexistente retorna 401 |
| **Descripción** | Verifica que usuario no registrado en LDAP retorna 401. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | `username=nonexistent`. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 401. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-027 |
| **Nombre** | JwtTokenProviderTest: generateToken crea token válido |
| **Descripción** | Verifica que `generateToken(username)` retorna JWT firmado y decodificable. |
| **Prerrequisitos/Condiciones** | Provider con secret key de prueba. |
| **Entradas** | `username=testuser`. |
| **Acciones** | `provider.generateToken(username)`. |
| **Salida Esperada** | Token decodificable con claims correctos. |
| **Criterios de Aceptación** | `assertTrue(isValidJwt(token))` y `assertEquals(username, extractSubject(token))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-028 |
| **Nombre** | JwtTokenProviderTest: validateToken acepta token válido |
| **Descripción** | Verifica que token generado y válido se acepta. |
| **Prerrequisitos/Condiciones** | Mismo provider. |
| **Entradas** | Token válido. |
| **Acciones** | `provider.validateToken(token)`. |
| **Salida Esperada** | `true`. |
| **Criterios de Aceptación** | `assertTrue(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-029 |
| **Nombre** | JwtTokenProviderTest: validateToken rechaza token expirado |
| **Descripción** | Verifica que token con `exp` pasado se rechaza. |
| **Prerrequisitos/Condiciones** | Provider que genera tokens con TTL=1s. |
| **Entradas** | Token expirado. |
| **Acciones** | `provider.validateToken(expiredToken)`. |
| **Salida Esperada** | `false`. |
| **Criterios de Aceptación** | `assertFalse(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-030 |
| **Nombre** | JwtTokenProviderTest: validateToken rechaza token firmado con otra key |
| **Descripción** | Verifica que token firmado con diferente secret se rechaza. |
| **Prerrequisitos/Condiciones** | Dos providers con diferentes secrets. |
| **Entradas** | Token del provider 1, validación con provider 2. |
| **Acciones** | `provider2.validateToken(token1)`. |
| **Salida Esperada** | `false`. |
| **Criterios de Aceptación** | `assertFalse(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-031 |
| **Nombre** | JwtTokenProviderTest: extractSubject obtiene username |
| **Descripción** | Verifica que se puede extraer el subject (username) del token. |
| **Prerrequisitos/Condiciones** | Mismo provider. |
| **Entradas** | Token válido. |
| **Acciones** | `provider.extractSubject(token)`. |
| **Salida Esperada** | Username original. |
| **Criterios de Aceptación** | `assertEquals(username, extracted)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-032 |
| **Nombre** | JwtTokenProviderTest: extractSubject maneja token inválido |
| **Descripción** | Verifica comportamiento defensivo al extraer claims de token inválido. |
| **Prerrequisitos/Condiciones** | Mismo provider. |
| **Entradas** | Token mal formado. |
| **Acciones** | `provider.extractSubject(badToken)`. |
| **Salida Esperada** | Excepción `JwtException` o similar. |
| **Criterios de Aceptación** | `assertThrows(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-033 |
| **Nombre** | LdapAuthServiceTest: authenticate delega a LdapTemplate |
| **Descripción** | Verifica que el servicio invoca `LdapTemplate.authenticate()` con DN construido. |
| **Prerrequisitos/Condiciones** | LdapTemplate mockeado. |
| **Entradas** | `username=student`, `password=pass123`. |
| **Acciones** | `service.authenticate(username, password)`. |
| **Salida Esperada** | `verify(ldapTemplate).authenticate(...)` invocado. |
| **Criterios de Aceptación** | Mock verify. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-034 |
| **Nombre** | LdapAuthServiceTest: authenticate retorna anonymousId con UUID |
| **Descripción** | Verifica que autenticación exitosa retorna objeto con anonymousId (UUID). |
| **Prerrequisitos/Condiciones** | Mismo setup. |
| **Entradas** | Credenciales válidas. |
| **Acciones** | `service.authenticate(username, password)`. |
| **Salida Esperada** | AuthResponse con anonymousId ≠ username. |
| **Criterios de Aceptación** | `assertNotEquals(username, response.getAnonymousId())` y `isValidUUID(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-035 |
| **Nombre** | RefreshTokenControllerTest: POST /refresh con token válido retorna nuevo JWT |
| **Descripción** | Verifica que `POST /api/v1/auth/refresh` con refresh token válido retorna JWT nuevo. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(AuthController)`; provider mockeado. |
| **Entradas** | Body `{"refreshToken":"..."}` válido. |
| **Acciones** | `mockMvc.perform(post("/api/v1/auth/refresh").contentType(JSON).content(body))`. |
| **Salida Esperada** | HTTP 200 con `$.token` nuevo. |
| **Criterios de Aceptación** | Token nuevo ≠ original. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-036 |
| **Nombre** | RefreshTokenControllerTest: POST /refresh con token expirado retorna 401 |
| **Descripción** | Verifica que refresh token expirado resulta en 401. |
| **Prerrequisitos/Condiciones** | Mismo slice; provider rechaza token expirado. |
| **Entradas** | Refresh token expirado. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 401. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-037 |
| **Nombre** | TokenRepositoryTest: save + find refresh token |
| **Descripción** | Verifica con `@DataJpaTest` que refresh tokens se persisten y recuperan. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con H2. |
| **Entradas** | RefreshToken(userId, token, expiresAt). |
| **Acciones** | `repository.save()` + `repository.findByToken()`. |
| **Salida Esperada** | Token recuperado con mismos atributos. |
| **Criterios de Aceptación** | `assertEquals(original, found)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-038 |
| **Nombre** | TokenRepositoryTest: findByUserAndValidToken retorna solo válidos |
| **Descripción** | Verifica query que retorna solo tokens no expirados de un usuario. |
| **Prerrequisitos/Condiciones** | Mismo setup con 3 tokens (2 válidos, 1 expirado). |
| **Entradas** | userId, criterio de validez. |
| **Acciones** | `repository.findByUserAndValidToken(userId)`. |
| **Salida Esperada** | 2 tokens. |
| **Criterios de Aceptación** | `assertEquals(2, result.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-039 |
| **Nombre** | AuthSecurityConfigTest: SecurityFilterChain bean se registra |
| **Descripción** | Verifica que la configuración de seguridad crea bean `SecurityFilterChain`. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con perfiles `test`. |
| **Entradas** | Contexto de aplicación. |
| **Acciones** | Obtener bean `securityFilterChain`. |
| **Salida Esperada** | Bean presente. |
| **Criterios de Aceptación** | `assertNotNull(bean)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-040 |
| **Nombre** | JwtAuthenticationFilterTest: Header Authorization parseado |
| **Descripción** | Verifica que filtro extrae token del header `Authorization: Bearer <token>`. |
| **Prerrequisitos/Condiciones** | Filtro mockeado. |
| **Entradas** | Header `Authorization: Bearer <valid-token>`. |
| **Acciones** | Pasar request a través de filtro. |
| **Salida Esperada** | Token extraído. |
| **Criterios de Aceptación** | `verify(filter).extractToken(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-041 |
| **Nombre** | JwtAuthenticationFilterTest: Sin header Authorization permite anónimo |
| **Descripción** | Verifica que solicitud sin header continúa como usuario anónimo. |
| **Prerrequisitos/Condiciones** | Mismo filtro. |
| **Entradas** | Sin header Authorization. |
| **Acciones** | Request pasa filtro. |
| **Salida Esperada** | Usuario anónimo establecido. |
| **Criterios de Aceptación** | `assertTrue(isAnonymous)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-042 |
| **Nombre** | JwtAuthenticationFilterTest: Header mal formado se ignora |
| **Descripción** | Verifica que header `Authorization: Invalid` no causa error. |
| **Prerrequisitos/Condiciones** | Mismo filtro. |
| **Entradas** | Header `Authorization: Invalid`. |
| **Acciones** | Request pasa filtro. |
| **Salida Esperada** | Trata como anónimo; no excepción. |
| **Criterios de Aceptación** | Sin excepción y usuario anónimo. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-043 |
| **Nombre** | LdapConnectionPoolTest: Pool mantiene conexiones reutilizables |
| **Descripción** | Verifica que pool de conexiones LDAP reutiliza conexiones. |
| **Prerrequisitos/Condiciones** | Pool con `maxSize=5`. |
| **Entradas** | Obtener 5 conexiones. |
| **Acciones** | Liberar y obtener nuevamente. |
| **Salida Esperada** | Las nuevas conexiones son del pool, no nuevas. |
| **Criterios de Aceptación** | Verificar IDs de conexión reutilizados. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-044 |
| **Nombre** | LdapConnectionPoolTest: Pool exceeding maxSize crea nuevas conexiones |
| **Descripción** | Verifica que pedir más conexiones que `maxSize` crea nuevas temporales. |
| **Prerrequisitos/Condiciones** | Pool con `maxSize=3`. |
| **Entradas** | Solicitar 6 conexiones. |
| **Acciones** | `pool.getConnection()` × 6. |
| **Salida Esperada** | 6 conexiones disponibles (3 del pool + 3 nuevas). |
| **Criterios de Aceptación** | Count = 6. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-045 |
| **Nombre** | JwtTokenProviderTest: generateRefreshToken TTL es mayor que access token |
| **Descripción** | Verifica que refresh token expira más tarde que access token. |
| **Prerrequisitos/Condiciones** | Provider con `accessTTL=1h`, `refreshTTL=7d`. |
| **Entradas** | Ambos tokens. |
| **Acciones** | Comparar `exp` claims. |
| **Salida Esperada** | `refreshExp > accessExp`. |
| **Criterios de Aceptación** | `assertTrue(refreshExp > accessExp)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-046 |
| **Nombre** | AuthAuditServiceTest: Login exitoso se audita |
| **Descripción** | Verifica que cada autenticación exitosa se registra en auditoría. |
| **Prerrequisitos/Condiciones** | Servicio con `@EventPublisher`. |
| **Entradas** | Usuario autenticado. |
| **Acciones** | `service.authenticate(username, password)`. |
| **Salida Esperada** | Evento `auth.login.success` publicado. |
| **Criterios de Aceptación** | `verify(publisher).publishEvent(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-047 |
| **Nombre** | AuthAuditServiceTest: Login fallido se audita |
| **Descripción** | Verifica que autenticación fallida se registra (para detectar fuerza bruta). |
| **Prerrequisitos/Condiciones** | Mismo servicio. |
| **Entradas** | Credenciales inválidas. |
| **Acciones** | `service.authenticate(username, badPassword)`. |
| **Salida Esperada** | Evento `auth.login.failure` publicado. |
| **Criterios de Aceptación** | `verify(publisher).publishEvent(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-048 |
| **Nombre** | LdapAuthServiceTest: Múltiples logins concurrentes handled |
| **Descripción** | Verifica que logins concurrentes no causan race conditions. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@ThreadSafety`. |
| **Entradas** | 10 hilos autenticándose simultáneamente. |
| **Acciones** | Ejecutar en paralelo. |
| **Salida Esperada** | Todos completan sin error; todos obtienen unique anonymousIds. |
| **Criterios de Aceptación** | 10 IDs únicos. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-049 |
| **Nombre** | JwtTokenProviderTest: Token claims include custom data |
| **Descripción** | Verifica que custom claims (roles, scopes) se incluyen en token. |
| **Prerrequisitos/Condiciones** | Provider que acepta claims adicionales. |
| **Entradas** | `generateToken(username, roles=["STUDENT", "MODERATOR"])`. |
| **Acciones** | Token generado y decodificado. |
| **Salida Esperada** | Claims contienen `roles` array. |
| **Criterios de Aceptación** | `assertTrue(token.getClaims().contains("roles"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-050 |
| **Nombre** | RefreshTokenServiceTest: Old refresh token revoked on new issue |
| **Descripción** | Verifica que al generar nuevo refresh token, el anterior se revoca. |
| **Prerrequisitos/Condiciones** | Servicio con repositorio. |
| **Entradas** | Usuario con refresh token existente. |
| **Acciones** | `service.generateRefreshToken(userId)`. |
| **Salida Esperada** | Nuevo token generado; antiguo marcado `revoked=true`. |
| **Criterios de Aceptación** | `assertTrue(old.isRevoked())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-051 |
| **Nombre** | TokenRotationPolicyTest: Tokens rotados automáticamente |
| **Descripción** | Verifica política que fuerza rotación de tokens después de N días. |
| **Prerrequisitos/Condiciones** | Política con `rotationIntervalDays=30`. |
| **Entradas** | Token con `issuedAt` hace 35 días. |
| **Acciones** | Validar token. |
| **Salida Esperada** | Token rechazado; cliente debe hacer refresh. |
| **Criterios de Aceptación** | `assertFalse(validate(...))`. |

---

# file-service

## File Service — Upload & Storage

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-059 |
| **Nombre** | FileUploadControllerTest: POST /files/upload multipart válido retorna 201 |
| **Descripción** | Verifica que un PDF válido se acepta y retorna 201 Created. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(FileUploadController)`; servicio mockeado. |
| **Entradas** | Multipart request con campo `file` contentType `application/pdf`. |
| **Acciones** | `mockMvc.perform(multipart("/api/v1/files/upload").file(...))`. |
| **Salida Esperada** | HTTP 201 con `$.fileId` en respuesta. |
| **Criterios de Aceptación** | `status().isCreated()` y `jsonPath("$.fileId").exists()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-060 |
| **Nombre** | FileUploadControllerTest: POST /files/upload sin archivo retorna 400 |
| **Descripción** | Verifica que upload sin campo `file` retorna 400. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | Multipart request sin campo `file`. |
| **Acciones** | `mockMvc.perform(multipart(...))`. |
| **Salida Esperada** | HTTP 400. |
| **Criterios de Aceptación** | `status().isBadRequest()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-061 |
| **Nombre** | FileStorageServiceTest: Save persiste archivo en disco |
| **Descripción** | Verifica que archivo se guarda en directorio configurado. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@TempDir` para directorio temporal. |
| **Entradas** | Contenido PDF, nombre `certificate.pdf`. |
| **Acciones** | `service.saveFile(content, filename)`. |
| **Salida Esperada** | Archivo existe en disco; retorna fileId y path. |
| **Criterios de Aceptación** | `Files.exists(path)` y `fileId != null`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-062 |
| **Nombre** | FileMetadataServiceTest: GET /files/{id} retorna metadatos |
| **Descripción** | Verifica que metadatos (nombre, tamaño, tipo, uploadedAt) se recuperan correctamente. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(FileMetadataController)`; repositorio mockeado. |
| **Entradas** | File UUID. |
| **Acciones** | `mockMvc.perform(get("/api/v1/files/{id}"))`. |
| **Salida Esperada** | HTTP 200 con JSON `{filename, size, contentType, uploadedAt}`. |
| **Criterios de Aceptación** | `jsonPath("$.filename").exists()` y `jsonPath("$.size").isNumber()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-063 |
| **Nombre** | FileValidationServiceTest: PDF válido pasa validación |
| **Descripción** | Verifica que archivo con magic bytes PDF válidos se acepta. |
| **Prerrequisitos/Condiciones** | Servicio con validación de magic bytes. |
| **Entradas** | Contenido PDF con header `%PDF-1.4`. |
| **Acciones** | `service.validate(content, "application/pdf")`. |
| **Salida Esperada** | `isValid = true`. |
| **Criterios de Aceptación** | `assertTrue(isValid)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-064 |
| **Nombre** | FileValidationServiceTest: Archivo sin extensión rechazado |
| **Descripción** | Verifica que archivo sin extensión válida es rechazado. |
| **Prerrequisitos/Condiciones** | Servicio con lista blanca de tipos permitidos. |
| **Entradas** | Contenido genérico sin extensión `.pdf`. |
| **Acciones** | `service.validate(content, "application/octet-stream")`. |
| **Salida Esperada** | `isValid = false`. |
| **Criterios de Aceptación** | `assertFalse(isValid)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-065 |
| **Nombre** | FileStorageServiceTest: Archivo > 10MB rechazado |
| **Descripción** | Verifica que upload de archivo > límite de tamaño es rechazado. |
| **Prerrequisitos/Condiciones** | Configuración con `max-file-size=10485760`. |
| **Entradas** | Contenido de 15 MB. |
| **Acciones** | `service.validateSize(content.length)`. |
| **Salida Esperada** | Excepción `FileSizeExceededException`. |
| **Criterios de Aceptación** | `assertThrows(FileSizeExceededException.class, ...)`. |

---

# gateway-service

## Gateway Service — QR Validation & Campus Entry

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-085 |
| **Nombre** | QrValidationControllerTest: POST /qr/validate token válido retorna GREEN |
| **Descripción** | Verifica que token JWT válido retorna status `GREEN` (autorizado entrada). |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(QrValidationController)`; `TokenValidationService` mockeado. |
| **Entradas** | JSON `{token: "<valid-jwt>"}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/qr/validate").contentType("application/json").content(...))`. |
| **Salida Esperada** | HTTP 200 con `$.status = "GREEN"`. |
| **Criterios de Aceptación** | `jsonPath("$.status").value("GREEN")`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-086 |
| **Nombre** | QrValidationControllerTest: POST /qr/validate token expirado retorna RED |
| **Descripción** | Verifica que token expirado retorna status `RED` (deniega entrada). |
| **Prerrequisitos/Condiciones** | Servicio verifica `exp` claim. |
| **Entradas** | JSON `{token: "<expired-jwt>"}` (issuedAt 30 días atrás). |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 200 con `$.status = "RED"` o HTTP 401. |
| **Criterios de Aceptación** | `status().is(anyOf(200, 401))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-087 |
| **Nombre** | QrValidationControllerTest: GET /actuator/health retorna UP |
| **Descripción** | Verifica que health check responde con estatus UP (liveness probe). |
| **Prerrequisitos/Condiciones** | Gateway servicio levantado. |
| **Entradas** | GET request. |
| **Acciones** | `mockMvc.perform(get("/actuator/health"))`. |
| **Salida Esperada** | HTTP 200 con `$.status = "UP"`. |
| **Criterios de Aceptación** | `status().isOk()` y `jsonPath("$.status").value("UP")`. |

---

# notification-service

## Notification Service — Multi-Channel Alert Dispatch

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-097 |
| **Nombre** | NotificationEventConsumerTest: Kafka event `survey.submitted` consumed |
| **Descripción** | Verifica que el consumer escucha y procesa eventos de Kafka. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Testcontainers Kafka; listener registrado. |
| **Entradas** | Evento Kafka topic `survey-events` con payload JSON. |
| **Acciones** | Publicar evento en topic; esperar a consumer. |
| **Salida Esperada** | Notificación encolada para envío. |
| **Criterios de Aceptación** | `verify(notificationQueue).enqueue(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-098 |
| **Nombre** | NotificationEventConsumerTest: Event con estructura inválida rechazado |
| **Descripción** | Verifica que evento con payload mal formado no causa crash. |
| **Prerrequisitos/Condiciones** | Mismo slice con dead-letter topic. |
| **Entradas** | Evento con JSON inválido. |
| **Acciones** | Publicar en topic. |
| **Salida Esperada** | Evento enviado a dead-letter topic; exception logged. |
| **Criterios de Aceptación** | `verify(deadLetterTopic).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-099 |
| **Nombre** | EmailDispatchServiceTest: Email enviado exitosamente |
| **Descripción** | Verifica que `dispatchEmail(recipient, template, vars)` envía vía SMTP. |
| **Prerrequisitos/Condiciones** | Servicio con `JavaMailSender` mockeado. |
| **Entradas** | Email `test@example.com`, template `alert`, variables. |
| **Acciones** | `service.dispatchEmail(...)`. |
| **Salida Esperada** | Email enviado (sin excepción). |
| **Criterios de Aceptación** | `verify(mailSender).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-100 |
| **Nombre** | EmailDispatchServiceTest: Email con destinatario inválido rechazado |
| **Descripción** | Verifica que email inválido no se intenta enviar. |
| **Prerrequisitos/Condiciones** | Validación de formato. |
| **Entradas** | Email `not-an-email`. |
| **Acciones** | `service.dispatchEmail(...)`. |
| **Salida Esperada** | Excepción `InvalidEmailException`. |
| **Criterios de Aceptación** | `assertThrows(InvalidEmailException.class, ...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-101 |
| **Nombre** | SmsDispatchServiceTest: SMS enviado a operador |
| **Descripción** | Verifica que SMS se envía a operador de telefonía (mock). |
| **Prerrequisitos/Condiciones** | `SmsGateway` mockeado. |
| **Entradas** | Número `+34600000000`, contenido SMS. |
| **Acciones** | `service.dispatchSms(phone, message)`. |
| **Salida Esperada** | Respuesta exitosa del gateway. |
| **Criterios de Aceptación** | `verify(gateway).send(phone, message)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-102 |
| **Nombre** | SmsDispatchServiceTest: SMS con número inválido rechazado |
| **Descripción** | Verifica que número malformado no se intenta enviar. |
| **Prerrequisitos/Condiciones** | Validación de E.164. |
| **Entradas** | Número `invalid-phone`. |
| **Acciones** | `service.dispatchSms(...)`. |
| **Salida Esperada** | Excepción `InvalidPhoneException`. |
| **Criterios de Aceptación** | `assertThrows(InvalidPhoneException.class, ...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-103 |
| **Nombre** | NotificationTemplateServiceTest: Template renderizado con variables |
| **Descripción** | Verifica que placeholder `{{var}}` se reemplaza con valor. |
| **Prerrequisitos/Condiciones** | Servicio con engine Freemarker/Thymeleaf. |
| **Entradas** | Template `"Alert: {{name}}"`, variables `{name: "John"}`. |
| **Acciones** | `service.render(template, vars)`. |
| **Salida Esperada** | Resultado `"Alert: John"`. |
| **Criterios de Aceptación** | `assertEquals("Alert: John", result)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-104 |
| **Nombre** | NotificationTemplateServiceTest: Template inválido lanza excepción |
| **Descripción** | Verifica que sintaxis de template inválida genera error. |
| **Prerrequisitos/Condiciones** | Mismo servicio. |
| **Entradas** | Template `"Alert: {{unclosed"`. |
| **Acciones** | `service.render(...)`. |
| **Salida Esperada** | Excepción `TemplateParseException`. |
| **Criterios de Aceptación** | `assertThrows(TemplateParseException.class, ...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-105 |
| **Nombre** | NotificationChannelStrategyTest: Multi-channel sends email Y sms |
| **Descripción** | Verifica que notificación multi-canal envía por ambos canales. |
| **Prerrequisitos/Condiciones** | Estrategia con lista `channels = [EMAIL, SMS]`. |
| **Entradas** | Recipient con email y teléfono. |
| **Acciones** | `service.dispatch(recipient, channels, content)`. |
| **Salida Esperada** | Email y SMS enviados. |
| **Criterios de Aceptación** | `verify(emailService, times(1)).dispatch(...)` Y `verify(smsService, times(1)).dispatch(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-106 |
| **Nombre** | NotificationRetryServiceTest: Failed dispatch reintentado |
| **Descripción** | Verifica que envío fallido se reintenta con backoff. |
| **Prerrequisitos/Condiciones** | Configuración con `maxRetries=3`, `backoffMs=1000`. |
| **Entradas** | Despacho que falla en intento 1, y 2, exitoso en intento 3. |
| **Acciones** | `service.dispatchWithRetry(notification)`. |
| **Salida Esperada** | Intento 1 → falla, Intento 2 → falla (después delay), Intento 3 → éxito. |
| **Criterios de Aceptación** | `verify(gateway, times(3)).send(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-107 |
| **Nombre** | NotificationRetryServiceTest: Max retries exceeded descarta notificación |
| **Descripción** | Verifica que tras N fallos, notificación se descarta y se audita. |
| **Prerrequisitos/Condiciones** | Mismo servicio con `maxRetries=2`. |
| **Entradas** | Despacho que falla 3 veces. |
| **Acciones** | `service.dispatchWithRetry(...)`. |
| **Salida Esperada** | Notificación marcada failed; evento auditoría publicado. |
| **Criterios de Aceptación** | `assertTrue(notification.isFailed())` Y `verify(auditPublisher).publish(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-108 |
| **Nombre** | NotificationDeduplicationServiceTest: Evento duplicado no genera 2 notificaciones |
| **Descripción** | Verifica que mismo evento Kafka consumido dos veces envía una sola notificación. |
| **Prerrequisitos/Condiciones** | Deduplicación por event-id; repositorio de eventos procesados. |
| **Entradas** | Mismo evento enviado dos veces. |
| **Acciones** | Procesar ambas copias. |
| **Salida Esperada** | Una sola notificación despachada. |
| **Criterios de Aceptación** | `assertEquals(1, notificationQueue.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-109 |
| **Nombre** | NotificationStatusTrackerTest: Status tracking actualiza DB |
| **Descripción** | Verifica que cada intento de despacho registra status en BD. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@DataJpaTest` slice para repo. |
| **Entradas** | Notification con id. |
| **Acciones** | Despacho; cambio status a `SENT`. |
| **Salida Esperada** | Row en tabla `notification_history` con `{notification_id, status='SENT', timestamp}`. |
| **Criterios de Aceptación** | `assertEquals("SENT", repo.findLatestStatus(notificationId))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-110 |
| **Nombre** | NotificationWebhookCallbackTest: Webhook callback actualiza status |
| **Descripción** | Verifica que callback POST desde operador actualiza status. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(WebhookController)`. |
| **Entradas** | JSON `{notification_id: "123", status: "DELIVERED"}`. |
| **Acciones** | `mockMvc.perform(post("/api/v1/webhooks/sms-delivery"))`. |
| **Salida Esperada** | HTTP 200; notificación marcada DELIVERED en BD. |
| **Criterios de Aceptación** | `status().isOk()` Y `assertEquals("DELIVERED", repo.findStatus("123"))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-111 |
| **Nombre** | NotificationWebhookCallbackTest: Webhook con signature inválida rechazado |
| **Descripción** | Verifica que callback sin firma HMAC válida es rechazado. |
| **Prerrequisitos/Condiciones** | Validación de firma con secret. |
| **Entradas** | JSON válido pero header `X-Signature` inválido. |
| **Acciones** | `mockMvc.perform(post(...))`. |
| **Salida Esperada** | HTTP 401 Unauthorized. |
| **Criterios de Aceptación** | `status().isUnauthorized()`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-112 |
| **Nombre** | NotificationRepositoryTest: Query por recipient obtiene historial |
| **Descripción** | Verifica que `findByRecipient(email)` retorna todas las notificaciones. |
| **Prerrequisitos/Condiciones** | `@DataJpaTest` con H2 in-memory. |
| **Entradas** | 5 notificaciones para mismo recipient. |
| **Acciones** | `repo.findByRecipient("test@example.com")`. |
| **Salida Esperada** | Lista con 5 elementos. |
| **Criterios de Aceptación** | `assertEquals(5, result.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-113 |
| **Nombre** | NotificationRepositoryTest: Query por fecha filtra correctamente |
| **Descripción** | Verifica que `findByCreatedAtBetween(start, end)` retorna solo notificaciones en rango. |
| **Prerrequisitos/Condiciones** | Mismo slice con datos de 3 semanas. |
| **Entradas** | Start: "2026-05-20", End: "2026-05-24". |
| **Acciones** | `repo.findByCreatedAtBetween(...)`. |
| **Salida Esperada** | Solo notificaciones en rango (2 de 5). |
| **Criterios de Aceptación** | `assertEquals(2, result.size())`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-114 |
| **Nombre** | NotificationServiceTest: Concurrent dispatch no causa race |
| **Descripción** | Verifica que N threads despachando simultáneamente no crean condiciones de carrera. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con `@ThreadSafety`. |
| **Entradas** | 10 notificaciones diferentes. |
| **Acciones** | Ejecutar en 10 threads en paralelo. |
| **Salida Esperada** | Todas se despachan sin error; BD consistente. |
| **Criterios de Aceptación** | 10 records en `notification_history`; 0 excepciones. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-115 |
| **Nombre** | NotificationPersistenceTest: Large batch (1000) insertados sin timeout |
| **Descripción** | Verifica que inserción de 1000 notificaciones completa en <5s. |
| **Prerrequisitos/Condiciones** | BD real con índices; `@SpringBootTest`. |
| **Entradas** | 1000 objetos Notification. |
| **Acciones** | `repo.saveAll(list)`. |
| **Salida Esperada** | Todas guardadas; tiempo < 5 segundos. |
| **Criterios de Aceptación** | `assertEquals(1000, repo.count())` Y tiempo < 5000ms. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-116 |
| **Nombre** | NotificationChannelFailureTest: Email gateway timeout handled gracefully |
| **Descripción** | Verifica que timeout del servidor SMTP no crashea el servicio. |
| **Prerrequisitos/Condiciones** | SMTP gateway mockeado para lanzar `SocketTimeoutException`. |
| **Entradas** | Despacho de email. |
| **Acciones** | `service.dispatchEmail(...)`. |
| **Salida Esperada** | Excepción capturada; reintento encolado; servicio sigue funcionando. |
| **Criterios de Aceptación** | `verify(retryQueue).enqueue(...)` Y no `RuntimeException`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-117 |
| **Nombre** | NotificationChannelFailureTest: SMS gateway error logged y reportado |
| **Descripción** | Verifica que fallo del gateway SMS se registra en auditoría. |
| **Prerrequisitos/Condiciones** | Gateway mockeado para retornar error 50x. |
| **Entradas** | Despacho de SMS. |
| **Acciones** | `service.dispatchSms(...)`. |
| **Salida Esperada** | Error logged; evento `sms.dispatch.failed` publicado. |
| **Criterios de Aceptación** | `verify(auditLog).error(...)` Y `verify(eventPublisher).publish(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-118 |
| **Nombre** | NotificationTemplateEngineTest: Freemarker expressions evaluadas |
| **Descripción** | Verifica que expresiones Freemarker complejas se evalúan correctamente. |
| **Prerrequisitos/Condiciones** | Engine Freemarker configurado. |
| **Entradas** | Template `"Name: ${user.firstName} ${user.lastName} (Age: ${user.age?c})"`. |
| **Acciones** | `service.render(template, model)`. |
| **Salida Esperada** | `"Name: John Doe (Age: 28)"`. |
| **Criterios de Aceptación** | `assertEquals("Name: John Doe (Age: 28)", result)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-119 |
| **Nombre** | NotificationTemplateEngineTest: Conditional logic en template |
| **Descripción** | Verifica que condicionales `<#if>` se evalúan. |
| **Prerrequisitos/Condiciones** | Mismo engine. |
| **Entradas** | Template `"<#if status == 'RED'>ALERT</#if>"`, status='RED'. |
| **Acciones** | `service.render(...)`. |
| **Salida Esperada** | `"ALERT"`. |
| **Criterios de Aceptación** | `assertEquals("ALERT", result)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-120 |
| **Nombre** | NotificationBatchProcessorTest: Batch procesado en chunks |
| **Descripción** | Verifica que batch grande se procesa en chunks (ej: 100 a la vez). |
| **Prerrequisitos/Condiciones** | Configuración con `batchSize=100`. |
| **Entradas** | 350 notificaciones. |
| **Acciones** | `service.processBatch(list)`. |
| **Salida Esperada** | 4 lotes: 100, 100, 100, 50. |
| **Criterios de Aceptación** | `verify(gateway, times(4)).sendBatch(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-121 |
| **Nombre** | NotificationAuditServiceTest: Audit event published for cada dispatch |
| **Descripción** | Verifica que evento auditoría se publica para cada intento. |
| **Prerrequisitos/Condiciones** | `@EventPublisher`. |
| **Entradas** | Despacho de notificación. |
| **Acciones** | `service.dispatch(notification)`. |
| **Salida Esperada** | Evento `notification.dispatched` con timestamp y metadata. |
| **Criterios de Aceptación** | `verify(eventPublisher).publishEvent(...)`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-122 |
| **Nombre** | NotificationMetricsTest: Prometheus metrics incremented |
| **Descripción** | Verifica que contador `notifications_sent_total` se incrementa. |
| **Prerrequisitos/Condiciones** | `@SpringBootTest` con Micrometer. |
| **Entradas** | Despacho exitoso. |
| **Acciones** | `service.dispatch(...)` → revisar métrica. |
| **Salida Esperada** | `notifications_sent_total{channel="email"} += 1`. |
| **Criterios de Aceptación** | `meterRegistry.find("notifications_sent_total").gauge().value() >= 1`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-123 |
| **Nombre** | NotificationMetricsTest: Latency histogram recorded |
| **Descripción** | Verifica que latencia de despacho se registra en histograma. |
| **Prerrequisitos/Condiciones** | Mismo slice. |
| **Entradas** | Despacho que tarda 150ms. |
| **Acciones** | `service.dispatch(...)` → revisar histograma. |
| **Salida Esperada** | `notification_dispatch_latency_ms` contiene observación 150. |
| **Criterios de Aceptación** | `meterRegistry.find("notification_dispatch_latency_ms").timer().totalTime(TimeUnit.MILLISECONDS) >= 150`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-124 |
| **Nombre** | NotificationControllerTest: GET /api/v1/notifications retorna lista |
| **Descripción** | Verifica que endpoint retorna historial de notificaciones paginado. |
| **Prerrequisitos/Condiciones** | `@WebMvcTest(NotificationController)`. |
| **Entradas** | Query `?page=0&size=20`. |
| **Acciones** | `mockMvc.perform(get("/api/v1/notifications"))`. |
| **Salida Esperada** | HTTP 200 con array de notificaciones. |
| **Criterios de Aceptación** | `jsonPath("$").isArray()` Y `jsonPath("$", hasSize(20))`. |

| Campo | Descripción |
|---|---|
| **Identificador Único** | PU-125 |
| **Nombre** | NotificationControllerTest: GET /actuator/health retorna UP |
| **Descripción** | Verifica que health check responde con estatus UP. |
| **Prerrequisitos/Condiciones** | Servicio levantado. |
| **Entradas** | GET request. |
| **Acciones** | `mockMvc.perform(get("/actuator/health"))`. |
| **Salida Esperada** | HTTP 200 con `$.status = "UP"`. |
| **Criterios de Aceptación** | `status().isOk()` Y `jsonPath("$.status").value("UP")`. |

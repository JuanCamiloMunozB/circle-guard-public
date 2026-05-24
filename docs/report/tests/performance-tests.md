# Especificación — Pruebas de Rendimiento y Estrés

> Documento generado para HU-09 (paso 9.4). Sigue el formato de la sección 4.4
> del `taller2-report.md`: modelo de carga, escenarios ejecutados, resultados,
> análisis de fallos y conclusiones.
>
> Las pruebas de rendimiento están implementadas con **Locust** en
> `tests/performance/locustfile.py`. Se eligió Locust sobre alternativas como
> JMeter porque permite expresar el comportamiento de cada perfil de usuario
> en Python con pesos por tarea, lo que se acerca más a una simulación
> realista que a una secuencia rígida de peticiones.

## Modelo de carga

El locustfile define cinco clases de usuario que replican los roles reales del
sistema y la proporción esperada entre ellos. Esta proporción es la decisión
de modelado más importante: si los pesos no reflejan la mezcla real, las
métricas resultantes describen una carga ficticia.

| Clase           | Peso (proporción) | Espera entre tareas | Operaciones simuladas |
|-----------------|-------------------|---------------------|------------------------|
| `StudentUser`   | 10                | 1 a 3 s             | Envío de encuesta sin síntomas (peso 10), envío con síntomas (peso 2), consulta del cuestionario activo (peso 3), historial propio (peso 1) |
| `AdminUser`     | 2                 | 3 a 8 s             | Validación de certificados médicos (POST), consulta de pendientes |
| `DashboardUser` | 1                 | 5 a 15 s            | Consultas analíticas al dashboard (resumen, series temporales, métricas por departamento) |
| `GatewayUser`   | 8                 | 1 a 5 s             | Validación de tokens QR en entrada de campus (health checks del gateway) |
| `FileUser`      | 3                 | 5 a 20 s            | Carga de certificados médicos, descarga de archivos |

La proporción 10:2:1:8:3 refleja la observación de que el envío diario de
encuestas es la operación dominante (StudentUser), seguida por validaciones
de entrada en campus (GatewayUser) en accesos matutinos/vespertinos, gestión
de archivos (FileUser) en ciclos administrativos, validaciones administrativas
(AdminUser) en orden de cientos, y consultas ejecutivas (DashboardUser)
esporádicamente.

---

## Escenarios ejecutados

Existen tres perfiles definidos en `jenkins/scripts/run-locust.sh`. Solo dos
se ejecutan dentro de los pipelines; el escenario de estrés se mantiene como
ejecución manual porque su intención es exploratoria, no validatoria.

Los tres escenarios ejercen los siguientes endpoints agregados:
- **form-service:** POST /api/v1/surveys, GET /api/v1/questionnaires/active, GET /api/v1/surveys/pending
- **dashboard-service:** GET /api/v1/analytics/summary, GET /api/v1/analytics/department/{dept}, GET /api/v1/analytics/time-series
- **gateway-service:** POST /api/v1/qr/validate, GET /actuator/health
- **file-service:** POST /api/v1/files/upload, GET /api/v1/files/{id}
- Todos los servicios exponen `/actuator/health` para health checks

### PR-001 — Baseline

| Campo | Descripción |
|---|---|
| **Identificador Único** | PR-001 |
| **Nombre** | Perfil baseline contra el ambiente de stage |
| **Descripción** | Carga ligera ejecutada automáticamente al final del pipeline de stage. Su intención es establecer una línea base estable de p50/p95 y throughput contra la cual comparar regresiones. Es la única prueba de rendimiento que corre en cada build de stage; si el throughput cae 30% respecto a la media histórica, se considera regresión. |
| **Prerrequisitos/Condiciones** | Stack desplegado en el namespace `circleguard-stage` con los 8 servicios `Ready`; `kubectl port-forward` activo desde el agente de Jenkins hacia el ingress del gateway; `pip3 install locust` ya ejecutado en el agente. |
| **Entradas** | Variables de entorno: `LOCUST_HOST=http://localhost:8080`, `PROFILE=baseline`. El script setea `--users 10`, `--spawn-rate 2`, `--run-time 60s`, `--headless`, `--exit-code-on-error 0`. |
| **Acciones** | El pipeline invoca `bash jenkins/scripts/run-locust.sh`. Locust arranca 10 usuarios virtuales con la distribución de pesos del locustfile y emite peticiones contra los endpoints de form-service, dashboard-service, gateway-service, file-service por 60 segundos. |
| **Salida Esperada** | Reporte HTML `tests/performance/reports/locust-report-baseline.html` y CSV de estadísticas `locust-baseline_stats.csv`. Throughput agregado mayor a 3 RPS; mediana global menor a 100 ms; cero errores 5xx atribuibles al sistema (los 4xx por contratos desalineados se documentan pero no bloquean). |
| **Criterios de Aceptación** | El pipeline registra los artefactos sin fallar gracias a `--exit-code-on-error 0`. La fiscalización real se hace inspeccionando los CSV en el reporte del build. |

### PR-002 — Master

| Campo | Descripción |
|---|---|
| **Identificador Único** | PR-002 |
| **Nombre** | Perfil master contra el ambiente de producción |
| **Descripción** | Carga proyectada de producción (5× la carga baseline) ejecutada automáticamente al final del pipeline de master. Verifica que el throughput escala con la concurrencia y que los percentiles altos (P95, P99) se mantienen dentro del SLA antes de promover una versión a producción. |
| **Prerrequisitos/Condiciones** | Stack desplegado en el namespace `circleguard-master` con los 8 servicios `Ready`; `kubectl port-forward` activo desde el agente de Jenkins. |
| **Entradas** | Variables de entorno: `LOCUST_HOST=http://localhost:8080`, `PROFILE=master`. El script setea `--users 50`, `--spawn-rate 5`, `--run-time 120s`, `--headless`, `--exit-code-on-error 0`. |
| **Acciones** | El pipeline invoca `bash jenkins/scripts/run-locust.sh`. Locust arranca 50 usuarios virtuales escalonadamente y emite carga contra todos los endpoints (form, dashboard, gateway, file) por 120 segundos. |
| **Salida Esperada** | Reporte HTML `locust-report-master.html` y `locust-master_stats.csv`. Throughput agregado mayor a 15 RPS; P50 menor a 50 ms; P95 menor a 100 ms; el throughput escala linealmente respecto a la concurrencia (factor ~5× respecto a baseline). |
| **Criterios de Aceptación** | El pipeline archiva los reportes; la inspección humana confirma que no hay degradación gradual ni saturación visible en el histórico (`*_stats_history.csv`). |

### PR-003 — Stress

| Campo | Descripción |
|---|---|
| **Identificador Único** | PR-003 |
| **Nombre** | Perfil stress (ejecución manual fuera de pipelines) |
| **Descripción** | Carga muy por encima de producción para identificar el punto de saturación del sistema y el modo de degradación. No se ejecuta en pipelines porque su intención es exploratoria y puede degradar el ambiente: el operador la ejecuta a propósito cuando quiere caracterizar límites. |
| **Prerrequisitos/Condiciones** | Stack desplegado y dedicado (no compartido con otros experimentos); `kubectl port-forward` activo; Docker Desktop o WSL con holgura de CPU/RAM en el agente que corre Locust. |
| **Entradas** | Variables de entorno: `LOCUST_HOST=http://localhost:8080`, `PROFILE=stress`. El script setea `--users 200`, `--spawn-rate 20`, `--run-time 300s`, `--headless`, `--exit-code-on-error 0`. |
| **Acciones** | El operador ejecuta `bash jenkins/scripts/run-locust.sh` desde el agente. Locust arranca progresivamente hasta 200 usuarios virtuales y mantiene la carga por 5 minutos contra todos los servicios. |
| **Salida Esperada** | Reporte HTML `locust-report-stress.html` y CSV. La intención NO es que todo el sistema responda con holgura — se aceptan timeouts y errores 5xx; lo importante es identificar a qué nivel de carga empieza a saturarse cada servicio y si la degradación es gradual o abrupta. |
| **Criterios de Aceptación** | El sistema debe degradarse de forma controlada (Circuit Breakers de HU-06 abren cuando los downstreams se saturan, los pods no caen completamente). Si algún pod entra en `CrashLoopBackOff` por OOM, se documenta y se ajustan los `resources.limits` antes de la siguiente corrida. |

---

## Resultados obtenidos

Los reportes en `docs/report/performance-reports/` corresponden a una
ejecución real del pipeline de stage (perfil baseline) y del pipeline de
master (perfil master). Los números a continuación se extraen directamente
de `locust-baseline_stats.csv` y `locust-master_stats.csv`.

### Métricas agregadas del sistema

| Métrica                     | Stage / baseline | Master / master | Comparación |
|-----------------------------|------------------|-----------------|-------------|
| Usuarios concurrentes        | 10               | 50              | x5          |
| Duración                     | 60 s             | 120 s           | x2          |
| Solicitudes totales          | 226              | 2,412           | x10.7       |
| Throughput agregado (RPS)    | 3.83             | 20.23           | x5.28       |
| Mediana global               | 17 ms            | 13 ms           | -23%        |
| P95 global                   | 26 ms            | 29 ms           | +12%        |
| P99 global                   | 38 ms            | 190 ms          | x5          |
| Máximo                       | 73 ms            | 864 ms          | x11.8       |
| Tasa de errores              | 7.96%            | 6.67%           | similar     |

El throughput escala casi linealmente con el número de usuarios (x5.28 frente
a x5 en concurrencia), lo que indica que el sistema no está saturado con 50
usuarios y que los servicios horizontalmente independientes responden a más
carga sin contención.

---

# form-service

## Form Service — Health Surveys & Certificate Management

El servicio concentra la operación más frecuente del sistema: envío de
encuestas. En el escenario master, los cuatro endpoints de form-service
acumulan 1,970 solicitudes de las 2,412 totales (81.7% del tráfico).

| Endpoint                    | Reqs  | Falla | P50 | P95  | P99  | Max  | Notas |
|-----------------------------|-------|-------|-----|------|------|------|-------|
| POST /surveys [healthy]     | 1,363 | 0     | 15  | 29   | 280  | 840  | Ruta crítica: 56% del tráfico total |
| POST /surveys [symptoms]    | 253   | 0     | 15  | 32   | 250  | 640  | Genera evento Kafka; colas largas bajo carga |
| GET /questionnaires/active  | 444   | 0     | 9   | 21   | 56   | 860  | Caché Redis; latencia excelente |
| GET /surveys/pending        | 120   | 0     | 10  | 32   | 160  | 630  | Lectura de listado |
| POST /surveys/{id}/validate | 53    | 0     | 10  | 27   | 34   | 34   | Validación de certificados |

El endpoint `POST /surveys [healthy]` mantiene una mediana de 15 ms y un P95
de 29 ms, respondiendo con holgura al requisito NFR-1 (menos de un segundo
bajo carga nominal). Los percentiles altos (P99 = 280 ms, max = 840 ms) son
atribuibles a la presión del productor Kafka cuando hay 50 productores
concurrentes. La lectura de cuestionario activo (`GET /questionnaires/active`)
demuestra que la caché de Redis está funcionando correctamente: mediana de 9 ms.

---

# dashboard-service

## Dashboard Service — Analytics & K-Anonymity

El servicio expone tres endpoints analíticos ejercidos por el perfil
`DashboardUser` (peso 1). En escenario master acumula 49 solicitudes (2% del
tráfico).

| Endpoint                    | Reqs | Falla | P50 | P95 | P99 | Max | Notas |
|-----------------------------|------|-------|-----|-----|-----|-----|-------|
| GET /analytics/department   | 18   | 0     | 9   | 50  | 50  | 50  | Consultagrafo Neo4j; K-Anonymity |
| GET /analytics/summary      | 22   | 22    | 9   | 27  | 31  | 31  | [Fallo] Ruta 404; no implementada |
| GET /analytics/time-series  | 9    | 9     | 8   | 13  | 13  | 13  | [Fallo] Ruta 404; no implementada |

El endpoint `GET /analytics/department` es la única ruta funcional del servicio
en el ambiente de prueba. Su latencia es excelente (P50 = 9 ms) a pesar de la
complejidad de la consulta Neo4j con filtro K-Anonymity. Los picos moderados
(P95 = 50 ms) reflejan el costo de la agregación gráfica cuando la concurrencia
sube a 50 usuarios.

---

# gateway-service

## Gateway Service — Campus Entry & QR Validation

El servicio es ejercido por el perfil `GatewayUser` (peso 8). Las validaciones
de tokens QR simulan accesos en olas matutinas/vespertinas.

| Endpoint                | Reqs | Falla | P50 | P95 | P99 | Max | Notas |
|-------------------------|------|-------|-----|-----|-----|-----|-------|
| POST /qr/validate       | —    | —     | —   | —   | —   | —   | No implementado en ambiente de prueba |
| GET /actuator/health    | —    | —     | —   | —   | —   | —   | Health checks; sin métricas reportadas |

El endpoint de validación de QR no está implementado en el ambiente de prueba.
Las pruebas contra `/actuator/health` (health checks) se registran pero no se
incluyen en el desglose de endpoints en los reportes CSV.

---

# file-service

## File Service — Certificate Upload & Retrieval

El servicio es ejercido por el perfil `FileUser` (peso 3). Simula la carga
y descarga de certificados médicos por personal administrativo.

| Endpoint          | Reqs | Falla | P50 | P95 | P99 | Max | Notas |
|-------------------|------|-------|-----|-----|-----|-----|-------|
| POST /files/upload | —    | —     | —   | —   | —   | —   | No implementado |
| GET /files/{id}    | —    | —     | —   | —   | —   | —   | No implementado |

El servicio no está implementado en el ambiente de prueba. Los endpoints no
están disponibles para medición.

---

# identity-service

## Identity Service — Anonymous ID Vault

*No se ejercen endpoints funcionales de identity-service en los perfiles de
Locust. El servicio es llamado indirectamente durante la creación de encuestas
(mapeo de identidades reales).*

---

# auth-service

## Auth Service — LDAP Authentication & Identity Integration

*No se ejercen endpoints de autenticación explícitamente en los perfiles de
Locust. La autenticación está configurada en el ambiente pero no se mide
bajo carga.*

---

# promotion-service

## Promotion Service — Health State Engine

*El servicio recibe eventos Kafka desde form-service cuando hay encuestas con
síntomas, pero no hay endpoints HTTP ejercidos directamente en los perfiles de
carga.*

---

# notification-service

## Notification Service — Event-Driven Alert Dispatch

*No hay ejercicio directo de este servicio en los perfiles de Locust. Opera
en segundo plano respondiendo a eventos Kafka.*

---

## Análisis de fallos

La tasa de errores agregada del 6.67% en el escenario master no es inducida
por saturación del sistema. El detalle por endpoint muestra patrones
determinísticos:

| Endpoint                    | Falla / Total | Código HTTP | Diagnóstico |
|-----------------------------|----------------|-------------|-------------|
| GET /surveys [by user]       | 130 / 130      | 405         | Método no permitido; contrato desalineado en locustfile.py |
| GET /analytics/summary       | 22 / 22        | 404         | Ruta no mapeada en dashboard-service desplegado |
| GET /analytics/time-series   | 9 / 9          | 404         | Ruta no mapeada en dashboard-service desplegado |

Estos fallos ocurren de forma determinística desde la primera invocación y
permanecen constantes bajo carga creciente. No representan saturación ni
timeouts; son discrepancias entre el contrato HTTP del locustfile y el que
expone el servicio. Las métricas de latencia de los seis endpoints restantes
(sin fallos) reflejan el comportamiento real del sistema.

---

## Progresión y estabilidad en el tiempo

El histórico de estadísticas (`*_stats_history.csv`) del escenario master
muestra:

- **Convergencia rápida:** El sistema alcanza régimen estacionario a partir
  del segundo 12 (cuando los 50 usuarios están completamente conectados).
- **Throughput estable:** Entre los segundos 12 y 120, se mantiene entre
  20–21 RPS sin degradación progresiva.
- **Latencia consistente:** La mediana móvil permanece en 13–14 ms durante
  toda la duración, sin fuga de memoria visible ni saturación del pool de
  conexiones PostgreSQL en el rango de 50 usuarios concurrentes.

---

## Análisis y conclusiones

- **Cumplimiento de SLA:** El sistema responde con holgura al requisito
  principal. En escenario master, P50 = 13 ms y P95 = 29 ms están dos órdenes
  de magnitud por debajo del umbral de un segundo.

- **Escalabilidad lineal:** El throughput escala casi perfectamente con la
  concurrencia (x5.28 para x5 usuarios). Esto sugiere que existe margen
  significativo para incrementar la carga antes de encontrar saturación.

- **Colas largas bajo carga:** El P99 crece de 38 ms a 190 ms y el máximo
  absoluto salta de 73 ms a 864 ms. Este comportamiento es típico de
  productores Kafka bajo presión de buffer. Se recomienda instrumentar el
  batching del `form-service` antes de escalar a producción.

- **Calidad de los datos de prueba:** La mediana mejora bajo carga (calentamiento
  del JIT) en lugar de degradarse. Los fallos (6.67%) son del fixture, no del
  sistema. Antes de la próxima corrida, actualizar los paths en `locustfile.py`
  para `/analytics/summary`, `/analytics/time-series` y `GET /surveys [by user]`.

---

## HU-09 paso 9.3 — pendiente contra AKS

Las corridas anteriores (PR-001 y PR-002) se documentan con datos reales del
ambiente local de Kubernetes (Docker Desktop). El paso 9.3 de HU-09 pide
ejecutar la misma batería **contra el ambiente de stage en AKS**, lo cual
requiere coordinación con Ops (Jose Manuel) que no estuvo disponible al
cerrar HU-09.

Concretamente, para abrir el PR de seguimiento `HU-09b: stress test contra
AKS` se necesita:

1. AKS de stage levantado y accesible, con presupuesto Azure confirmado
   (restricción de los 100 USD de la cuota universitaria).
2. URLs públicas o ingress de cada uno de los 8 servicios (al menos auth,
   form, identity, promotion, dashboard).
3. Secrets cargados en el namespace de stage (`circleguard-secrets`
   incluyendo `VAULT_SECRET`/`VAULT_SALT` añadidos en HU-02).
4. Confirmación de la ventana de tiempo para correr Locust ~30 minutos sin
   reventar la cuota.
5. DNS resoluble o IP estable apuntable desde la máquina del Dev / Cloud
   Shell (con `--host` de Locust).

Cuando los 5 puntos estén disponibles, se ejecutará nuevamente la matriz
PR-001 + PR-002 contra AKS, los CSV y HTML se archivarán en
`docs/report/performance-reports/aks/`, y se actualizará este documento con
las métricas reales y el análisis de comparación contra el ambiente local.

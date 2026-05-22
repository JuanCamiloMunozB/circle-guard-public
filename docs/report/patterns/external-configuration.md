# Patrón: External Configuration Store

> Estado: implementado en Nivel 1 (Dev) + parcialmente en Nivel 2 (Ops).
> Alcance: los 8 microservicios de CircleGuard. Historia de Usuario: HU-02.

## 1. Problema que resuelve

Antes de aplicar el patrón, los `application.yml` de cada servicio contenían valores
fijos para URLs de bases de datos, credenciales, secretos JWT, brokers Kafka, hosts de
Redis/Neo4j/LDAP/SMTP. Esto producía tres clases de daño:

1. **Configuración acoplada al binario.** Cambiar de entorno (local → AKS) exigía
   recompilar la imagen. Violaba el factor III de los Twelve-Factor Apps
   ("Config en el entorno, no en el código").
2. **Riesgo de fuga de secretos.** Contraseñas, secretos JWT y claves QR vivían en
   ficheros versionados. Cualquier copia del repo era un compromiso permanente del
   material criptográfico, incluso después de rotarlo.
3. **Misma imagen, comportamiento incierto.** Sin separación entre config y código,
   no podíamos asegurar que la imagen `:v1.0.0` probada en stage fuera idéntica a la
   de producción — había una recompilación por entorno.

## 2. Cómo se implementó

CircleGuard sigue una estrategia híbrida que aprovecha las capacidades de Spring para
minimizar el ruido en los `application.yml`. Hay dos mecanismos complementarios y un
único store central en Kubernetes.

### 2.1 Mecanismo primario — Spring Relaxed Binding

Spring Boot mapea automáticamente variables de entorno con nombre canónico (mayúsculas
y guiones bajos) a propiedades YAML equivalentes:

| Variable de entorno | Propiedad Spring |
|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `spring.kafka.bootstrap-servers` |
| `SPRING_NEO4J_URI` | `spring.neo4j.uri` |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` |
| `SPRING_LDAP_URLS` | `spring.ldap.urls` |
| `SPRING_MAIL_HOST` | `spring.mail.host` |
| … | … |

Para estas propiedades **NO se necesita `${VAR:default}`** en el YAML. Basta con
escribir el valor de desarrollo local plano; Spring lo usa si no hay env var, o lo
sobreescribe si la env var existe:

```yaml
# auth-service/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/circleguard_auth   # SPRING_DATASOURCE_URL pisa esto
    username: admin                                            # SPRING_DATASOURCE_USERNAME pisa esto
    password: dev-only-not-for-prod                            # SPRING_DATASOURCE_PASSWORD pisa esto
```

Beneficio: el YAML queda legible, sin ruido de `${...}` redundantes, y el contrato
con Ops es la **convención de nombres de Spring**, no nombres inventados por Dev.

### 2.2 Mecanismo secundario — Placeholders explícitos para claves no-spring

Las propiedades fuera del prefijo `spring.*` (las definidas por la aplicación, p. ej.
`jwt.secret`, `vault.hash-salt`, `qr.secret`, `auth.api.url`) **no son alcanzadas por
relaxed binding**. Para ellas se usa la sintaxis Spring `${VAR:default}` con un nombre
de variable que coincida exactamente con el del store central:

```yaml
jwt:
  secret: ${JWT_SECRET:dev-only-jwt-secret-do-not-use-in-prod-32chars}
  expiration: ${JWT_EXPIRATION:3600000}

qr:
  secret: ${QR_SECRET:dev-only-qr-secret-do-not-use-in-prod-32chars}
  expiration: ${QR_EXPIRATION:300}
```

Beneficio: aún se exteriorizan, sin perder la capacidad de arrancar en laptop sin
configurar nada.

### 2.3 Store central único — un ConfigMap + un Secret por namespace

El plan §2 fija una convención del repo que NO se viola: existe **un solo**
`k8s/configmap.yaml` que define dos recursos compartidos:

- `ConfigMap circleguard-config` — todas las variables NO sensibles (URLs internas
  de Kafka/Redis/Neo4j/LDAP/SMTP, hosts, puertos, URLs inter-servicio, expirations,
  el flag `MANAGEMENT_HEALTH_PROBES_ENABLED`, etc.).
- `Secret circleguard-secrets` — contraseñas DB/LDAP/Neo4j, `JWT_SECRET`, `QR_SECRET`.

**No se crea un ConfigMap ni un Secret por servicio.** Los 8 Deployments referencian
los dos recursos compartidos:

```yaml
# k8s/deployments/auth-service.yaml (extracto)
envFrom:
  - configMapRef:
      name: circleguard-config
  - secretRef:
      name: circleguard-secrets
```

### 2.4 Override per-deployment — solo `SPRING_DATASOURCE_URL`

La única variable que difiere entre servicios (porque cada uno tiene su propia base
de datos en Postgres) se declara como `env` local en el Deployment del servicio:

```yaml
env:
  - name: SPRING_DATASOURCE_URL
    value: jdbc:postgresql://postgres:5432/circleguard_auth
```

Esto evita que el ConfigMap compartido tenga 8 entradas redundantes
(`SPRING_DATASOURCE_URL_AUTH`, etc.) y mantiene la convención de nombre canónico.

## 3. Inventario de variables consumidas por servicio

Nombres exactamente como aparecen en `k8s/configmap.yaml`/`circleguard-secrets` y en
los `application.yml` (vía relaxed binding o vía `${VAR:default}`).

### auth-service
- Vía relaxed binding: `SPRING_DATASOURCE_URL` (env local), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_LDAP_URLS`, `SPRING_LDAP_BASE`, `SPRING_LDAP_USERNAME`, `SPRING_LDAP_PASSWORD`.
- Vía `${}`: `JWT_SECRET`, `JWT_EXPIRATION`, `QR_SECRET`, `QR_EXPIRATION`.

### identity-service
- Vía relaxed binding: `SPRING_DATASOURCE_URL` (env local), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Vía `${}`: `VAULT_SECRET` ⚠ (pendiente en Secret), `VAULT_SALT` ⚠ (pendiente en Secret), `VAULT_HASH_SALT`, `JWT_SECRET`.

### form-service
- Vía relaxed binding: `SPRING_DATASOURCE_URL` (env local), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`.

### dashboard-service
- Vía relaxed binding: `SPRING_DATASOURCE_URL` (env local), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

### promotion-service
- Vía relaxed binding: `SPRING_DATASOURCE_URL` (env local), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_NEO4J_URI`, `SPRING_NEO4J_AUTHENTICATION_USERNAME`, `SPRING_NEO4J_AUTHENTICATION_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`.
- Vía `${}`: `JWT_SECRET`, `JWT_EXPIRATION`.

### notification-service
- Vía relaxed binding: `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`.
- Vía `${}`: `AUTH_API_URL`, `JWT_SECRET`, `JWT_EXPIRATION`, `QR_SECRET`, `QR_EXPIRATION`.

### gateway-service
- Vía relaxed binding: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`.
- Vía `${}`: `JWT_SECRET`, `JWT_EXPIRATION`, `QR_SECRET`, `QR_EXPIRATION`.

### file-service
- Sin dependencias externas hoy (placeholder S3/MinIO comentado).

## 4. Beneficio medible

- **Una sola imagen Docker por versión, N entornos.** La misma imagen
  `docker.io/circleguard/auth-service:v1.0.0` corre en dev, stage y prod cambiando
  solo el ConfigMap/Secret del namespace.
- **Sin secretos productivos en Git.** Los valores reales nunca entran al
  repositorio. Los defaults en YAML usan el prefijo `dev-only-…-not-for-prod` que
  funciona como red flag si aparece en logs de stage o prod.
- **Convención de nombres alineada con Spring.** El contrato Dev↔Ops es la
  documentación oficial de Spring, no nombres inventados; reduce errores de
  tipeo y onboarding.
- **Rotación sin redeploy de código.** Cambiar un `JWT_SECRET` es
  `kubectl edit secret circleguard-secrets` + `kubectl rollout restart`. No
  interviene Gradle ni Jenkins.
- **12-Factor compliance** en los factores III (Config) y XII (Admin processes).

## 5. Anti-patrones evitados

- **`${VAR:default}` redundante en propiedades `spring.*`.** Cuando relaxed binding ya
  resuelve el override, añadir un placeholder es ruido visual y duplica el contrato.
- **Un ConfigMap/Secret por servicio.** Habría multiplicado el mantenimiento (8 vs 1)
  y forzado a duplicar las claves comunes (`JWT_SECRET`, `KAFKA_BOOTSTRAP_SERVERS`,
  etc.) sin ganancia de aislamiento real (todos los servicios viven en el mismo
  namespace de cada entorno).
- **Profile-based hardcoding** (`application-prod.yml` con valores reales). Habría
  arrastrado secretos al repo.
- **`@Value` directo desde Java** con default literal. Duplica defaults entre YAML y
  código.

## 6. Validación

- **Local:** `./gradlew :services:<servicio>:bootRun` arranca con los defaults. Los
  logs muestran `Tomcat initialized with port NNNN` (resolución exitosa de
  `server.port`) y `Connection to localhost:5432 refused` (resolución exitosa del
  default `SPRING_DATASOURCE_URL`). La falla de conexión es esperada cuando no hay
  dependencias locales corriendo.
- **Local con override:**
  `SPRING_DATASOURCE_URL=jdbc:postgresql://otra-bd:5432/x ./gradlew :services:<svc>:bootRun`
  arranca con la URL inyectada — los logs lo confirman (`Connection to otra-bd:5432
  refused`).
- **K8s (pendiente, Ops + Fase 4):** `kubectl describe pod <svc>` debe mostrar
  `envFrom: configMapRef/circleguard-config + secretRef/circleguard-secrets` y los
  pods deben quedar `Ready`. Validación end-to-end ocurre en Fase 4 (HU-04) cuando
  todos los Deployments estén desplegados.

## 7. Pendiente para Ops (HU-02 lado Ops)

El store central ya existe y los 6 Deployments más antiguos lo consumen. Quedan tres
ítems puntuales pendientes:

1. **Agregar 2 claves al Secret `circleguard-secrets`:** `VAULT_SECRET` y `VAULT_SALT`,
   actualmente requeridas por `identity-service/application.yml`. El plan §2.3 obliga
   a Dev a anotarlas y comunicarlas, no a crearlas; aquí quedan documentadas.
2. **Crear Deployments para `gateway-service` y `file-service`** (no existen hoy en
   `k8s/deployments/`). Alcance natural de **Fase 4 (HU-04)**, no de esta fase. Cuando
   se haga, deben seguir el mismo patrón `envFrom: configMapRef + secretRef` y
   declarar `SPRING_DATASOURCE_URL` como env local únicamente si el servicio usa
   Postgres (gateway y file no lo usan hoy, así que probablemente no haga falta).
3. **Validación end-to-end en AKS** de que los 8 servicios arrancan consumiendo el
   store central. Parte de Fase 4.

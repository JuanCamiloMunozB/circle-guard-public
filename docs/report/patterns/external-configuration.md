# Patrón: External Configuration Store

> Estado: implementado en Nivel 1 (variables de entorno con defaults locales).
> Alcance: los 8 microservicios de CircleGuard. Historia de Usuario: HU-02.

## 1. Problema que resuelve

Antes de aplicar el patrón, los `application.yml` de cada servicio contenían valores
fijos para URLs de bases de datos, credenciales, secretos JWT, brokers Kafka, hosts de
Redis/Neo4j/LDAP/SMTP, puertos HTTP. Esto producía tres clases de daños:

1. **Configuración acoplada al binario.** Cambiar de entorno (local → AKS) exigía
   recompilar la imagen para alterar una URL. Violaba el factor III de los Twelve-Factor
   Apps ("Config en el entorno, no en el código").
2. **Riesgo de fuga de secretos.** Contraseñas, secretos JWT y claves QR vivían en
   ficheros versionados en Git público. Cualquier copia del repo era un compromiso
   permanente del material criptográfico, incluso después de rotarlo.
3. **Misma imagen, comportamiento incierto.** Sin separación entre config y código, no
   podíamos asegurar que la imagen `:v1.0.0` probada en stage fuera idéntica a la de
   producción — había una rebuild por entorno.

## 2. Cómo se implementó

### Nivel 1 — Placeholders Spring con default local

Cada valor sensible o dependiente de entorno en `src/main/resources/application.yml` se
sustituyó por la sintaxis `${VAR:default}` de Spring Framework. Ejemplo en
`auth-service`:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/circleguard_auth}
    username: ${DB_USER:admin}
    password: ${DB_PASSWORD:password}
  ldap:
    urls: ${LDAP_URL:ldap://localhost:389}
    password: ${LDAP_PASSWORD:admin}

jwt:
  secret: ${JWT_SECRET:my-super-secret-dev-key-32-chars-long-12345678}
```

Comportamiento en tiempo de arranque:

- **En la laptop del desarrollador** (sin env vars exportadas) Spring resuelve cada
  placeholder con su default. La app arranca contra `localhost` y credenciales triviales
  útiles para desarrollo. Equivale al comportamiento anterior, sin recompilar.
- **En Kubernetes** los Deployments montan `envFrom: configMapRef` (no sensibles) y
  `envFrom: secretRef` (sensibles). Spring detecta las env vars y sobreescribe los
  defaults — sin tocar la imagen Docker.

### Nivel 2 — ConfigMap + Secret en Kubernetes

**No incluido en este commit.** Es responsabilidad de Ops (Jose Manuel) crear
`k8s/config/configmap-<servicio>.yaml` (URLs, hosts, puertos, nombres de tópicos Kafka)
y `k8s/config/secret-<servicio>.yaml` (contraseñas, claves JWT/QR). Los manifiestos
deben referenciar exactamente los nombres de variables documentados en la tabla de la
sección 3.

### Nivel 3 — Vault / Key Vault (opcional, fuera de alcance)

El sistema queda preparado para futura integración con Azure Key Vault o HashiCorp
Vault. El cambio sería transparente para el código: los manifiestos K8s solo
sustituirían `secret-<servicio>.yaml` por un `SecretProviderClass` que extrae las
mismas claves del Vault. Las apps siguen leyendo `${JWT_SECRET}` sin saberlo.

## 3. Inventario de variables externalizadas

Ver tabla detallada en el reporte de Fase 2 (HU-02). Resumen por categorías:

| Categoría | Variables |
|---|---|
| Datasource PostgreSQL | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JPA_DDL_AUTO` |
| Secretos de aplicación | `JWT_SECRET`, `JWT_EXPIRATION`, `QR_SECRET`, `QR_EXPIRATION` |
| LDAP (solo auth) | `LDAP_URL`, `LDAP_BASE`, `LDAP_USER`, `LDAP_PASSWORD` |
| Vault de identidades (solo identity) | `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT` |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_GROUP` |
| Redis | `REDIS_HOST`, `REDIS_PORT` |
| Neo4j (solo promotion) | `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD` |
| SMTP (solo notification) | `MAIL_HOST`, `MAIL_PORT` |
| Inter-servicio | `AUTH_API_URL` |
| Red | `SERVER_PORT` |

## 4. Beneficio medible

- **Una sola imagen Docker por versión, N entornos.** La misma imagen
  `docker.io/circleguard/auth-service:v1.0.0` corre en dev, stage y prod cambiando solo
  el ConfigMap/Secret del namespace. Reduce surface de bugs por divergencia de imágenes.
- **Sin secretos en Git.** Los valores reales nunca entran al repositorio. Los defaults
  en `application.yml` son intencionalmente débiles (p.ej. `password`, `admin`) — solo
  útiles para arranque local; servirían como red flag si aparecieran en logs de stage o
  prod, lo que facilita auditoría.
- **Rotación sin redeploy de código.** Cambiar un `JWT_SECRET` es `kubectl edit secret`
  + `kubectl rollout restart deploy/<servicio>`. No interviene Gradle ni Jenkins.
- **12-Factor compliance** explícito en el factor III (Config) y XII (Admin processes).

## 5. Anti-patrones evitados

- **Profile-based hardcoding** (`application-prod.yml` con valores reales). Habría
  arrastrado secretos de producción al repo. Rechazado.
- **`@Value` directo desde Java** con default literal. Habría duplicado defaults entre
  YAML y código. Rechazado.
- **Cliente de Vault en el código de cada servicio.** Acoplaría todos los servicios a la
  implementación concreta del store. Aplazado a Nivel 3.

## 6. Validación

- Local: `./gradlew :services:<servicio>:bootRun` arranca con los defaults y los logs
  muestran las URLs `localhost`.
- Local con override: `DB_URL=jdbc:postgresql://otra-bd:5432/x ./gradlew :services:<servicio>:bootRun`
  arranca con la URL inyectada; los logs lo confirman.
- K8s (pendiente, Ops): `kubectl describe pod` debe mostrar `envFrom` apuntando a los
  ConfigMap/Secret del servicio y los pods deben quedar `Ready` consumiendo esos
  valores. Validación completa en AKS depende del trabajo paralelo de Ops.

# Change Management

## 1. Objetivo

Definir cómo promover, revertir y auditar cambios en CircleGuard sin perder trazabilidad ni introducir interrupciones mayores que las necesarias.

## 2. Alcance

Aplica a despliegues en `dev`, `stage` y `prod`, a toggles funcionales y a rollbacks de imagen o base de datos.

## 3. Procedimiento general

1. Validar el cambio en rama `feature/*` con pruebas verdes.
2. Fusionar a `develop` o a la rama de release correspondiente.
3. Desplegar primero en `stage`.
4. Verificar salud, logs y comportamiento funcional.
5. Promover a `prod` solo después de aprobación manual.

## 4. Rollback por Feature Toggle

Para desactivar una funcionalidad sin revertir código:

1. Cambiar `FEATURE_SMS_ALERTS_ENABLED=true` a `false` en el entorno o secret de configuración.
2. Reiniciar únicamente `notification-service`.
3. Verificar que `smsService.sendAsync(...)` ya no se ejecuta.

Este es el rollback más rápido porque no toca la imagen Docker ni el despliegue completo.

## 5. Rollback de imagen Docker

Si la imagen nueva introduce un problema:

1. Volver a la etiqueta anterior en el Deployment.
2. Ejecutar `kubectl rollout restart deployment/notification-service`.
3. Confirmar el estado con `kubectl rollout status`.

## 6. Rollback de base de datos

Cuando exista una migración incompatible:

1. Restaurar el backup anterior o revertir la migración con la herramienta aplicada por el servicio.
2. Validar integridad funcional en `stage`.
3. Repetir el despliegue de la versión que corresponde al esquema restaurado.

## 7. Escalación

- Desarrollo: Juan Camilo Muñoz
- Operaciones: Jose Manuel Cardona
- Revisión funcional: líder del servicio afectado

## 8. Evidencia esperada

- Logs del despliegue y del rollback.
- Resultado de pruebas posteriores al rollback.
- Confirmación de la versión activa en Kubernetes.
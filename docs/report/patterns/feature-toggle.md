# Patrón: Feature Toggle

> Estado: implementado en `notification-service` con la flag `FEATURE_SMS_ALERTS_ENABLED`, expuesta también en el `ConfigMap` compartido `circleguard-config`.
> Alcance: el despacho de SMS dentro de `NotificationDispatcher`.

## 1. Problema que resuelve

No todas las capacidades de un sistema deben quedar siempre activas. Algunas tienen costo por uso, riesgo operativo o necesidad de despliegue gradual. En CircleGuard, el canal SMS cumple esas tres condiciones: cada mensaje cuesta dinero, depende de integración externa y no siempre debe estar encendido.

Sin un feature toggle, habilitar o deshabilitar SMS exigiría editar código, recompilar y generar una nueva imagen. Eso rompe la idea de externalizar configuración y vuelve el cambio lento y riesgoso.

## 2. Cómo se implementó

La flag se expone en `services/circleguard-notification-service/src/main/resources/application.yml` como:

```yaml
feature:
  sms-alerts:
    enabled: ${FEATURE_SMS_ALERTS_ENABLED:false}
```

Spring resuelve `FEATURE_SMS_ALERTS_ENABLED` desde el entorno. En Kubernetes, el valor se publica en `k8s/configmap.yaml` dentro de `circleguard-config`, y si la variable no existe el servicio arranca con `false` por defecto, dejando el canal SMS deshabilitado.

La lectura tipada se hace con `FeatureToggleProperties`, una clase de configuración enlazada con `@ConfigurationProperties(prefix = "feature")`. `NotificationDispatcher` consulta esa propiedad antes de invocar `smsService.sendAsync(...)`.

## 3. Comportamiento

- `FEATURE_SMS_ALERTS_ENABLED=false`: el dispatcher omite el envío SMS y registra que el canal quedó desactivado por flag.
- `FEATURE_SMS_ALERTS_ENABLED=true`: el dispatcher construye el contenido SMS y ejecuta el envío normal.

La diferencia no requiere recompilación. Basta con cambiar la variable de entorno y reiniciar el servicio.

## 4. Beneficio

- Reduce costo operativo cuando no se necesita SMS.
- Permite activar el canal gradualmente en stage o producción.
- Evita despliegues de código para un cambio puramente funcional.
- Mantiene el binario único para todos los entornos.

## 5. Validación

La prueba `NotificationDispatcherToggleTest` cubre ambos escenarios:

- con la flag desactivada, `smsService` nunca se invoca;
- con la flag activada, `smsService.sendAsync(...)` sí se ejecuta.

Eso demuestra que el comportamiento cambia por configuración y no por recompilación.
# Patrón: Publisher-Subscriber

> Estado: presente en la arquitectura actual de CircleGuard mediante Apache Kafka.
> Alcance: flujo de eventos de salud, validación y notificación entre microservicios.

## 1. Propósito

El patrón Publisher-Subscriber desacopla a los productores de eventos de los consumidores. Cada microservicio publica un hecho del dominio en Kafka y otros servicios reaccionan de forma asíncrona. Eso evita llamadas directas entre todos los participantes y reduce el acoplamiento temporal.

## 2. Tópicos principales

CircleGuard usa estos 5 tópicos de negocio como núcleo del flujo actual:

| Tópico | Productor | Consumidor(es) |
|---|---|---|
| `survey.submitted` | `form-service` | `promotion-service` |
| `certificate.validated` | `form-service` | `promotion-service` |
| `promotion.status.changed` | `promotion-service` | `notification-service` |
| `alert.priority` | `promotion-service` | `notification-service` |
| `circle.fenced` | `promotion-service` | `notification-service` |

## 3. Flujo

1. `form-service` publica `survey.submitted` cuando el usuario envía su encuesta.
2. `form-service` publica `certificate.validated` cuando un certificado es aprobado o rechazado.
3. `promotion-service` consume esos eventos y calcula el estado sanitario.
4. `promotion-service` publica `promotion.status.changed`, `alert.priority` y `circle.fenced` según la evolución del caso.
5. `notification-service` consume esos eventos y dispara notificaciones multicanal.

## 4. Tópicos auxiliares

Existen además eventos de soporte y auditoría, como `audit.identity.accessed` y `notification.audit`. Se omiten del diagrama principal para mantener la documentación enfocada en el flujo funcional de negocio.

## 5. Beneficios

- Desacoplamiento entre servicios productores y consumidores.
- Escalabilidad horizontal por consumidores independientes.
- Tolerancia a picos: Kafka absorbe eventos aunque un consumidor esté temporalmente lento.
- Trazabilidad del flujo sanitario y de notificación.

## 6. Diagrama

Ver [docs/diagrams/pub-sub.puml](../../diagrams/pub-sub.puml) para la vista secuencial del flujo.
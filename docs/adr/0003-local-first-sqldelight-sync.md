# 0003 · SQLDelight como fuente de verdad + sincronización propia

- **Estado:** aceptada · 2026-10-07

## Contexto
La app se usa en viajes, a menudo sin cobertura. Los subproyectos de estadísticas y presupuestos necesitan consultas agregadas rápidas. La caché offline de Firestore no es una base de datos consultable de verdad.

## Decisión
La UI lee solo de **SQLDelight**. Las escrituras son locales y se encolan en un **outbox** que se sube a Firestore; los cambios remotos bajan por listeners incrementales. Concurrencia optimista por documento con `version` encadenada, validada en las reglas: gana la primera escritura que llega al servidor y la perdedora se guarda como conflicto recuperable. La persistencia offline del SDK de Firestore se desactiva. Detalle en [SYNC.md](../SYNC.md).

## Consecuencias
- Funciona 100 % offline y los saldos y estadísticas son consultas SQL locales.
- Hay que construir y probar a fondo el motor de sincronización (`data:sync`), con un Firestore falso en tests.

## Alternativas descartadas
- **Firestore como única fuente con su caché**: mucho menos código, pero consultas limitadas y mala base para los subproyectos 4 y 5.
- **Event sourcing** (registro inmutable + proyección local): sin conflictos y con deshacer gratis, pero demasiado complejo para este dominio (migraciones de eventos, compactación).
- **Last-write-wins por `updatedAt`**: más simple, pero pierde ediciones en silencio. La versión encadenada cuesta lo mismo y no pierde nada.

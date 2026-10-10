# Roadmap

Owlsplit se construye en subproyectos independientes. Cada uno pasa por **diseño → plan → implementación** y tiene su propio documento en `docs/designs/`.

| # | Subproyecto | Estado | Depende de |
|---|---|---|---|
| 1 | **Núcleo (MVP)** | Diseño aprobado | — |
| 2 | Liquidar con un toque y recordatorios | Pendiente | 1 |
| 3 | Gastos recurrentes | Hecho (P13b) | 1 |
| 4 | Presupuestos, estadísticas y exportación | Pendiente | 1 |
| 5 | Escaneo de tickets | Pendiente | 1 (mejor tras 4) |

## 1. Núcleo (MVP)

Cuentas anónimas vinculables, grupos, miembros con pesos, gastos con varios pagadores y cuatro tipos de reparto, transferencias, saldos, simplificación de deudas, multidivisa con tipo congelado, invitaciones por enlace y QR, historial, sincronización offline-first. Diseño: [designs/2026-10-07-owlsplit-core-design.md](designs/2026-10-07-owlsplit-core-design.md).

## 2. Liquidar con un toque y recordatorios

- Al pulsar “Saldar”, abrir **Bizum, PayPal o Revolut** con importe y concepto prerrellenados cuando la app lo permita (PayPal.me y enlaces de Revolut sí; Bizum no tiene enlace público: copiar importe y teléfono). Cada usuario configura sus métodos de cobro en su perfil.
- **Recordatorios** amables a quien debe, mediante notificación push y mensaje para compartir.
- Notificaciones de actividad del grupo (nuevo gasto, te han saldado).
- Requiere **FCM** y **Cloud Functions** (plan Blaze) para enviar push a otros usuarios.

## 3. Gastos recurrentes

- Plantillas con periodicidad (semanal, mensual, anual, cada N días) que generan el gasto automáticamente: alquiler, suministros, suscripciones.
- Usa el campo `recurrenceId` reservado en el MVP.
- Decisión abierta: generación en el cliente (el primer dispositivo que abre el grupo, con id determinista para evitar duplicados) o en el servidor (Cloud Functions programadas).

## 4. Presupuestos, estadísticas y exportación

- Presupuesto por grupo o viaje y por categoría, con avance y aviso al acercarse.
- Gráficas: gasto por persona, por categoría y por día; media diaria del viaje.
- Exportación a **CSV** y **PDF** (resumen y saldos).
- Todo calculado en local sobre SQLDelight (motivo principal de [ADR 0003](adr/0003-local-first-sqldelight-sync.md)).

## 5. Escaneo de tickets

- Foto del ticket → OCR en el dispositivo (ML Kit en Android, Vision en iOS) que rellena importe, fecha y comercio.
- Reparto **línea a línea**: asignar cada producto a una o varias personas, con impuestos y propina prorrateados.
- Adjuntar la foto al gasto (usa `receiptUrl`; requiere Cloud Storage, plan Blaze).
- Opcional: extracción con un modelo multimodal para tickets difíciles.

## Ideas sin priorizar

Widget de saldo en la pantalla de inicio, accesos directos (Siri / App Shortcuts), web ligera para invitados sin app, versión de escritorio (la plantilla ya incluye el target JVM), dominio propio para los enlaces de invitación.

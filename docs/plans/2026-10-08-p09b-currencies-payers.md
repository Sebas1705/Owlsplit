# P9b · Divisas, pagadores, fechas y pagos manuales — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Completar el dinero de P9: gastos en cualquier divisa con el tipo de cambio congelado ([3.7](../designs/2026-10-07-owlsplit-core-design.md#37-multidivisa), [DOMAIN · Divisas](../DOMAIN.md#divisas)), varios pagadores, fecha y nota del gasto, fechas del viaje, transferencias a mano y balance total de Inicio convertido a la divisa preferida.

**Fuera (P9c):** filtros y búsqueda, historial por grupo y pestaña Actividad, restaurar borrados y recuperar la versión perdedora de un conflicto.

**Architecture:**
- Puerto `FxRates` en `data:repository` (paquete `fx`); implementación nueva `data:fx` con Ktor contra Frankfurter (`/latest?base=USD&symbols=EUR`), los tipos leídos como texto (`FxRate.parse`, nunca `Double`) y caché en DataStore (`KeyValueCache` de `core:datastore`) con la caducidad del perfil (`AppConfig.FX_CACHE_SECONDS`). Si la red falla se usa el último tipo guardado, con su fecha; si no hay ninguno, el usuario lo escribe.
- `domain`: `GetFxRate`, `ConvertTotal` (Inicio) y `RecordTransfer`; `ExpenseDraft` gana `currency`, `fxRate`, `payers` (varios), `date` y `note`, con su validación (`MissingRate`, `PayersSumMismatch`).
- `core:money`: `Currency.all` para el selector.
- `core:ui`: `CurrencyPicker` (diálogo con búsqueda), `DateField` (selector de fecha de Material 3).

**Tech Stack:** Ktor 3.5.2 (ya en `core:network`), DatePicker de Material 3 (Compose Multiplatform), DataStore.

**Platform:** KMP (Android verificado en emulador; iOS compila en CI)

**Depende de:** P9a.

---

## Decisiones

1. **Tipo congelado**: se pide al elegir una divisa distinta de la del grupo y se guarda en el gasto (`fxRateScaled`). Editar un gasto conserva su tipo; solo se vuelve a pedir si cambia la divisa. El usuario puede escribir otro (`1 USD = 0,92 EUR`). Sin tipo, no se puede guardar.
2. **Fuente**: Frankfurter (BCE, sin clave). Frankfurter no cubre todas las divisas de la tabla: para esas, tipo manual.
3. **Caché**: por par de divisas, valor `tipo|fecha|guardadoEn`. Vigente durante `FX_CACHE_SECONDS` (0 en desarrollo: siempre pide); caducado solo se usa si falla la red, mostrando la fecha.
4. **Varios pagadores**: interruptor "Varios" en "Pagó". Con uno, paga el importe completo y se ajusta solo; con varios, cada importe a mano y aviso en vivo ("Faltan 5,00 € de pagadores").
5. **Fecha y nota**: la fecha por defecto es hoy; se cambia con el selector. Nota opcional (≤ 500).
6. **Fechas del viaje**: opcionales en el editor de grupo (inicio ≤ fin). Al editar un grupo ya no se pierden.
7. **Transferencia manual**: desde Saldar, "Registrar un pago": de, a (distintos), importe y nota, en la divisa del grupo, fecha de hoy. (Otra divisa en transferencias no se ofrece en el MVP.)
8. **Balance total**: los grupos en otras divisas se convierten con el último tipo disponible y el total se marca como aproximado ("≈"); si falta algún tipo, esos grupos siguen aparte.

## Tareas

### Task 1: Tipos de cambio
- [ ] `Currency.all`; `KeyValueCache` en `core:datastore`; puerto `FxRates`; módulo `data:fx` (`FrankfurterRates`) con tests (MockEngine de Ktor: respuesta normal, divisa no soportada, red caída con y sin caché, caché vigente).
- [ ] `GetFxRate`, `ConvertTotal` con tests.
- [ ] Commit.

### Task 2: Gasto completo
- [ ] `ExpenseDraft`: divisa, tipo, pagadores, fecha, nota; validación y guardado. Tests.
- [ ] Editor: chip de divisa + `CurrencyPicker`, fila de tipo de cambio, equivalente en la divisa del grupo, pagadores varios, fecha y nota. Tests de ViewModel y capturas.
- [ ] Commit.

### Task 3: Grupo y pagos
- [ ] Fechas del viaje en el editor de grupo; `RecordTransfer` y pantalla "Registrar un pago". Tests y capturas.
- [ ] Inicio con `ConvertTotal`. Tests.
- [ ] Commit.

### Task 4: App, documentación y PR
- [ ] Koin y navegación; prueba en el emulador (gasto en USD con tipo real de Frankfurter, varios pagadores, transferencia manual).
- [ ] `docs/DOMAIN.md` (caché), `MODULE_MAP.md`, `DESIGN_SYSTEM.md`, índice. `./gradlew build`. PR.

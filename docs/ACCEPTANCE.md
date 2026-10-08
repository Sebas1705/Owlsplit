# Criterios de aceptación del MVP

Los siete criterios del [spec, sección 12](designs/2026-10-07-owlsplit-core-design.md#12-criterios-de-aceptación-del-mvp), con cómo se comprueba cada uno y su estado. Se revisa antes de cada versión `vX.Y.Z` (P11d).

| # | Criterio | Cómo se comprueba | Estado (2026-10-08) |
|---|---|---|---|
| 1 | Dos dispositivos (Android + iOS) con cuentas anónimas comparten un grupo por enlace y ven los mismos gastos y saldos al céntimo | Automático: `FirestoreSyncEmulatorTest` (dos dispositivos contra los emuladores, `scripts/run-sync-emulator-test.sh`), `InviteUseCasesTest`, `InviteViewModelsTest`. Manual: invitar desde un Android, unirse desde otro con el enlace o el QR y comparar Saldos. | **Android ↔ Android verificado** (P10a/P10b en emulador, contra el antiguo `owlsplit-dev`). **iOS pendiente**: la app compila en CI (simulador), falta probarla en un iPhone (necesita Mac o TestFlight, con la cuenta de Apple). |
| 2 | Con un dispositivo en modo avión se crean, editan y borran gastos; al reconectar ambos convergen y nada se pierde | Automático: `SyncEngineTest` (outbox, subida automática, `flush`), `OutboxProcessorTest`, `FirestoreSyncEmulatorTest` (convergencia). Manual: modo avión en uno, tres operaciones, reconectar, comparar. | Cubierto por tests. **Prueba manual pendiente** en la primera build de testers (necesita Authentication en `owlsplit`). |
| 3 | Una edición concurrente del mismo gasto produce un aviso de conflicto y la versión perdedora es recuperable desde el historial | Automático: `SyncEngineTest` ("a pulled change over a pending local edit becomes a conflict"), `FirestoreSyncEmulatorTest` ("…a concurrent edit becomes one conflict"), `HistoryUseCasesTest` y `HistoryViewModelTest` (reaplicar o descartar). | **Cumplido** (P6, P9c). |
| 4 | Un gasto en USD en un grupo en EUR usa el tipo congelado y los saldos no cambian al día siguiente | Diseño: el gasto guarda `fxRateScaled` al crearse y los saldos solo leen ese valor (`ledger`); el tipo del día solo propone el valor al editar. Automático: tests de `ledger` y `FrankfurterRatesTest` (caché y tipo antiguo sin red). | **Cumplido** (P3, P9b). |
| 5 | Vincular Google conserva todos los grupos; borrar la cuenta deja sus miembros como fantasmas y no quedan datos personales | Automático: `AccountUseCasesTest` (vincular conserva el uid; borrar deja fantasmas y borra `users/{uid}`), `ProfileViewModelTest`. Manual: vincular desde Perfil y comprobar los grupos; borrar y comprobar el miembro sin cuenta desde otro dispositivo. | Cubierto por tests. **Prueba manual pendiente**: Google necesita Authentication activo en `owlsplit`. |
| 6 | Un gasto a partes iguales se añade en < 10 s desde Inicio | Manual con cronómetro: Inicio → Gasto → importe → concepto → Guardar (el reparto por defecto es a partes iguales entre todos y pagó "yo"). | Flujo de 4 toques + teclado. **Medición pendiente** en la primera build de testers. |
| 7 | Todos los tests verdes en CI, incluidos los de reglas y la compilación iOS | CI de cada PR: `detekt`, tests (207 `@Test`), migraciones, capturas, reglas en el emulador y la app iOS para el simulador. | **Cumplido** en `main` (la compilación de la app iOS completa entra con P11c). |

## Antes de la primera versión para testers

Lo que solo puede hacer el propietario:

1. Activar Authentication en `owlsplit` (Anónimo y Google) y volver a ejecutar `scripts/setup-firebase-env.sh`.
2. Para iOS: cuenta de Apple Developer (Team ID), con la que se completan Universal Links (`apple-app-site-association`), Sign in with Apple y la firma (TestFlight).

Después: pruebas manuales de los criterios 2, 5 y 6 en la build de `main` que llega por App Distribution, y anotar el resultado en esta tabla.

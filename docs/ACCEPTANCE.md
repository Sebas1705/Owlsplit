# Criterios de aceptación del MVP

Los siete criterios del [spec, sección 12](designs/2026-10-07-owlsplit-core-design.md#12-criterios-de-aceptación-del-mvp), con cómo se comprueba cada uno y su estado. Se revisa antes de cada versión `vX.Y.Z` (P11d).

| # | Criterio | Cómo se comprueba | Estado (2026-10-08) |
|---|---|---|---|
| 1 | Dos dispositivos (Android + iOS) con cuentas anónimas comparten un grupo por enlace y ven los mismos gastos y saldos al céntimo | Automático: `FirestoreSyncEmulatorTest` (dos dispositivos contra los emuladores, `scripts/run-sync-emulator-test.sh`), `InviteUseCasesTest`, `InviteViewModelsTest`. Manual: invitar desde un Android, unirse desde otro con el enlace o el QR y comparar Saldos. | **Android ↔ Android verificado contra `owlsplit`** (2026-10-08): Ana crea "Prueba" y gasto; Luis, en otra app con su cuenta anónima, abre `owlsplit.web.app/j/…`, pasa el onboarding, se une y añade "Taxi" 20 € a partes iguales; los dos ven Ana −10,00 € / Luis +10,00 €. **iOS pendiente**: la app compila y enlaza en CI; falta un iPhone (Mac o TestFlight, con la cuenta de Apple). |
| 2 | Con un dispositivo en modo avión se crean, editan y borran gastos; al reconectar ambos convergen y nada se pierde | Automático: `SyncEngineTest` (outbox, subida automática, `flush`), `OutboxProcessorTest`, `FirestoreSyncEmulatorTest` (convergencia). Manual: modo avión en uno, tres operaciones, reconectar, comparar. | **Cumplido** (2026-10-08, contra `owlsplit`): en modo avión Ana añadió "Super" 15 € y borró "Cena" y Luis añadió "Bus" 4 €; al reconectar ambos muestran los mismos tres gastos y Ana −4,50 € / Luis +4,50 €. |
| 3 | Una edición concurrente del mismo gasto produce un aviso de conflicto y la versión perdedora es recuperable desde el historial | Automático: `SyncEngineTest` ("a pulled change over a pending local edit becomes a conflict"), `FirestoreSyncEmulatorTest` ("…a concurrent edit becomes one conflict"), `HistoryUseCasesTest` y `HistoryViewModelTest` (reaplicar o descartar). | **Cumplido** (P6, P9c). |
| 4 | Un gasto en USD en un grupo en EUR usa el tipo congelado y los saldos no cambian al día siguiente | Diseño: el gasto guarda `fxRateScaled` al crearse y los saldos solo leen ese valor (`ledger`); el tipo del día solo propone el valor al editar. Automático: tests de `ledger` y `FrankfurterRatesTest` (caché y tipo antiguo sin red). | **Cumplido** (P3, P9b). |
| 5 | Vincular Google conserva todos los grupos; borrar la cuenta deja sus miembros como fantasmas y no quedan datos personales | Automático: `AccountUseCasesTest` (vincular conserva el uid; borrar deja fantasmas y borra `users/{uid}`), `ProfileViewModelTest`. Manual: vincular desde Perfil y comprobar los grupos; borrar y comprobar el miembro sin cuenta desde otro dispositivo. | **Borrar: cumplido** (2026-10-08): Luis borró su cuenta y Ana lo ve como "Sin cuenta" con sus gastos y el mismo saldo. **Vincular Google: pendiente del propietario** (iniciar sesión con una cuenta de Google real en el móvil; el agente no introduce credenciales). |
| 6 | Un gasto a partes iguales se añade en < 10 s desde Inicio | Manual con cronómetro: Inicio → Gasto → importe → concepto → Guardar (el reparto por defecto es a partes iguales entre todos y pagó "yo"). | Flujo de 4 toques + teclado. **Medición pendiente** en la primera build de testers. |
| 7 | Todos los tests verdes en CI, incluidos los de reglas y la compilación iOS | CI de cada PR: `detekt`, tests (207 `@Test`), migraciones, capturas, reglas en el emulador y la app iOS para el simulador. | **Cumplido**: desde P11c, CI compila y enlaza la app iOS completa (Swift, Firebase por SPM) en cada PR. |

## Antes de la primera versión para testers

Lo que solo puede hacer el propietario:

1. ~~Activar Authentication en `owlsplit`~~ (hecho el 2026-10-08; configuración refrescada).
2. Para iOS: cuenta de Apple Developer (Team ID), con la que se completan Universal Links (`apple-app-site-association`), Sign in with Apple y la firma (TestFlight).

Queda para el propietario: vincular Google (criterio 5) y cronometrar un gasto (criterio 6) en la build que llega por App Distribution.

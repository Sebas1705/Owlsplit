# Mapa de módulos

| Módulo | Contenido | Plan que lo amplía |
|---|---|---|
| `composeApp` | Entrada Android/iOS, DI raíz, `AppConfig` por entorno, navegación (onboarding → pestañas Inicio · Actividad · Perfil), `SyncStarter`, Google con Credential Manager | P9 |
| `core:common` | MVI base, `AppResult`, utilidades | — |
| `core:ui` | `OwlsplitTheme` (tokens, Inter, formas), `MoneyFormat`, componentes y `DesignCatalog`; capturas Roborazzi | P9 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Cliente Ktor por plataforma | P9 (tipos de cambio) |
| `core:datastore` | Preferencias (`AppPreferences`: tema, perfil local, aviso para vincular; `ThemeSettings`) | P11 (idioma) |
| `core:logging` | Kermit | — |
| `core:testing` | `CoroutineTestRule` y falsos de cuenta/sincronización para los tests de ViewModels | — |
| `core:money` | Dinero, divisas, multiplicación-división exacta, reparto por restos mayores, tipos de cambio | — |
| `data:models` | Documentos (`Group`, `Member`, `Expense`, `Transfer`, `ChangeLogEntry`) con `SyncMeta`, serializables | P6 |
| `data:local` | Esquema SQLDelight completo v1 (`AppDatabase`), snapshot `databases/1.db` y `verifyMigrations` | P6, P9 |
| `data:repository` | Repositorios con Flows (cada escritura guarda la entidad, compacta el outbox y registra el historial en una transacción) y motor de sincronización `sync` (`RemoteStore`, `SyncEngine`) | P8, P9 |
| `data:remote` | Firebase (GitLive): `FirestoreRemoteStore` (adaptador del motor), `FirebaseAccountService`, `FirestoreProfileRemote` (`users/{uid}`); test de humo y test de sincronización con emuladores | P10 |
| `domain:usecases` | `ledger` (partes, netos, saldos, pagos sugeridos), `ObserveGroupBalances` y `account` (onboarding, Google, perfil, cerrar sesión, borrar cuenta, aviso para vincular) | P9 |
| `feature:onboarding` | Bienvenida → nombre y avatar → primer grupo; "Ya tengo cuenta" con Google | P9, P10 |
| `feature:profile` | Pestaña Perfil: perfil, vincular Google, divisa preferida, tema, cerrar sesión, borrar cuenta | P11 |

Módulos previstos: más `feature:*` en P9–P10. Las features solo ven `domain`, `data:models` y `core` (ARCHITECTURE.md). El motor de sincronización vive en `data:repository` (paquete `sync`), no en un módulo `data:sync`.

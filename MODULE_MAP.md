# Mapa de módulos

| Módulo | Contenido | Plan que lo amplía |
|---|---|---|
| `composeApp` | Entrada Android/iOS, DI raíz, `AppConfig` por entorno | P8–P9 |
| `core:common` | MVI base, `AppResult`, utilidades | — |
| `core:ui` | `OwlsplitTheme` (tokens, Inter, formas), `MoneyFormat`, componentes y `DesignCatalog`; capturas Roborazzi | P9 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Cliente Ktor por plataforma | P9 (tipos de cambio) |
| `core:datastore` | Preferencias (`AppPreferences`) | P8 |
| `core:logging` | Kermit | — |
| `core:testing` | Utilidades de test | — |
| `core:money` | Dinero, divisas, multiplicación-división exacta, reparto por restos mayores, tipos de cambio | — |
| `data:models` | Documentos (`Group`, `Member`, `Expense`, `Transfer`, `ChangeLogEntry`) con `SyncMeta`, serializables | P6 |
| `data:local` | Esquema SQLDelight completo v1 (`AppDatabase`), snapshot `databases/1.db` y `verifyMigrations` | P6, P9 |
| `data:repository` | Repositorios con Flows (cada escritura guarda la entidad, compacta el outbox y registra el historial en una transacción) y motor de sincronización `sync` (`RemoteStore`, `SyncEngine`) | P8, P9 |
| `data:remote` | Firebase (GitLive): `FirestoreRemoteStore` (adaptador del motor), DTOs; test de humo y test de sincronización con emuladores | P8 |
| `domain:usecases` | `ledger` (partes, netos, saldos, pagos sugeridos) y `ObserveGroupBalances` | P8, P9 |

Módulos previstos: `feature:*` (P8–P10). El motor de sincronización vive en `data:repository` (paquete `sync`), no en un módulo `data:sync`.

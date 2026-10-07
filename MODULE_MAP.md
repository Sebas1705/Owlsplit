# Mapa de módulos

| Módulo | Contenido | Plan que lo amplía |
|---|---|---|
| `composeApp` | Entrada Android/iOS, DI raíz, `AppConfig` por entorno | P8–P9 |
| `core:common` | MVI base, `AppResult`, utilidades | — |
| `core:ui` | Tema y componentes compartidos | P7 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Cliente Ktor por plataforma | P9 (tipos de cambio) |
| `core:datastore` | Preferencias (`AppPreferences`) | P8 |
| `core:logging` | Kermit | — |
| `core:testing` | Utilidades de test | — |
| `core:money` (se crea en P3) | Dinero, divisas, reparto de restos, tipos de cambio | P3 |
| `data:local` | SQLDelight (`AppDatabase`) | P4 |
| `domain:usecases` | Casos de uso; `ledger` (saldos y deudas) | P3 |

Módulos previstos: `data:models`, `data:repository` (P4), `data:remote`, `data:sync` (P2, P6), `feature:*` (P8–P10).

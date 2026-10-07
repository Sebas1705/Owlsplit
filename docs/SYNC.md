# Sincronización

Owlsplit es **local-first**: SQLDelight es la fuente de verdad de la UI y Firestore es el medio de intercambio entre dispositivos. Decisión en [ADR 0003](adr/0003-local-first-sqldelight-sync.md).

## Principios

1. La UI **nunca** espera a la red: lee Flows de SQLDelight.
2. La unidad de sincronización es el **documento** (un gasto, un miembro…). No hay mezcla de campos: un documento gana o pierde entero.
3. **Concurrencia optimista** con `version`: gana la primera escritura que llega al servidor; la otra se conserva como conflicto y nunca se pierde en silencio.
4. Todo es **idempotente**: repetir una subida o una bajada no cambia el resultado.
5. La persistencia offline del propio SDK de Firestore se **desactiva** (caché en memoria) para no tener dos cachés que diverjan; el outbox es la única cola.

## Tablas locales (SQLDelight)

| Tabla | Propósito |
|---|---|
| `group_entity`, `member_entity`, `expense_entity`, `transfer_entity`, `change_log` | Entidades (espejo de Firestore + columnas locales). Booleanos como `INTEGER` 0/1 |
| `expense_payer`, `expense_split` | Pagadores y valores de reparto de cada gasto (se reescriben enteros en cada edición) |
| `outbox` | Cola de escrituras pendientes: `id`, `groupId`, `entityType`, `entityId`, `baseVersion`, `payloadJson`, `attempts`, `nextAttemptAt`, `lastError`, `createdAt` |
| `sync_cursor` | Por grupo y colección: último `updatedAt` remoto aplicado |
| `conflict` | Ediciones locales rechazadas, para reaplicar o descartar |
| `fx_rate_cache` | Tipos de cambio con fecha de obtención (llega en P9, primera migración) |

Columnas locales de cada entidad: `syncState` (`SYNCED`, `PENDING`, `CONFLICT`), `serverVersion`.

## Escritura local

En **una sola transacción**:
1. Se escribe la entidad con `version = serverVersion + 1` y `syncState = PENDING`.
2. Se inserta (o se **compacta**, si ya había una pendiente del mismo documento) la entrada en `outbox` con `baseVersion = serverVersion` y el documento completo.
3. Se añade la entrada de historial local.

Compactar: si hay varias ediciones offline del mismo documento, solo se sube el último estado, con la `baseVersion` original. Así, diez ediciones offline = una escritura.

## Subida (outbox → Firestore)

- Disparadores: cambio de conectividad, nueva entrada en outbox, app a primer plano, WorkManager periódico (Android, con restricción de red) y temporizador en primer plano (iOS).
- Orden: FIFO por `createdAt` dentro de cada grupo; los grupos son independientes entre sí.
- Cada entrada se escribe con `set` del documento completo, `version = baseVersion + 1` y `updatedAt = serverTimestamp()`. Las reglas exigen `request.resource.data.version == resource.data.version + 1` (o `== 1` al crear).
- **Éxito** → se borra del outbox; `serverVersion = version`, `syncState = SYNCED` (si no hay otra pendiente encima).
- **Error transitorio** (red, `unavailable`, `deadline-exceeded`) → `attempts++`, `nextAttemptAt = ahora + min(2^attempts s, 15 min)` con jitter.
- **`permission-denied`** → se lee el documento remoto para distinguir:
  - Versión remota ≥ `baseVersion + 1` → **conflicto**: se aplica la remota en local, la local pasa a `conflict` y al historial (“Tu edición de *Cena* chocó con la de Ana”) y se avisa con opción **Reaplicar** (crea una edición nueva sobre la versión remota) o **Descartar**.
  - Sin acceso al grupo → **acceso perdido**: el grupo pasa a solo lectura; las entradas del outbox de ese grupo se descartan tras confirmar el usuario.
  - Otro motivo (validación) → `lastError` y aviso; es un bug: se registra en Crashlytics.

## Bajada (Firestore → local)

- Para cada grupo **abierto o con actividad reciente** (últimos 30 días, máx. 20) hay listeners sobre `members`, `expenses`, `transfers` y el documento del grupo, con `where("updatedAt", ">=", cursor - 5 min)`, ordenados por `updatedAt`. El margen cubre los relojes de servidor de escrituras concurrentes; la idempotencia hace inocuas las repeticiones.
- El resto de grupos se sincroniza con una consulta puntual al abrir la app o el grupo.
- Para cada documento recibido:
  - Si `remote.version > local.serverVersion` y no hay pendiente local → se sobrescribe en local.
  - Si hay pendiente local → se guarda `serverVersion` remoto; la subida pendiente fallará por versión y entrará en el flujo de conflicto. Para no esperar, se resuelve en el momento con el mismo flujo.
  - Si `remote.version <= local.serverVersion` → se ignora.
- El cursor avanza al mayor `updatedAt` aplicado (tras confirmar la transacción local).
- Nuevo grupo (unión): bajada completa paginada antes de mostrarlo como sincronizado.

## Borrados

- Lógicos: `deleted = true` es una edición más (con versión). Se ocultan en la UI y se excluyen de los saldos.
- Se pueden restaurar desde el historial durante 30 días; después se purgan en local (en Firestore se quedan marcados; la purga remota llegará con Cloud Functions en el futuro).

## Historial (`changelog`)

- Se crea en el cliente con cada acción y se sube por el outbox como documento de solo creación (las reglas prohíben editarlo o borrarlo).
- `diff`: lista de `{campo, antes, después}` legible, generada en el dominio.

## Estado de sincronización en la UI

| Estado | Condición |
|---|---|
| Sincronizado | outbox vacío y listeners activos |
| N cambios pendientes | outbox con N entradas y red disponible (subiendo) |
| Sin conexión | sin red; N pendientes si los hay |
| Error | alguna entrada con error no transitorio o conflicto sin resolver |

## Casos probados (`data:sync`)

Creación, edición y borrado offline y online; compactación; orden; reintentos con backoff; conflicto (las dos ramas: reaplicar y descartar); acceso perdido; muerte del proceso a mitad de subida (idempotencia); bajada con margen y duplicados; unión a grupo grande con paginación; reloj del dispositivo desajustado (no afecta, porque `updatedAt` es del servidor).

## Implementación (P6a)

Motor en `data:repository`, paquete `sync`: `RemoteStore` (puerto hacia el servidor; P6b lo implementa con Firestore), `SyncLocalStore` (outbox, cursores, avisos de acceso), `RemoteApplier` (aplicar remoto y conflictos), `OutboxProcessor` (una pasada de subida) y `SyncEngine` (bucle de subida + escucha). Precisiones respecto a lo anterior:

- **Versión de subida = `base_version + 1`**, no la del payload. Si el documento se edita mientras se sube, al confirmar no se borra la entrada: su base avanza a la versión escrita y la siguiente subida encadena.
- **Rechazo del servidor → se lee el remoto**: sin acceso → acceso perdido (nuevo intento en 1 h); misma o mayor versión con **el mismo contenido** → era nuestra escritura cuyo acuse se perdió, se da por buena; mayor con otro contenido → conflicto; menor → rechazo por validación (bug), reintento en 1 h con `last_error`.
- **FIFO por grupo estricto**: una entrada que espera su reintento retiene a las siguientes de su grupo, aunque estén listas. El bucle despierta en el primer reintento entre las **cabezas de grupo** (despertar en el mínimo global giraba en vacío).
- **Aplicar remoto**: versión menor → se ignora; igual con edición local pendiente → se ignora (eco de nuestra subida); igual sin pendiente → se refresca (`updatedAt` del servidor); mayor con pendiente → conflicto; lo aplicado queda siempre `SYNCED` con `serverVersion = version`.
- **Acceso perdido**: se guarda como `accessLost:<grupo>` en la tabla `meta`. Se detecta al subir (el servidor niega la lectura) y al escuchar (un grupo ya sincronizado deja de aparecer en "mis grupos").
- Estado expuesto por ahora: `SyncEngine.pendingCount` e `isAccessLost(grupo)`; los conflictos están en la tabla `conflict` y en el historial (`CONFLICT`). La UI llega en P9.

## Implementación (P6b): Firestore

`FirestoreRemoteStore` (`data:remote`, GitLive) implementa `RemoteStore`; la app lo arranca en `composeApp/.../sync/SyncModule.kt`.

- **Formato**: un DTO por tipo con exactamente las claves de las reglas (ver [FIREBASE](FIREBASE.md#formato-de-los-documentos)). Se escribe con `set(merge = true)` para no borrar `lastJoinCode`, que el cliente no conoce.
- **Caché de Firestore en memoria**: la copia local es SQLDelight. Sin red, `set` no termina (el SDK la encola): a los 20 s se da por transitoria y la reintenta el outbox; si la escritura encolada llega antes, el reintento se rechaza, se lee el remoto y se reconoce como propia.
- **Errores**: `PERMISSION_DENIED` → rechazo (al subir) o sin acceso (al leer); el resto → transitorio. Para resolver un rechazo se lee del **servidor** (la caché podría tener nuestra escritura sin confirmar).
- **Escuchas**: solo documentos confirmados (sin `hasPendingWrites`) y solo los cambios de cada instantánea. *Mis grupos* se entrega completo y solo cuando no viene de caché ni tiene escrituras pendientes, porque el motor deduce el acceso perdido de lo que falta. Errores transitorios → reintento con espera creciente; `PERMISSION_DENIED` termina la escucha sin romper el motor.
- **Historial con hora de servidor**: `changelog` también lleva `updatedAt` (las reglas exigen `request.time`) y se baja por él. Filtrar por `createdAt` (reloj del cliente) perdía las entradas creadas sin conexión hace más que el margen del cursor.
- **Eco de la propia subida**: con Firestore real la escucha entrega nuestra escritura **antes** de que `set` vuelva. El motor recuerda la subida en vuelo (`InFlightUpload`) y, si llega ese documento con esa versión y el mismo contenido, lo trata como acuse. Sin esto cada subida acababa como conflicto (el test con emuladores falla siempre sin ello).
- **Conflicto visto dos veces**: si la escucha ya registró el conflicto mientras la subida estaba en vuelo, el rechazo posterior no lo vuelve a registrar.
- **Arranque**: sin `google-services.json` no se arranca nada. Con él, sesión anónima (reintentos con espera si no hay red; el onboarding real es P8) y `SyncEngine.start` con el uid de cada estado de sesión. En Android, WorkManager (`SyncWorker`, con red, trabajo único `KEEP`) sube lo pendiente aunque se cierre la app. En iOS el disparador en segundo plano llega en P11.
- **Pendiente para P8**: crear datos antes de la primera sesión deja `createdBy` vacío y el servidor lo rechazaría; el onboarding debe garantizar sesión antes de crear grupos.
- **Pruebas**: `FirestoreSyncEmulatorTest` (dos dispositivos con los emuladores de Firestore y Auth y las reglas reales: unión con código, cambios en ambos sentidos, edición concurrente → un único conflicto). Se lanza con `scripts/run-sync-emulator-test.sh` con un emulador Android conectado; no está en CI.

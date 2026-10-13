# Guía: publicar Owlsplit en Google Play (pasos manuales)

Lo que **solo puede hacer el dueño de la cuenta** en Play Console y Google Cloud. Lo automatizable (subida del AAB desde CI, páginas web, textos y gráficos de la ficha) lo prepara Claude en el repo.

- App: **Owlsplit** · paquete `es.sebas1705.owlsplit` · gratuita, sin anuncios ni compras.
- Cuenta de desarrollador: **personal** (ya publicó otra app).
- Comprobado en el repo el 2026-10-13: `minSdk 24`, `targetSdk 36`, `compileSdk 36` (`build-logic/.../KotlinMultiplatformExtensions.kt`).
- Lo marcado con **[sin verificar]** no se pudo confirmar en la documentación oficial: fíate de lo que diga la Console.

---

## Checklist (en este orden)

- [ ] **1.** Crear la app en Play Console
- [ ] **2.** Firma: configurar Play App Signing con **nuestra** clave de publicación (antes de subir nada)
- [ ] **3.** Contenido de la app: privacidad, borrado de cuenta, anuncios, acceso, clasificación, público, seguridad de los datos, funciones financieras y resto de declaraciones
- [ ] **4.** Ficha de Play Store (textos y gráficos que deja Claude en el repo)
- [ ] **5.** Cuenta de servicio para la API de Play → JSON en Doppler (`PLAY_SERVICE_ACCOUNT_JSON`) → **avisar a Claude**
- [ ] **6.** Primer AAB **subido a mano** a *Prueba cerrada*
- [ ] **7.** Testers mediante un Grupo de Google: 12 o más que se mantengan apuntados 14 días seguidos
- [ ] **8.** Esperar los 14 días (sin que nadie se dé de baja)
- [ ] **9.** Solicitar acceso a producción

> No uses cuentas de tester falsas ni compradas. Va contra las políticas de Play y puede acabar con la suspensión de la cuenta. Necesitas 12 personas reales: amigos, familia o los testers actuales de App Distribution.

---

## Fase 1. Crear la app

**Dónde:** Play Console → *Inicio* → **Crear aplicación**.

| Campo | Valor |
|---|---|
| Nombre de la aplicación | `Owlsplit` |
| Idioma predeterminado | Español (España) – es-ES |
| ¿Aplicación o juego? | Aplicación |
| ¿Gratuita o de pago? | Gratuita (después **no** se puede cambiar a de pago) |
| Declaraciones | Marca las casillas de las Políticas del programa para desarrolladores y de las leyes de exportación de EE. UU. Las tienes que aceptar tú. |

Al crear la app, Play registra y vincula el nombre de paquete a tu cuenta automáticamente. Eso también cuenta para la nueva *verificación de desarrolladores de Android* (ver Fase 10). El paquete queda fijado cuando subes el primer AAB.

---

## Fase 2. Firma de la app: usar NUESTRA clave (crítico)

### Por qué

Los APK de GitHub Releases, de `owlsplit.web.app/apk` y de Firebase App Distribution van firmados con la clave de publicación de Doppler (`SIGNING_*`, alias `owlsplit`, RSA 4096, SHA‑256 `99:0B:AD:25:…:18:C3`). Esa misma huella aparece en:

- `firebase/hosting/.well-known/assetlinks.json` (los enlaces de invitación `/j/…` se abren en la app);
- la app Android de release en Firebase (el inicio de sesión con Google con Credential Manager).

Si Play generara su propia clave, la versión de Play iría firmada con otra clave. Pasaría esto:
1. Quien instaló el APK no podría actualizar desde Play (y al revés): Android rechaza una actualización con otra firma, así que habría que desinstalar y se perderían los datos locales.
2. Habría que añadir la huella de Google a `assetlinks.json` y a Firebase. Si se olvida, fallan los App Links y el inicio de sesión con Google.

Con nuestra clave como *clave de firma de apps*, Google vuelve a firmar con la misma clave y todo sigue funcionando.

> **Aviso:** según la ayuda actual, las apps nuevas se apuntan por defecto a una firma "híbrida" con claves generadas por Google ("quantum-ready hybrid signing") **[sin verificar en la UI]**. La clave solo se puede cambiar **antes** de que una versión llegue a prueba abierta o a producción. Hazlo antes del primer AAB.

### Cómo

1. **Dónde:** al crear la primera versión (Fase 6), en el apartado **Integridad de la app** pulsa **Cambiar clave de firma de apps**. También está en *Protegido con Play → Distribución en Play Store → Ir a Play App Signing → Cambiar la clave de firma de apps*.
2. Elige **"Exportar y subir una clave de un almacén de claves Java"** (*Export and upload a key from Java keystore*). La Console ofrece dos descargas: **`pepk.jar`** y **`encryption_public_key.pem`**.
3. Saca el keystore de Doppler a una carpeta temporal **fuera del repo** (o usa tu copia `Documents\owlsplit-release.jks`):

   ```bash
   # Git Bash. Escribe el .jks fuera del repo y bórralo al terminar.
   doppler secrets get SIGNING_KEYSTORE_BASE64 --project owlsplit --config prd --plain | base64 -d > ~/owlsplit-release.jks
   doppler secrets get SIGNING_KEY_ALIAS --project owlsplit --config prd --plain   # debería ser: owlsplit
   ```

4. Ejecuta PEPK. **Copia el comando que te muestre la Console**, que es el que vale. Tendrá una forma parecida a esta (con marcadores, todo en una sola línea):

   ```bash
   java -jar pepk.jar --keystore=~/owlsplit-release.jks --alias=owlsplit --output=owlsplit-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```

   Pedirá la contraseña del almacén y la de la clave (`SIGNING_STORE_PASSWORD` y `SIGNING_KEY_PASSWORD` de Doppler; son iguales). Escríbelas tú cuando lo pida y no las pegues en el comando.
   - En Windows, si da error, revisa que el comando no tenga saltos de línea.
   - `--output` tiene que ser un **fichero** `.zip`, no una carpeta.
5. Sube `owlsplit-key.zip` en la Console y pulsa **Guardar**.
6. **Comprobación:** en *Play App Signing → Certificado de la clave de firma de apps*, la SHA‑256 tiene que empezar por `99:0B:AD:25` y acabar en `18:C3`. Si no coincide, **no subas ningún AAB** y avisa a Claude.
7. **Clave de subida:** CI firma el AAB con la misma clave, así que la clave de subida será la misma que la de firma. Google recomienda que sean distintas, pero se permite. Si más adelante quieres una clave de subida aparte, se pide un *restablecimiento de la clave de subida* y Claude adapta CI.
8. Borra `~/owlsplit-key.zip` y `~/owlsplit-release.jks` temporales (`rm`). Comprueba que tienes una copia del `.jks` **fuera del PC**: esta clave no se puede recuperar.

**Avisar a Claude:** "Play App Signing con nuestra clave: SHA‑256 coincide" (o la huella que salga si no coincide).

---

## Fase 3. Contenido de la app (Política → Contenido de la app)

**Dónde:** menú izquierdo → *Supervisar y mejorar* → **Política y programas → Contenido de la app**. Completa cada tarjeta.

### 3.1 Política de privacidad
- URL: `https://owlsplit.web.app/privacidad` **(la crea Claude; comprueba que está publicada antes de enviar)**.

### 3.2 Anuncios
- ¿Contiene anuncios? **No.**

### 3.3 Acceso a la app
- **"Todas las funciones están disponibles sin acceso especial"**.
- Motivo, comprobado en el código: al primer arranque se crea solo una cuenta anónima de Firebase (`signInAnonymously`), el onboarding se puede saltar (el nombre es opcional) y vincular Google es opcional. No hay pantalla de inicio de sesión obligatoria. Nota: hace falta internet para sincronizar.

### 3.4 ID de publicidad
- ¿Usa el ID de publicidad? **No.** En el manifiesto combinado de release no aparece `com.google.android.gms.permission.AD_ID` (no se usa Firebase Analytics).

### 3.5 Clasificación del contenido (IARC)
- Correo de contacto: el tuyo.
- Categoría: **"Todos los demás tipos de aplicaciones"** (utilidad o productividad; no es juego, red social ni agregador) **[sin verificar el nombre exacto]**.
- Respuestas:
  - Violencia, miedo, sexualidad, lenguaje soez, sustancias controladas, humor crudo: **No** en todo.
  - Apuestas o juego simulado: **No**.
  - **¿Los usuarios pueden interactuar o intercambiar contenido?** **Sí.** Los miembros de un grupo ven los nombres, notas de gastos y datos de pago que escriben los demás. Solo comparten con personas invitadas por enlace o QR; no hay chat ni contenido público.
  - ¿Comparte la ubicación del usuario con otros usuarios? **No.**
  - ¿Permite comprar productos digitales? **No.**
  - ¿Acceso sin restricciones a internet (navegador)? **No.**
  - ¿Es principalmente una app de noticias o de contenido educativo? **No.**
- Resultado esperado: PEGI 3 / Para todos, con el aviso "Los usuarios interactúan".

### 3.6 Público objetivo y contenido
- Grupos de edad: **solo "18 años o más"** (recomendado).
  - Razones: la app maneja deudas y datos de pago (teléfono de Bizum, PayPal.me, Revolut) y abre esos servicios, que exigen ser mayor de edad (PayPal pide 18 años **[sin verificar la edad mínima de Bizum y Revolut]**). Si incluyes menores de 13, la app entra en la política de *Familias* (SDKs certificados, más revisión) y no compensa. Incluir 13–17 no entra en *Familias*, pero añade preguntas y revisión sin aportar nada.
  - Alternativa válida: "16-17 y 18+", si quieres llegar a estudiantes. **Nunca** incluyas menores de 13.
- ¿Podría atraer a niños? **No.**

### 3.7 Seguridad de los datos (Data safety)

Basado en el código: `firebase/firestore.rules`, `docs/FIREBASE.md`, `data/remote`, Crashlytics y el manifiesto combinado.

**Sección general**

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte alguno de los tipos de datos obligatorios? | **Sí** |
| ¿Todos los datos se cifran en tránsito? | **Sí** (Firebase y Frankfurter usan HTTPS) |
| Métodos de creación de cuenta | **OAuth** (Google) y **Otro** (cuenta anónima automática) **[sin verificar las etiquetas exactas]** |
| URL de borrado de cuenta | `https://owlsplit.web.app/borrar-cuenta` **(la crea Claude)** |
| ¿Se pueden borrar datos sin borrar la cuenta? | **No** (o "Sí" si Claude añade esa opción; pregúntale) |
| ¿Los usuarios pueden pedir que se borren sus datos? | **Sí**: dentro de la app (Perfil → Borrar cuenta, `DeleteAccount` en `domain/usecases/account`) y en la URL anterior |
| ¿Datos compartidos con terceros? | **No**. Firebase (Google) es un *proveedor de servicios* que trata los datos por cuenta nuestra. A Frankfurter (`api.frankfurter.dev`) solo se le piden tipos de cambio, sin datos del usuario. Abrir Bizum, PayPal o Revolut lo inicia el usuario. Lo que ven los demás miembros del grupo es contenido que el usuario comparte a propósito. |

**Tipos de datos recogidos.** En todos: **Compartido = No**, **Tratado de forma efímera = No**.

| Categoría → tipo | Qué es en Owlsplit | ¿Obligatorio u opcional? | Finalidades |
|---|---|---|---|
| Información personal → **Nombre** | Nombre visible del perfil (`users/{uid}.displayName`) y nombres de los miembros | **Opcional** (en el onboarding se puede dejar vacío y se usa "Yo") | Funcionalidad de la app; Gestión de la cuenta |
| Información personal → **Dirección de correo** | Correo de la cuenta de Google al vincularla (lo guarda Firebase Auth) | **Opcional** (solo si vincula Google) | Gestión de la cuenta |
| Información personal → **IDs de usuario** | UID de Firebase (en grupos, gastos y el historial) | **Obligatorio** | Funcionalidad de la app; Gestión de la cuenta |
| Información personal → **Número de teléfono** | Teléfono de Bizum que el usuario guarda como forma de cobro (`payMethods.bizum`) | **Opcional** | Funcionalidad de la app |
| Información financiera → **Otra información financiera** | Importes, deudas y saldos de gastos y transferencias; usuario de PayPal.me y de Revolut | **Obligatorio** (es el núcleo de la app) | Funcionalidad de la app |
| Información financiera → **Historial de compras** | Los gastos son registros de compras hechas por los miembros (título, categoría, importe, fecha) | **Obligatorio** | Funcionalidad de la app |
| Actividad en apps → **Otro contenido generado por usuarios** | Nombres e iconos de grupos, títulos y notas de gastos, historial de cambios | **Obligatorio** | Funcionalidad de la app |
| Información y rendimiento de la app → **Registros de fallos** | Crashlytics: trazas de fallos y errores no fatales | **Obligatorio** (siempre activo en release; no hay interruptor) | Análisis |
| Información y rendimiento de la app → **Diagnóstico** | Crashlytics: metadatos del dispositivo y del estado de la app en el fallo | **Obligatorio** | Análisis |
| Dispositivo u otros IDs → **Dispositivo u otros IDs** | UUID de instalación de Crashlytics / ID de instalación de Firebase | **Obligatorio** | Análisis |

Notas:
- "Historial de compras" es una interpretación prudente. Declarar de más es más seguro que quedarse corto. Si prefieres no marcarlo, "Otra información financiera" ya cubre importes y deudas.
- **No** declares "Información de pago del usuario" (tarjetas o cuentas bancarias): la app no las guarda.
- **No** se declara ubicación. Firebase Auth recibe la IP para seguridad, pero no deducimos ubicación de ella **[sin verificar: criterio habitual, no confirmado por Google para este caso]**.
- Si se añaden fotos de tickets (`receiptUrl` existe en el modelo, sin interfaz todavía), habrá que añadir "Fotos".

### 3.8 Funciones financieras
- Respuesta recomendada: **"Mi app no ofrece ninguna función financiera"**.
- Razón: Owlsplit es un cuaderno de cuentas compartidas. No mueve dinero, no guarda tarjetas ni cuentas bancarias, no da préstamos, no asesora y no intermedia pagos. "Saldar" solo abre la app o el enlace de Bizum, PayPal o Revolut del propio usuario, y el pago ocurre fuera.
- Si en la revisión piden lo contrario, la categoría menos mala sería *Otro* con esa descripción **[sin verificar: la ayuda de Google no habla de apps para repartir gastos]**.
- La declaración es obligatoria para todas las apps, también las que están en prueba cerrada.

### 3.9 Resto de declaraciones (todo "No" o "No aplica")
- Aplicación gubernamental: **No**. Aplicaciones de salud: **ninguna**. Aplicación de noticias: **No**.
- Permisos de servicio en primer plano: el manifiesto combinado incluye `FOREGROUND_SERVICE` genérico (de WorkManager, para los gastos recurrentes) sin tipos `FOREGROUND_SERVICE_*`. Si la Console pide declararlo, avisa a Claude **[sin verificar si lo pedirá]**.
- Otros permisos del manifiesto de release (todos de librerías): `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC`/`USE_FINGERPRINT` (de Credential Manager / play-services-auth) y `READ_GSERVICES`. No hay **cámara** (los QR solo se **generan** con qrose; para escanearlos se usa la cámara del sistema), ni ubicación, ni contactos, ni SMS.

---

## Fase 4. Ficha de Play Store

**Dónde:** *Hacer crecer usuarios → Presencia en la tienda → Ficha principal* y *Configuración de la tienda*.

- Textos y gráficos: **están en el repo**, en `fastlane/metadata/android/es-ES` y `en-US` (`title.txt`, `short_description.txt`, `full_description.txt`, `images/icon.png`, `images/featureGraphic.png` y `images/phoneScreenshots/`). La primera vez, cópialos y súbelos a mano: la ficha tiene que estar completa para mandar la prueba cerrada a revisión. Cuando esté la cuenta de servicio (Fase 5), el workflow **Play listing** los publica solo cada vez que cambian en `main`.
- Configuración de la tienda: categoría **Finanzas** (donde están Splitwise y Settle Up) o *Productividad* si quieres evitar dudas con funciones financieras. Etiquetas: hasta 5 relacionadas.
- Datos de contacto: Play exige un **correo** público en la ficha (aunque la web use GitHub Issues como contacto); puede ser uno creado solo para la app. Sitio web: `https://owlsplit.web.app`.
- Países: en *Producción/Prueba cerrada → Países/regiones*, empieza por España (o los que quieras).

---

## Fase 5. Cuenta de servicio para la API de Play (para que CI suba el AAB)

Ya no hace falta **vincular** un proyecto de Google Cloud en Play Console. Basta con crear una cuenta de servicio en cualquier proyecto de Cloud e **invitarla como usuario** en Play Console.

1. **Google Cloud Console**, proyecto **`owlsplit`**:
   - *APIs y servicios → Biblioteca* → **Google Play Android Developer API** → **Habilitar**.
   - *IAM y administración → Cuentas de servicio → Crear*: nombre `play-publisher` (correo `play-publisher@owlsplit.iam.gserviceaccount.com`). **No le des roles de IAM** en Cloud, porque no los necesita. No reutilices `owlsplit-ci`, para mantener los permisos separados.
   - En la cuenta → *Claves → Añadir clave → JSON*. Se descarga un `.json`.
2. **Play Console** → *Usuarios y permisos* → **Invitar a nuevos usuarios**:
   - Correo: `play-publisher@owlsplit.iam.gserviceaccount.com`.
   - *Permisos de aplicación* → añade **Owlsplit** con: **Ver información de la app (solo lectura)**, **Publicar en canales de prueba**, **Gestionar canales de prueba y editar listas de testers**. **Gestionar la presencia en la tienda** (para el workflow *Play listing*). Cuando haya producción, añade **Publicar en producción…** (para *Play promote*).
   - Nada de permisos de cuenta ni financieros. → **Invitar usuario**.
3. **Doppler** (sin que el JSON pase por el chat):
   ```bash
   doppler secrets set PLAY_SERVICE_ACCOUNT_JSON --project owlsplit --config prd < ~/Downloads/<fichero>.json
   rm ~/Downloads/<fichero>.json
   ```
4. Los permisos de una cuenta de servicio recién invitada pueden tardar en activarse (a veces horas) **[sin verificar el plazo actual]**.

**Avisar a Claude:** "`PLAY_SERVICE_ACCOUNT_JSON` está en Doppler owlsplit/prd e invitada en Play Console". No pegues el JSON en el chat.

---

## Fase 6. Primer AAB, subido a mano, a prueba cerrada

La API de Play no puede publicar en una app que nunca ha recibido un AAB. El **primero se sube a mano**; desde el segundo lo hace el workflow.

1. Descarga el `.aab` de la última GitHub Release (`release.yml` lo adjunta) o lanza el workflow de release.
2. **Dónde:** *Probar y publicar → Pruebas → Prueba cerrada* → canal **Alpha** (o crea uno, p. ej. `testers`) → **Crear versión**.
3. **Integridad de la app → Play App Signing**: comprueba que ya está con nuestra clave (Fase 2). Si no, hazlo **ahora**, antes de subir.
4. Sube el `.aab`. Nombre de la versión: el que proponga (p. ej. `1.0.0-beta.5`). Notas: `es-ES` con un resumen breve.
5. Si aparece el aviso de "archivo de desofuscación", ignóralo por ahora; Claude puede añadir la subida del `mapping.txt`.
6. **Revisar versión → Iniciar lanzamiento en prueba cerrada**. La primera revisión puede tardar unos días.

**Avisar a Claude:** "Primer AAB subido a prueba cerrada, canal `<nombre>`". Claude guarda ese nombre en la variable `PLAY_TRACK` del repo (para el canal Alpha es `alpha`) y desde entonces cada tag `vX.Y.Z` sube el AAB a ese canal con sus notas en es-ES y en-US. También puedes hacerlo tú: `gh variable set PLAY_TRACK --body alpha --repo Sebas1705/Owlsplit`.

---

## Fase 7. Testers con un Grupo de Google

1. https://groups.google.com → **Crear grupo**: p. ej. `owlsplit-testers` (`owlsplit-testers@googlegroups.com`). Quién puede unirse: *Cualquiera puede pedir unirse* o *Solo invitados*. Invita a los testers (los de App Distribution + amigos y familia). **Necesitan cuentas de Google reales**, con el mismo correo que usan en Play Store.
2. Play Console → *Prueba cerrada → (tu canal) → Testers* → **Grupos de Google** → añade `owlsplit-testers@googlegroups.com` → **Guardar**.
3. Copia el **enlace de participación** ("Unirse en la web") y mándalo. Cada tester:
   1. se une al grupo;
   2. abre el enlace → **Convertirse en tester**;
   3. instala Owlsplit **desde Play Store**. Si ya tiene el APK de GitHub, Play lo actualiza sin desinstalar, porque la firma es la misma.
4. Apunta a **15 o más** para tener margen: si alguien se sale, su contador de 14 días vuelve a empezar.
5. Pide uso real (crear un grupo, añadir gastos). En el formulario de producción Google pregunta por la interacción y el feedback recibido.

---

## Fase 8. Los 14 días

- Requisito para cuentas **personales creadas después del 13/11/2023**: **12 o más testers apuntados sin interrupción durante 14 días** en una prueba cerrada. Si tu cuenta es anterior, puede que no te aplique; lo verás en el *Panel* (si ya aparece "Solicitar acceso a producción" o directamente *Producción* habilitada).
- El progreso se ve en el *Panel* de la app.
- Mientras tanto, sube versiones nuevas (las sube CI) y apunta el feedback y lo que corriges: lo vas a necesitar en la Fase 9.

---

## Fase 9. Solicitar acceso a producción

**Dónde:** *Panel* → **Solicitar acceso a producción**. Tiene tres secciones; guarda en cada página (si sales sin guardar se pierde).

Respuestas orientativas (ajústalas a lo que haya pasado de verdad):

- **Sobre la prueba cerrada**
  - ¿Cómo reclutaste testers? "Amigos, familia y compañeros que ya probaban la app por Firebase App Distribution, invitados a un Grupo de Google."
  - Facilidad para reclutar: la que fuera.
  - Uso y feedback: "Crearon grupos reales (viajes, piso compartido), añadieron gastos, probaron invitaciones por enlace/QR, saldar con Bizum/PayPal y exportar CSV/PDF. El feedback llegó por mensajes directos." + 2 o 3 ejemplos concretos de cambios hechos por ese feedback (mira los commits de esas dos semanas).
- **Sobre la app**
  - Público: "Personas que comparten gastos: viajes, pisos compartidos, parejas, cenas."
  - Valor: "Registrar gastos compartidos, ver quién debe a quién con el mínimo de transferencias, funciona sin conexión y sin crear cuenta, multidivisa, gratis y sin anuncios, código abierto (MIT)."
  - Instalaciones esperadas el primer año: realista (p. ej. 100–1.000).
- **Preparación para producción**
  - Cambios hechos por la prueba: lista concreta.
  - Cómo decidiste que está lista: "Sin fallos abiertos en Crashlytics en la última semana, flujos principales probados por los testers, versión X.Y.Z estable."
- Revisión: normalmente **7 días o menos**. Puede que pidan seguir con la prueba más tiempo.

Cuando te den acceso a producción, publicar es lanzar el workflow **Play promote** (*Actions → Play promote → Run workflow*): `from` = tu canal de prueba, `to` = `production`, `rollout` = `0.1` para un 10 % de usuarios (o `1` para todos). Para subir el porcentaje, vuelve a lanzarlo con `from` = `to` = `production` y otro `rollout`. Dale antes a la cuenta de servicio el permiso **Publicar en producción**.

**Avisar a Claude:** acceso a producción concedido.

---

## Fase 10. Verificación de desarrolladores de Android (2026–2027)

- Google exige registrar la **identidad** del desarrollador y los **nombres de paquete** para que las apps se puedan instalar en dispositivos certificados, **también fuera de Play** (nuestros APK de GitHub y de la web). Calendario: obligatorio desde el 30/09/2026 en Brasil, Indonesia, Singapur y Tailandia; en todo el mundo en **2027**.
- Qué hacer:
  1. Play Console → *Configuración → Cuenta de desarrollador*: comprueba que tu identidad está **verificada**. Si ya publicaste, normalmente sí.
  2. En la *Inicio* de Play Console, comprueba que `es.sebas1705.owlsplit` aparece registrado. Al crear la app en Play se registra solo.
  3. Si la Console pide **demostrar que tienes la clave de firma** del paquete, sigue sus pasos (con la misma clave de la Fase 2) **[sin verificar el procedimiento: la documentación no lo detalla]**.
- Como el APK de GitHub y el de Play comparten paquete y clave, un único registro debería cubrir los dos **[sin verificar]**.

---

## Resumen: qué tienes que pasar o avisar a Claude

| Momento | Mensaje |
|---|---|
| Fase 2 | "Play App Signing con nuestra clave; SHA‑256 coincide" (o la huella si no coincide) |
| Fase 3 | Que las páginas `/privacidad` y `/borrar-cuenta` estén publicadas **antes** de enviar los formularios (pídeselas a Claude si no lo están). Si la Console pide declarar el servicio en primer plano. |
| Fase 5 | "`PLAY_SERVICE_ACCOUNT_JSON` en Doppler y cuenta invitada" (**nunca** el JSON en el chat) |
| Fase 6 | Nombre exacto del canal de prueba cerrada y que el primer AAB ya está subido |
| Fase 9 | Acceso a producción concedido |

## Fuentes (consultadas el 2026-10-13)

- Requisitos de prueba de cuentas personales nuevas (12 testers / 14 días, solicitud de producción): https://support.google.com/googleplay/android-developer/answer/14151465
- Play App Signing y clave propia: https://support.google.com/googleplay/android-developer/answer/9842756
- API de Play: ya no hay que vincular un proyecto de Cloud; se invita la cuenta de servicio: https://developers.google.com/android-publisher/getting_started
- Requisito de borrado de cuenta: https://support.google.com/googleplay/android-developer/answer/13327111
- Formulario de seguridad de los datos (definiciones y tipos): https://support.google.com/googleplay/android-developer/answer/10787469
- Datos que recogen los SDK de Firebase (Auth, Firestore, Crashlytics): https://firebase.google.com/docs/android/play-data-disclosure
- Declaración de funciones financieras: https://support.google.com/googleplay/android-developer/answer/13849271
- Verificación de desarrolladores de Android: https://developer.android.com/developer-verification y https://developer.android.com/developer-verification/guides/google-play-console
- Sintaxis de PEPK (fuentes de la comunidad; manda el comando que muestra la Console): https://discuss.appstudio.dev/t/play-encrypt-private-key-google-play-pepk/3435

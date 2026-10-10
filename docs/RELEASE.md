# Distribución

Todo se reparte por **Firebase App Distribution** (proyecto `owlsplit`, grupo `testers`), siempre la misma app: `es.sebas1705.owlsplit`, firmada con la clave de publicación. Hay dos momentos:

| Cuándo | Qué reciben los testers | Flujo |
|---|---|---|
| Cada merge a `main` que cambie la app | Build `main-<n>` | `.github/workflows/distribute.yml` |
| Un tag `vX.Y.Z` | La versión `X.Y.Z`, y además una GitHub Release con APK, AAB, SHA-256 y notas | `.github/workflows/release.yml` |

`versionCode` es el número de commits de `main`: crece siempre, así que cada build se instala encima de la anterior, venga de `main` o de un tag.

Los testers reciben un correo de App Distribution con el enlace de descarga (la primera vez aceptan la invitación e instalan el APK desde el navegador o con la app *Firebase App Tester*). Añadir testers: `node scripts/setup-ci-access.mjs correo@…` (también rota la clave de CI) o la consola de App Distribution.

Las builds de depuración (`es.sebas1705.owlsplit.debug`, `./gradlew :composeApp:installDebug`) son solo para desarrollo local: se instalan junto a la de los testers y usan el mismo proyecto.

## Publicar una versión

```bash
git tag v1.0.0 && git push origin v1.0.0
```

También se puede lanzar a mano desde Actions con la versión (`X.Y.Z-beta.1` crea una prerelease). El AAB queda listo para Google Play.

Cuando termina la Release, `deploy-firebase.yml` vuelve a desplegar Hosting con el APK de esa versión. Así **[owlsplit.web.app](https://owlsplit.web.app)** ofrece siempre la descarga de la última, incluidas las betas:

- el APK está en `/download/owlsplit.apk`;
- `/download/latest.json` indica la versión, el tamaño, el SHA-256 y la fecha.

El APK no se guarda en el repo: cada despliegue lo descarga de la GitHub Release.

Todos los secretos vienen de Doppler `owlsplit/prd`; GitHub solo guarda `DOPPLER_TOKEN`, un token de solo lectura.

## Preparación (hecha el 2026-10-08)

- Proyecto `owlsplit`: Firestore `eur3`, apps Android (release y debug) e iOS, reglas, índices y Hosting.
- Configuración de Firebase en Doppler (`scripts/setup-firebase-env.sh`).
- Clave de publicación `Documents\owlsplit-release.jks` en Doppler (`scripts/setup-release-keystore.sh`); SHA-1 `D9:45:BC:07:…:08:C3`, SHA-256 `99:0B:AD:25:…:18:C3`, registradas en la app de release y en `assetlinks.json`. **Guardar una copia del `.jks` fuera del PC**: no se puede sustituir.
- Cuenta de servicio `owlsplit-ci@owlsplit`, su clave en Doppler, `DOPPLER_TOKEN` en GitHub y el grupo `testers` (`scripts/setup-ci-access.mjs`).
- Authentication activo (Anónimo y Google) desde el 2026-10-08; `google-services.json` incluye el cliente web de Google.

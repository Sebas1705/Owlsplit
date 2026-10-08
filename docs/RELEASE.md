# Distribución

Dos canales, los dos por **Firebase App Distribution** al grupo `testers` (cada proyecto tiene el suyo):

| Canal | Cuándo | App | Proyecto | Flujo |
|---|---|---|---|---|
| **Staging** | Cada merge a `main` que cambie la app | `es.sebas1705.owlsplit.stg` ("Owlsplit Staging", se instala junto a producción) | `owlsplit-stg` | `.github/workflows/distribute-staging.yml` |
| **Producción** | Un tag `vX.Y.Z` | `es.sebas1705.owlsplit` | `owlsplit` | `.github/workflows/release.yml` |

Las dos se firman con la **misma clave de publicación**, así que cada build se instala encima de la anterior y los App Links y Google Sign-In funcionan. `versionCode` es el número de ejecución del flujo; `versionName` es `stg-<n>` en staging y la versión del tag en producción.

Los testers reciben un correo de App Distribution con el enlace de descarga (la primera vez instalan la app *Firebase App Tester* o descargan el APK desde el navegador). Para añadir testers: `node scripts/setup-ci-access.mjs correo@…` (también rota la clave de CI) o la consola de App Distribution de cada proyecto.

## Publicar una versión de producción

```bash
git tag v1.0.0 && git push origin v1.0.0
```

`release.yml` compila el APK y el AAB de producción, crea la GitHub Release (APK, AAB, SHA-256 y notas de los PRs desde el tag anterior) y sube el APK a App Distribution. También se puede lanzar a mano desde Actions con la versión (`X.Y.Z-beta.1` crea una prerelease). El AAB queda listo para Google Play.

Todos los secretos vienen de Doppler (`owlsplit/prd` y `owlsplit/stg`); GitHub solo guarda `DOPPLER_TOKEN` y `DOPPLER_TOKEN_STG`, tokens de solo lectura.

## Preparación (hecha el 2026-10-08)

- Proyectos `owlsplit-stg` y `owlsplit` con Firestore, apps Android/iOS, reglas, índices y Hosting.
- Configuración de Firebase en Doppler `stg` y `prd` (`scripts/setup-firebase-env.sh`).
- Clave de publicación `Documents\owlsplit-release.jks` en Doppler `prd` y `stg` (`scripts/setup-release-keystore.sh`); SHA-1 `D9:45:BC:07:…:08:C3`, SHA-256 `99:0B:AD:25:…:18:C3`, registradas en las apps Android de staging y producción y en `assetlinks.json`. **Guardar una copia del `.jks` fuera del PC**: no se puede sustituir.
- Cuenta de servicio `owlsplit-ci@owlsplit` (App Distribution en los dos proyectos; reglas, Hosting e índices en producción), su clave en Doppler, los tokens en GitHub y el grupo `testers` (`scripts/setup-ci-access.mjs`).
- **Pendiente**: Authentication en la consola de `owlsplit-stg` y `owlsplit` (*Comenzar* → Anónimo y Google), y después volver a ejecutar `scripts/setup-firebase-env.sh stg` y `prd` para que `google-services.json` incluya el cliente web de Google. Sin Authentication la app no pasa del onboarding en esos entornos.

## Comprobar una build de producción en local

```bash
APP_ENV=production ./gradlew :composeApp:assembleRelease
```

Necesita `composeApp/google-services.json` de producción (`scripts/doppler-sync-local.sh prd`); sin variables `SIGNING_*` se firma con la clave de depuración. Volver a `dev` después: `scripts/doppler-sync-local.sh dev`.

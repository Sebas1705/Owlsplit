# Publicar una versión

Solo lo que lleva tag llega a los testers. El flujo `.github/workflows/release.yml`:

1. Se dispara con un tag `vX.Y.Z` (o a mano desde Actions con la versión; `X.Y.Z-beta.1` crea una prerelease).
2. Compila el APK y el AAB de **producción** (`APP_ENV=production`) firmados con la clave de publicación, con `versionName` = la versión del tag y `versionCode` = número de ejecución del flujo (cada versión instala encima de la anterior).
3. Crea la GitHub Release con el APK, el AAB, sus SHA-256 y las notas generadas de los PRs desde el tag anterior.
4. Sube el APK a Firebase App Distribution, grupo `testers` (o el de la variable de repositorio `FIREBASE_TESTER_GROUP`).

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Todos los secretos vienen de Doppler `owlsplit/prd`; GitHub solo guarda `DOPPLER_TOKEN` (token de solo lectura). El AAB queda listo para Google Play cuando se publique allí.

## Preparación (una vez, el propietario)

Estado el 2026-10-08: los proyectos `owlsplit-stg` y `owlsplit` existen con Firestore, apps Android/iOS, reglas, índices y Hosting desplegados, y huellas de depuración registradas. Falta:

1. **Authentication** en la consola de `owlsplit-stg` y `owlsplit`: *Comenzar* → activar **Anónimo** y **Google** (correo de soporte). Sin esto la app no pasa del onboarding en esos entornos.
2. `scripts/setup-firebase-env.sh stg` y `scripts/setup-firebase-env.sh prd` (después del paso 1, para que `google-services.json` incluya el cliente de Google).
3. `scripts/setup-release-keystore.sh`: crea `Documents\owlsplit-release.jks`, lo guarda en Doppler y registra sus huellas. Guardar una copia del `.jks` fuera del PC. Pasar la SHA-256 que imprime para añadirla a `firebase/hosting/.well-known/assetlinks.json` (paquete `es.sebas1705.owlsplit`); el despliegue de `main` la publica.
4. `node scripts/setup-ci-access.mjs tu-correo@…`: cuenta de servicio de CI, su clave en Doppler, `DOPPLER_TOKEN` en GitHub y el grupo `testers` con los correos indicados.
5. Primera versión: `git tag v1.0.0-beta.1 && git push origin v1.0.0-beta.1`.

## Comprobar una build de producción en local

```bash
APP_ENV=production ./gradlew :composeApp:assembleRelease
```

Necesita `composeApp/google-services.json` de producción (`scripts/doppler-sync-local.sh prd`); sin variables `SIGNING_*` se firma con la clave de depuración. Volver a `dev` después: `scripts/doppler-sync-local.sh dev`.

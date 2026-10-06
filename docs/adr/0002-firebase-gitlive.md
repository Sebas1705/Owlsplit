# 0002 · Firebase con el SDK KMP de GitLive

- **Estado:** aceptada · 2026-10-07

## Contexto
Hace falta autenticación, almacenamiento compartido en tiempo real entre miembros y, más adelante, notificaciones y ficheros. El autor ya opera Firebase en YouKnow (reglas probadas con emulador, Doppler, App Distribution, cuentas de servicio mínimas).

## Decisión
**Firebase**: Authentication (anónimo, Google, Apple), Firestore, Hosting (invitaciones), Crashlytics y App Distribution, accedidos desde código común con `dev.gitlive:firebase-*`. Plan Spark durante el MVP, sin Cloud Functions ni Storage.

## Consecuencias
- Seguridad y validación en las reglas de Firestore, probadas con el emulador en CI.
- Dependencia del SDK de GitLive: el primer paso del plan es una prueba de humo en Android e iOS (auth anónima, escritura, listener). Si fallara en iOS, alternativa: API REST de Firestore con Ktor detrás de la misma interfaz `data:remote`.
- Funciones que necesitan servidor (push a otros usuarios, limpieza) llegan con el plan Blaze en subproyectos posteriores.

## Alternativas descartadas
- **Supabase**: mejor modelo relacional, pero el offline habría que construirlo igual y no aporta experiencia previa.
- **Backend propio** (Ktor o Workers + D1): máximo control, demasiado trabajo antes de tener algo usable.

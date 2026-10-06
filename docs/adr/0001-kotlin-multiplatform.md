# 0001 · Kotlin Multiplatform + Compose Multiplatform

- **Estado:** aceptada · 2026-10-07

## Contexto
Una app de gastos compartidos solo es útil si la usa todo el grupo, y los grupos mezclan Android e iOS. El autor domina Kotlin, Compose y Android (YouKnow) y mantiene plantillas Templetry para `android` y `kmp`.

## Decisión
Android e iOS desde el MVP con **Kotlin Multiplatform** y **Compose Multiplatform**, generando el proyecto con la plantilla Templetry `kmp/modular-features` (Clean Architecture por capas, MVI, Koin, SQLDelight, Ktor, Coil, perfiles dev/stg/prd). Al generar, se actualizan las versiones de la plantilla (Kotlin 2.1 y CMP 1.7 en origen) a las estables actuales. El target de escritorio de la plantilla se desactiva en el MVP.

## Consecuencias
- Dominio, datos, sincronización y UI compartidos al 100 %; solo los puntos de integración (Google/Apple Sign-In, WorkManager, enlaces) son específicos de plataforma.
- iOS no compila en Windows: CI con runner macOS y validación manual de iOS en hitos.

## Alternativas descartadas
- **Solo Android** (`android/modular-features`): más rápido de arrancar, pero deja fuera a medio grupo.
- **Android + web ligera**: no cubre la experiencia nativa en iPhone.
- **Flutter / React Native**: tiran a la basura la experiencia y las plantillas existentes en Kotlin.

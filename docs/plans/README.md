# Planes de implementación — Núcleo (MVP)

El spec del núcleo ([2026-10-07-owlsplit-core-design.md](../designs/2026-10-07-owlsplit-core-design.md)) abarca varios subsistemas independientes, así que se implementa en **planes encadenados**. Cada plan deja software que compila, pasa sus tests y se puede probar.

Los planes se escriben en detalle (código completo, comandos exactos) **justo antes de ejecutarlos**, porque cada uno depende de cómo haya quedado el anterior. Los tres primeros ya están escritos.

## Planes

| # | Plan | Entrega verificable | Depende de | Estado |
|---|---|---|---|---|
| P1 | [Base del proyecto](2026-10-07-p01-foundation.md) | Proyecto KMP generado desde la plantilla, versiones actuales, ejemplo de la plantilla eliminado, app que arranca en Android, CI verde | — | Hecho |
| P2 | [Firebase dev + prueba de humo GitLive](2026-10-07-p02-firebase-smoke.md) | Proyecto `owlsplit-dev`, Doppler, configuración por entorno y test instrumentado que hace auth anónima + escritura + listener contra Firestore real | P1 | Hecho |
| P3 | [Dinero y cálculo de saldos](2026-10-07-p03-money-ledger.md) | `core:money` y `domain` (`ledger`): divisas, reparto, conversión, saldos y simplificación, con tests de tablas y de propiedades | P1 | Hecho |
| P4 | Base de datos local y repositorios | Esquema SQLDelight completo (entidades, outbox, cursores, conflictos), DAOs, repositorios con Flows, migraciones verificadas | P1, P3 | Pendiente |
| P5 | Reglas de Firestore | `firebase/firestore.rules`, índices y `firebase/rules-test` con el emulador, en CI | P2 | Pendiente |
| P6 | Motor de sincronización | `data:remote` + `data:sync`: outbox, subida con backoff, listeners incrementales, conflictos, acceso perdido; tests con Firestore falso | P4, P5 | Pendiente |
| P7 | Sistema de diseño | `OwlsplitTheme`, tokens, Inter, iconos Tabler, componentes de `core:ui` con previews y capturas claro/oscuro | P1 | Pendiente |
| P8 | Cuentas y onboarding | Auth anónima, onboarding, perfil, vincular Google/Apple, borrar cuenta | P6, P7 | Pendiente |
| P9 | Grupos, gastos y saldos (pantallas) | Inicio, grupo (gastos, saldos, saldar), editor de gasto y de reparto, transferencias, historial, multidivisa con Frankfurter | P6, P7, P8 | Pendiente |
| P10 | Invitaciones | Códigos, QR, App Links / Universal Links con Firebase Hosting, unirse y reclamar miembro | P9 | Pendiente |
| P11 | Pulido y entrega | i18n es/en, accesibilidad, Crashlytics, proyectos stg/prd, firma, pipeline de release a App Distribution, checklist de criterios de aceptación | P10 | Pendiente |

## Dependencias y paralelismo

```
P1 ──┬── P2 ── P5 ──┐
     ├── P3 ── P4 ──┴── P6 ──┬── P8 ── P9 ── P10 ── P11
     └── P7 ─────────────────┘
```

- **P2, P3 y P7** pueden ir en paralelo tras P1: no comparten archivos, salvo `settings.gradle.kts` y `gradle/libs.versions.toml`, que se tocan en tareas pequeñas y se integran con rebase.
- **P2 va primero** si solo hay una persona: es la prueba de riesgo del SDK de GitLive (ADR 0002). Si falla, cambia el diseño de `data:remote`.
- Cada plan termina con build verde, tests verdes y un commit por tarea.

## Entorno (SEBAS-PC)

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
```

El JDK 26 del sistema no sirve para Gradle/Kotlin. iOS no compila en Windows: se compila en CI (macOS).

## Versiones fijadas (2026-10-07, validadas en P1)

| Pieza | Versión | Nota |
|---|---|---|
| Gradle | 9.5.1 | 9.1 compila build-logic con Kotlin 2.2, que no lee metadatos de Kotlin 2.4; AGP 8 se rompe con Gradle ≥ 9.6 |
| Kotlin | 2.4.20 | |
| Compose Multiplatform | 1.11.1 (material3 1.9.0) | 1.12 exige compileSdk 37 |
| Android Gradle Plugin | 8.13.2 | Admite como máximo compileSdk 36. AGP 9 exige el plugin `com.android.kotlin.multiplatform.library`; se migra en un plan aparte cuando la plantilla lo haga |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |
| SQLDelight | 2.4.1 | |
| Koin | 4.2.2 | |
| Ktor | 3.5.2 | 3.6 arrastra OkHttp 5.5, que exige compileSdk 37 |
| Lifecycle (JetBrains) | 2.10.0 | 2.11 exige compileSdk 37 |
| Coil | 3.5.0 | 3.6 exige compileSdk 37 |
| GitLive Firebase | 2.7.0 | Se valida en P2 |
| kotlinx-coroutines / serialization / datetime | 1.11.0 / 1.11.0 / 0.8.0 | |
| Detekt | 1.23.8 | Con su Kotlin fijado a 2.0.21 (incompatibilidad conocida con Kotlin ≥ 2.1) |

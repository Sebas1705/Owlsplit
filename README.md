# Owlsplit

App para Android e iOS que lleva los gastos compartidos de grupos (viajes, pisos, cenas, regalos): quién ha pagado qué, cuánto debe cada uno y cómo quedar en paz con el menor número de pagos. Funciona sin conexión y se sincroniza entre todos los miembros.

Inspirada en Settle Up, con un diseño cuidado (estilo fintech limpio) y más funciones por delante: liquidar con un toque, gastos recurrentes, presupuestos y estadísticas, y escaneo de tickets.

> **Estado:** en construcción (P1 completado). Ver [planes](docs/plans/README.md).

## Documentación

| Documento | Contenido |
|---|---|
| [Diseño del núcleo (MVP)](docs/designs/2026-10-07-owlsplit-core-design.md) | Spec completo: alcance, arquitectura, UX, errores, pruebas, CI y criterios de aceptación |
| [Roadmap](docs/ROADMAP.md) | Los cinco subproyectos y su orden |
| [Dominio](docs/DOMAIN.md) | Entidades, reglas de dinero, reparto, divisas, saldos y simplificación de deudas |
| [Sincronización](docs/SYNC.md) | Motor local-first: outbox, listeners, conflictos y borrados |
| [Firebase](docs/FIREBASE.md) | Proyectos, colecciones, reglas, autenticación y borrado de cuenta |
| [Sistema de diseño](docs/DESIGN_SYSTEM.md) | Tokens, tipografía, componentes, movimiento y tono |
| [Decisiones (ADR)](docs/adr/README.md) | Por qué se eligió cada pieza y qué se descartó |
| [Mockup aprobado](docs/mockups/owlsplit-fintech-v3.html) | Inicio, grupo y editor de gasto |

## Stack previsto

Kotlin Multiplatform · Compose Multiplatform · SQLDelight · Koin · Ktor · Firebase (Auth, Firestore) vía GitLive, sobre la plantilla Templetry `kmp/modular-features`.

## Desarrollo

Requisitos: JDK 21 y Android SDK (platform 36). iOS solo compila en macOS.

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
scripts/doppler-sync-local.sh                   # configuración de Firebase (Doppler)
./gradlew :composeApp:installDebug         # app de depuración (es.sebas1705.owlsplit.debug)
./gradlew build                            # tests, detekt, lint y builds
```

Estructura de módulos en [MODULE_MAP.md](MODULE_MAP.md); planes de implementación en [docs/plans](docs/plans/README.md); Firebase en [docs/FIREBASE.md](docs/FIREBASE.md); distribución a testers (Firebase App Distribution) y versiones en [docs/RELEASE.md](docs/RELEASE.md); criterios de aceptación del MVP en [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

iOS: el proyecto de Xcode se genera con XcodeGen (`cd iosApp && xcodegen generate`, en un Mac); CI compila la app para el simulador en cada PR.

## Licencia

Privado. Todos los derechos reservados.

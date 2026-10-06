# Owlsplit

App para Android e iOS que lleva los gastos compartidos de grupos (viajes, pisos, cenas, regalos): quién ha pagado qué, cuánto debe cada uno y cómo quedar en paz con el menor número de pagos. Funciona sin conexión y se sincroniza entre todos los miembros.

Inspirada en Settle Up, con un diseño cuidado (estilo fintech limpio) y más funciones por delante: liquidar con un toque, gastos recurrentes, presupuestos y estadísticas, y escaneo de tickets.

> **Estado:** fase de diseño. El núcleo (MVP) está definido y documentado; todavía no hay código.

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

## Licencia

Privado. Todos los derechos reservados.

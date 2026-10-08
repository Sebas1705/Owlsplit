# P9a · Grupos, gastos y saldos — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** La app sirve para lo que existe: crear un grupo, añadir miembros, apuntar gastos en la divisa del grupo con cualquier tipo de reparto, ver saldos y saldar con un toque. Pantallas de [3.2–3.6 del spec](../designs/2026-10-07-owlsplit-core-design.md#32-grupos) y del [mockup](../mockups/owlsplit-fintech-v3.html).

**Fuera de P9a (van a P9b):** gastos en otra divisa y tipos de Frankfurter, balance total convertido entre divisas, varios pagadores, fechas del viaje y fecha del gasto distinta de hoy, filtros y búsqueda, historial y pestaña Actividad, transferencia manual, borrar y restaurar desde el historial.

**Architecture:**
- Los tipos de entrada (`GroupInput`, `MemberInput`, `ExpenseInput`, `TransferInput`) pasan a `data:models`: las features no pueden ver `data:repository`.
- `domain:usecases`: paquete `groups` (`ObserveHome`, `ObserveGroupDetail`, `GroupActions`, `MemberActions`, `SettleUp`) y `expenses` (`ExpenseDraft` con validación en vivo sobre `ShareCalculator`, `SaveExpense`).
- Tres módulos nuevos: `feature:home` (Inicio), `feature:group` (pantalla de grupo, editor de grupo, miembros) y `feature:expense` (editor de gasto y de reparto). Navegación en `composeApp`.
- `core:ui`: catálogo de iconos de grupo (`GroupIcons`) y de categorías (`CategoryIcons`), fila de gasto (`ExpenseRow`).

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation Compose 2.9.2 (rutas tipadas), Koin, Roborazzi.

**Platform:** KMP (Android verificado en emulador; iOS compila en CI)

**Depende de:** P3 (ledger), P4 (repositorios), P7 (componentes), P8 (perfil y navegación).

---

## Decisiones

1. **Mi miembro** en un grupo = el que tiene `userUid` = mi uid. Mi saldo en Inicio y en la cabecera del grupo es el suyo; si no tengo miembro (caso raro) no se muestra saldo.
2. **Inicio**: balance total = suma de mis saldos en los grupos cuya divisa base es mi divisa preferida; si hay grupos en otras divisas, se indica debajo ("y 12,00 USD en otros grupos"). La conversión llega en P9b. Lista de grupos no archivados por mí, ordenados por última actividad. Acciones rápidas: Gasto (elige grupo si hay varios), Saldar (elige grupo y abre Saldar), Grupo (crear). "Unirse" aparece en P10.
3. **Crear grupo**: nombre, icono (12 iconos), color (8), divisa base (lista corta; la lista completa con el selector de divisas de P9b). El creador entra con el nombre de su perfil.
4. **Miembros**: añadir fantasma (nombre y peso por defecto), editar, desactivar (si tiene movimientos) o borrar (si no tiene). Mi miembro no se puede desactivar ni borrar.
5. **Editor de gasto**: importe con teclado propio, concepto, categoría, pagador (uno; por defecto yo), reparto. Por defecto partes iguales entre los activos. Pesos, importes y porcentajes se editan por miembro con validación en vivo ("faltan 3,20 €"); no se puede guardar si no cuadra. Sobre la regla de restos de `ShareCalculator`, la vista previa de cada parte es exactamente lo que se guardará.
6. **Saldar**: los pagos sugeridos de `DebtSimplifier`; "Saldar" pide confirmación y crea la transferencia por el importe sugerido en la divisa base, con fecha de hoy. Debajo, las transferencias registradas.
7. **Lista de gastos**: agrupada por día, con quién pagó, el importe y mi parte. Tocar abre el editor.
8. **Grupo archivado / salir**: archivar lo quita de Inicio (sigue en la lista de archivados de P9b); salir pide confirmación y vuelve a Inicio.

## Tareas

### Task 1: Base
- [ ] Mover los tipos de entrada a `data:models` (`Inputs.kt`).
- [ ] `core:ui`: `GroupIcons` (clave ↔ icono Tabler, 12), `CategoryIcons` + nombres de categoría, `ExpenseRow`.
- [ ] Commit.

### Task 2: Casos de uso
- [ ] `ObserveHome`, `ObserveGroupDetail`, `GroupActions`, `MemberActions`, `SettleUp`, `ExpenseDraft`/`SaveExpense`.
- [ ] Tests con repositorios falsos (`core:testing`): saldo de mi miembro, total por divisa, validación de cada tipo de reparto, pagos sugeridos → transferencia, no desactivar mi miembro.
- [ ] Commit.

### Task 3: `feature:home`
- [ ] ViewModel + pantalla + capturas (vacío, con grupos, claro/oscuro). Aviso para vincular (de P8) movido aquí.
- [ ] Commit.

### Task 4: `feature:group`
- [ ] Editor de grupo, pantalla de grupo (Gastos · Saldos · Saldar), miembros. ViewModels con tests; capturas.
- [ ] Commit.

### Task 5: `feature:expense`
- [ ] Editor de gasto y de reparto. ViewModel con tests; capturas.
- [ ] Commit.

### Task 6: App
- [ ] Rutas y navegación; Koin. Prueba completa en el emulador: crear grupo, añadir dos miembros, gasto igual, gasto por importes, saldos, saldar.
- [ ] Commit.

### Task 7: Documentación y PR
- [ ] `MODULE_MAP.md`, `DESIGN_SYSTEM.md` (componentes nuevos), índice de planes. `./gradlew build` verde. PR.

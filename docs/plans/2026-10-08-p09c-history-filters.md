# P9c · Historial, actividad, filtros y conflictos — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Cerrar P9: filtros y búsqueda en la lista de gastos ([3.4](../designs/2026-10-07-owlsplit-core-design.md#34-gastos)), historial por grupo con cambios legibles y pestaña Actividad ([3.9](../designs/2026-10-07-owlsplit-core-design.md#39-historial)), restaurar gastos borrados durante 30 días y recuperar la versión perdedora de un conflicto (criterio de aceptación 3 del MVP).

**Architecture:**
- `data`: consultas `selectAllByGroup` (gastos con borrados), `selectRecent` (historial de todos los grupos), `conflict.selectAll/selectById`; `ChangeLogRepository.observeRecent`, `ExpenseRepository.observeExpenses(includeDeleted)` y `ConflictRepository` (reaplicar como una edición normal o descartar). Sin cambios de esquema.
- `domain`: `ExpenseFilter` (búsqueda, persona, categoría), `ObserveHistory` / `ObserveActivity` (entradas con autor, sujeto, divisa y acciones posibles), `RestoreExpense`, `ResolveConflict`.
- `feature:history`: pantalla de historial de un grupo y pestaña Actividad (misma lista); filtros en `feature:groups`.

**Platform:** KMP (Android verificado en emulador; iOS compila en CI)

**Depende de:** P9b.

---

## Decisiones

1. **Filtros**: búsqueda por concepto (sin distinguir mayúsculas ni tildes), persona (pagó o participa) y categoría; se combinan. Viven en el ViewModel del grupo (no se guardan).
2. **Texto de cada entrada**: "Ana añadió «Cena» · 60,00 €", "Luis cambió «Cena»: importe 12,40 € → 14,40 €; concepto «Cena» → «Cena larga»", "Ana borró «Taxi»", "Marta se unió", "Conflicto en «Cena»". Los campos que no se resumen bien (pagadores, reparto, icono, color) aparecen solo por su nombre ("reparto cambiado").
3. **Autor**: el miembro del grupo con ese `userUid`; si ya no está, "Alguien". Mis entradas dicen "Tú".
4. **Restaurar**: en entradas de borrado de gastos, si el gasto sigue borrado y el borrado tiene menos de 30 días.
5. **Conflictos**: en la entrada de conflicto, "Reaplicar mi cambio" (vuelve a escribir mi versión encima de la ganadora, con nueva versión) y "Descartar". Mientras haya conflictos sin resolver, Actividad los muestra arriba.
6. **Actividad**: últimas 200 entradas de todos mis grupos, con el nombre del grupo.

## Tareas

- [ ] Task 1: datos (consultas, `ConflictRepository`) con tests. Commit.
- [ ] Task 2: dominio (`ExpenseFilter`, `ObserveHistory`, `ObserveActivity`, `RestoreExpense`, `ResolveConflict`) con tests. Commit.
- [ ] Task 3: filtros en la pantalla de grupo. Tests y capturas. Commit.
- [ ] Task 4: `feature:history` (grupo y Actividad) con tests y capturas; navegación. Commit.
- [ ] Task 5: prueba en el emulador, documentación, `./gradlew build`, PR.

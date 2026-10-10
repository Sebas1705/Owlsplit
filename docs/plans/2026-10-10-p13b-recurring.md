# P13b · Gastos recurrentes — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Cuarta propuesta del [estudio de mercado](../research/2026-10-09-estudio-de-mercado.md) y punto 3 del [ROADMAP](../ROADMAP.md). Alquiler, suministros o suscripciones se apuntan una vez y se repiten solos cada semana, mes o año, sin servidor y también sin conexión.

**Architecture:**
- `domain`:
  - `Recurrence` (`RepeatUnit`) se guarda en el campo `recurrenceId`, reservado desde el MVP, con la forma `<serie>|<unidad><cada>` (`…|M1`) o `<serie>|stop`. Todos los gastos de una serie comparten el id de serie. Las fechas se calculan desde el primero (el ancla), así que un día 31 no se va desplazando.
  - `GenerateRecurring` añade los gastos que tocan hasta hoy:
    - copia el último gasto, de modo que un alquiler que sube conserva el importe nuevo;
    - cada gasto generado lleva un id fijo `<serie>_<fecha>`;
    - como mucho genera 24 por pasada.
  - `SaveExpense`:
    - "Nunca" marca el gasto con `stop`, y con eso se para toda la serie;
    - otro periodo empieza una serie nueva desde ese gasto y para la anterior en su último gasto.
- `data`:
  - `ExpenseInput.recurrenceId`;
  - `ExpenseRepository.create(…, id)` acepta un id fijo.
  - No cambian el esquema ni las reglas, porque el campo ya existía (≤ 100 caracteres).
- `feature`:
  - fila "Repetir" (Nunca, cada semana, mes o año) en el editor de gastos, con una explicación;
  - "· se repite" en la lista del grupo;
  - el grupo y la pantalla de inicio generan lo pendiente al abrirse.

**Platform:** KMP (Android probado en el emulador; iOS compila en CI).

**Depende de:** P12.

---

## Decisiones

1. **Se genera en el móvil**, no con Cloud Functions, para no necesitar el plan Blaze. Funciona sin conexión y la escritura se sincroniza como cualquier otra.
2. **Sin duplicados.** Dos medidas:
   - solo genera quien creó la serie (si se fue, el miembro con el uid más bajo);
   - los ids son deterministas, así que nada se añade dos veces aunque se ejecute varias veces o en varios móviles de la misma persona.

   Si dos móviles de la misma cuenta generan a la vez sin conexión, la segunda subida cae en el conflicto normal de P6 (mismo contenido) y se resuelve desde el aviso de conflictos.
3. **Parar está a un toque**, en cualquier gasto de la serie. No hace falta una pantalla aparte de plantillas: la plantilla es el último gasto.
4. **Si la serie no tiene gastos visibles, no se genera.** Si se borran todos los gastos de una serie, se considera terminada. Si solo se borra uno, el siguiente se sigue generando.

## Tareas

- [x] Task 1: `Recurrence`, `GenerateRecurring` y los cambios de `SaveExpense`, con tests (meses de 31 días, una sola vez, parar, cambiar de periodo, solo el creador). Commit.
- [x] Task 2: `ExpenseInput.recurrenceId` y el id fijo en los repositorios. Commit.
- [x] Task 3: fila "Repetir", "se repite" en la lista, generación al abrir el grupo y el inicio, y textos en es/en. Prueba en el emulador: un alquiler mensual con fecha de hace dos meses genera septiembre y octubre al volver. Commit.

# P12a · Saldar con un toque (Bizum, PayPal, Revolut) — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** La primera propuesta del [estudio de mercado](../research/2026-10-09-estudio-de-mercado.md): que pagar lo que debes esté a un toque. Cada persona puede publicar en su perfil cómo quiere cobrar (Bizum, PayPal.me, Revolut). Quien le debe dinero ve esos métodos al saldar. Quien cobra puede enviar un mensaje pidiendo el pago con sus datos.

**Architecture:**
- `data:models`: `PayMethods(bizum, paypal, revolut)`. `UserProfile.payMethods`. `Member.payMethods` (nulo si no hay).
- `data:local`: columna `member_entity.pay_methods` (JSON), con la migración `1.sqm` y la instantánea `2.db`. Nueva consulta `selectByUser`.
- `data:repository`: `MemberRepository.publishMyPayMethods` copia los métodos a mis miembros de todos los grupos. Se sincroniza, pero **no entra en el historial**: es un dato personal, no un cambio del grupo. Al salir de un grupo, mi miembro fantasma pierde los métodos.
- `data:remote`: `payMethods` en `MemberDto` (opcional al leer documentos anteriores) y en `users/{uid}`, para restaurarlo en otro móvil.
- `firebase/firestore.rules`: `payMethods` es un mapa con forma fija (`bizum` ≤ 20, `paypal` y `revolut` ≤ 40). Solo lo publica la persona del miembro (`userUid == uid()`); cualquiera puede borrarlo (al liberar). Tests en `subcollections.test.mjs`.
- `domain`: `PayLinks` limpia lo que se escribe (`@ana`, `paypal.me/ana` → `ana`; teléfono sin espacios) y construye los enlaces (`https://paypal.me/ana/12.50EUR`, `https://revolut.me/ana`). `SaveProfile`, `GroupActions.create` y `ClaimMember` publican los métodos.
- `feature:profile`: sección "Cómo te pagan", con validación por campo.
- `feature:groups`: `SettleDialog` sustituye a la confirmación de saldar:
  - Si pago yo y el otro tiene métodos: "Pagar con…". PayPal y Revolut abren su enlace. Bizum copia el teléfono, porque no tiene enlace público.
  - Si cobro yo: "Pedir el pago" comparte un mensaje con mis datos. Si no tengo datos, una pista lleva al perfil.
  - En todos los casos el pago se registra a mano con "Registrar". La app nunca ve mover el dinero.

**Platform:** KMP (Android probado en emulador; iOS compila en CI). `LocalUriHandler` y `LocalClipboardManager` funcionan en las dos plataformas, y el mensaje usa el `TextSharer` existente.

**Depende de:** P11.

---

## Decisiones

1. **Sin integración de pagos.** Ni Bizum ni los bancos ofrecen una API pública para iniciar un pago desde otra app. Abrir el servicio y registrar después evita licencias de pago (PSD2) y no toca dinero.
2. **Los datos viven en el miembro**, no en `users/{uid}`, porque las reglas solo dejan leer el perfil a su dueño. Copiarlos a cada grupo los hace visibles justo a quien los necesita.
3. **Fuera del historial.** Un teléfono no debe quedarse para siempre en el registro de cambios. Además, una edición propia sin diferencias se lee como "salió del grupo" en las entradas antiguas (P9c), así que estas escrituras no generan entrada.
4. **Compatibilidad:** las reglas aceptan documentos sin `payMethods`, y las escrituras usan `merge = true`, así que una versión anterior no borra los métodos. Las versiones anteriores a esta no leen el campo nuevo, pero solo hay testers con la última build.

## Tareas

- [x] Task 1: modelo, columna y migración, DTO, reglas y sus tests. Commit.
- [x] Task 2: `publishMyPayMethods`, liberar al salir, `PayLinks` y casos de uso, con tests. Commit.
- [x] Task 3: perfil ("Cómo te pagan") y `SettleDialog`, con textos en es/en y prueba en el emulador. Commit.

# 0006 · Dirección visual fintech limpia

- **Estado:** aceptada · 2026-10-07

## Contexto
El motivo de crear Owlo es que el diseño de Settle Up da mucho que desear. Se exploraron tres direcciones con mockups:
1. *Desenfadada con carácter*: colores vivos por grupo y mascota ilustrada. Se descartó por poco seria.
2. *Sobria con personalidad*: índigo y Manrope. Correcta, pero menos distintiva.
3. **Fintech limpia** (estilo Revolut/Monzo). **Elegida.**

## Decisión
Cifras protagonistas, fondo gris claro con contenedores blancos (negro y grafito en oscuro), un único acento azul eléctrico, botones de acción circulares, píldoras, verde solo para saldos a favor y deudas en color neutro. Owlo queda como logotipo geométrico, no como mascota. Tokens y componentes en [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md); mockup en [mockups/owlo-fintech-v3.html](../mockups/owlo-fintech-v3.html).

## Consecuencias
- Un tema propio (`OwloTheme`) sobre Material 3 y un catálogo de componentes en `core:ui` con capturas en claro y oscuro.
- La personalidad sale de la tipografía de los importes, del movimiento y de la claridad, no de la decoración.

# 0006 · Clean fintech visual direction

- **Status:** accepted · 2026-10-07

## Context
The reason for creating Owlsplit is that Settle Up's design leaves a lot to be desired. Three directions were explored with mockups:
1. *Playful with character*: vivid per-group colors and an illustrated mascot. Rejected as not serious enough.
2. *Sober with personality*: indigo and Manrope. Fine, but less distinctive.
3. **Clean fintech** (Revolut/Monzo style). **Chosen.**

## Decision
Numbers in the spotlight, light gray background with white containers (black and graphite in dark), a single electric blue accent, circular action buttons, pills, green only for balances in your favor and debts in a neutral color. The Owlsplit owl stays as a geometric logo, not a mascot. Tokens and components in [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md); mockup in [mockups/owlsplit-fintech-v3.html](../mockups/owlsplit-fintech-v3.html).

## Consequences
- A custom theme (`OwlsplitTheme`) on top of Material 3 and a component catalog in `core:ui` with light and dark screenshots.
- Personality comes from the typography of the amounts, from motion and from clarity, not from decoration.

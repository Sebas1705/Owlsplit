# Design system

**Clean fintech** direction (Revolut/Monzo style), decided in [ADR 0006](adr/0006-fintech-visual-direction.md). Reference mockup: [mockups/owlsplit-fintech-v3.html](mockups/owlsplit-fintech-v3.html).

## Principles

1. **Numbers rule.** The relevant amount or balance is always the largest thing on the screen.
2. **A single accent.** Blue is reserved for the main action and selection. Everything else is neutral.
3. **Green for the good, never red for debts.** What you are owed is green; what you owe is in the normal text color with a "−" sign. No alarmism.
4. **Containers, not loose cards.** Lists as rows inside rounded containers on a light gray background (or graphite on black).
5. **Light and dark are first-class.** Every screen is designed and tested in both.

Built on Compose Multiplatform **Material 3** with a custom `OwlsplitTheme` (`core:ui`): M3 components receive the tokens below and custom components only use tokens, never literal colors.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#F2F3F7` | `#0B0C10` | Screen background |
| `surface` | `#FFFFFF` | `#16181F` | Containers, bottom bar |
| `surfaceVariant` | `#E4E6EE` | `#1C1E26` | Search field, segmented control, chips |
| `segmentSelected` | `#FFFFFF` | `#2C303B` | Active option of the segmented control (lighter than its track in both themes) |
| `divider` | `#ECEEF3` | `#22252E` | Separators and bar tracks |
| `textPrimary` | `#0B0C10` | `#F5F6FA` | Text and amounts; debts too |
| `textSecondary` | `#5B5F6B` | `#8A8E99` | Subtitles, labels |
| `textMuted` | `#696D78` | `#80848F` | Hints, placeholders and metadata; AA contrast (≥ 4.5:1) on background and surface (P11a) |
| `accent` | `#3B4BF5` | `#3B4BF5` | Main button, selection, included avatars |
| `onAccent` | `#FFFFFF` | `#FFFFFF` | Text on `accent` (6:1 contrast) |
| `accentText` | `#3B4BF5` | `#8C96FF` | Links and accent text on surfaces |
| `accentContainer` | `#E8EBFF` | `#232750` | Background of action icons and of the default group icon |
| `positive` | `#087A54` | `#34C38F` | Balances in your favor (contrast ≥ 4.5:1 in both themes) |
| `positiveFill` | `#0E9F6E` | `#34C38F` | Bars for balances in your favor (graphic element) |
| `negativeFill` | `#0B0C10` | `#F5F6FA` | Bars for balances against you |
| `warning` | `#9A5B00` | `#F2B84B` | Pending sync, conflicts |
| `error` | `#C4372B` | `#FF7A6E` | Only validation or system errors, never debts |
| `brandBeak` | `#E8B04B` | `#E8B04B` | Logo only |

**Group accents** (icon background / icon color), in light: blue `#E8EBFF/#3B4BF5`, orange `#FFF1E5/#D9661A`, violet `#F3E8FF/#8B3FD9`, green `#E3F5EE/#087A54`, pink `#FDE8F0/#C2306A`, amber `#FFF4D6/#9A6400`, cyan `#E0F4FA/#0A7893`, graphite `#ECEEF3/#3A3E4A`. In dark, the background is the color at 18 % over `surface` and the icon one shade lighter. They only tint the group icon, never the screen.

## Typography

**Inter** (OFL, embedded in `core:ui`) with tabular figures (`fontFeatureSettings = "tnum"`) on every amount.

| Style | Size / weight | Use |
|---|---|---|
| `amountDisplay` | 44 sp / 700, tracking −3 % | Editor amount, Home balance |
| `amountHeadline` | 30 sp / 700, tracking −3 % | Balance in the group header |
| `title` | 17 sp / 600 | Screen titles |
| `bodyStrong` | 14 sp / 600 | Names in rows, amounts in rows |
| `body` | 14 sp / 400 | General text |
| `label` | 12 sp / 500 | Secondary buttons, chips, tabs |
| `caption` | 11 sp / 400 | Metadata ("5 personas · hace 2 h" — "5 people · 2 h ago") |

In large amounts the decimal part is shown at 60 % of the size (and in `textSecondary` in the editor), as in the mockup.

## Shape and spacing

- Radii: containers **16 dp**, segmented controls and chips **12 dp**, main buttons as **pills**, quick action buttons and avatars **circular**.
- Spacing scale: 4 · 8 · 12 · 16 · 24 · 32 dp. Screen side margin 16 dp; containers 12 dp from the edge.
- List rows: 56 dp minimum height; 36 dp avatar or icon.
- Touch targets ≥ 48 dp even if the visible element is smaller.
- No shadows: depth comes from the contrast between `background` and `surface`.
  - Dropdown menus (`OwlsplitDropdownMenu`): no shadow, 1 dp border in `divider` and control corners (12 dp); the chosen option has a ✓ (`OwlsplitMenuItem(selected = …)`).
  - Dialogs: 24 dp corners (`OwlsplitShapes.dialog`, Material's `extraLarge`). Buttons never elevate, not even on hover or focus.
  - **Press shadow**: follows the visible shape. Containers clip their rows (the whole row lights up and respects the corners); pills, chips and keys clip to their shape (`clip` before `clickable`); when the touch area is larger than the shape (circle with a label: quick actions, avatars, colors, group icons) `rememberPress()` + `pressFeedback(press, CircleShape)` is used so the shadow stays on the circle. Side padding goes inside the pressable area, never outside.
  - **Critical actions with double confirmation**: recording a payment (settle or manual) and leaving a group ask for a second confirmation; leaving also warns about the remaining balance. Payments can be deleted from «Pagos registrados» ("Recorded payments") and restored from the history (30 days).
  - The theme fills every Material container with `surface` and removes the tonal tint (`surfaceTint = surface`): no Material component introduces a color of its own.
- Main action pinned at the bottom (`BottomActionBar`): `background` fill and a `divider` line on top only when the list continues below, so nothing looks cut off.
- Horizontal lists inside a column with margin (`Modifier.bleedHorizontally`): they reach the screen edge and keep the margin as padding; they open on the chosen item.
- Switches (`SwitchRow`): the whole row toggles; off with `surfaceVariant` track and `textMuted` border, on in `accent`.
- Member colors (`memberAccent`): the one from their profile or a stable one derived from the id; the same in Members, Balances and "¿Quién eres?" ("Who are you?").
- Inline text buttons next to content: shifted by their internal padding (12 dp) so the label lines up with the text or the cards.

## Iconography

**Tabler Icons** in outline style (MIT), via the KMP port `compose-icons`. Stroke 1.75–2 px, sizes 20 and 24 dp. Expense categories and group icons come from the same set.

## Logo

Geometric owl: rounded square in `accent` with two white rings for eyes and a beak in `brandBeak`. Uses: app icon, splash, empty states and welcome screen. It does **not** appear as an illustrated mascot inside working screens. Geometry, files and icons: [BRAND](BRAND.md); the colors on this page come from `brand/tokens.json`.

## Components (`core:ui`)

| Component | Description |
|---|---|
| `BalanceHero` | Large centered balance, label and sync status chip |
| `QuickActionButton` | 48 dp circular button on `surface` with a label below |
| `OwlsplitContainer` | 16 dp `surface` container with optional title |
| `GroupRow` | Group icon, name, metadata and colored balance |
| `SegmentedControl` | `surfaceVariant` pill with the active option in `segmentSelected` |
| `MemberBalanceRow` | Avatar, name, thin proportional 4 dp bar and balance |
| `SettleSuggestionRow` | "X te debe Y" ("X owes you Y") with a "Saldar" ("Settle") pill button |
| `AmountDisplay` | Amount with reduced decimals and currency picker |
| `AmountKeypad` | Custom numeric keypad with locale decimal separator and delete |
| `AvatarToggle` | Avatar that toggles included (`accent`) / excluded (`surfaceVariant`) |
| `SyncStatusChip` | Synced · N pending · Offline · Error |
| `PrimaryPillButton` | Wide main pill button, `accent` |
| `CompactPillButton` | Pill as wide as its label, for a primary action inside a card |
| `CalloutCard` | Suggestion card (icon, title, text, "not now" + action aligned to the end): link your account, a new version |
| `EmptyState` | Logo, headline, one line and an optional action |
| `ProfileAvatar` | Initials on the chosen color (`accent:<COLOR>`); from 64 dp up the initial uses the `title` style |
| `OwlsplitTextField` | Single-line field on `surface`, label above and error below |
| `TextActionButton` | Secondary text action (`accentText` or the color passed in, e.g. `error` in destructive dialogs) |
| `ExpenseRow` | Category icon, description, who paid, amount and "tu parte" ("your share") |
| `GroupIcons`, `categoryIcon`, `categoryName` | The 12 group icons (key stored in `Group.icon`) and the icons and names of the 11 categories |
| `CurrencyPicker` | Dialog with all supported currencies and search by code |
| `DateField` | Date row that opens the Material 3 picker (days in UTC so they don't shift) |
| `AccentPicker` | Row of the 8 `GroupAccent` colors, each with a name for screen readers (`accentName`) |
| `Avatar` | Photo or initials on a neutral tone; 24, 30, 36 and 48 dp |
| `OwlsplitLogo` | The geometric owl drawn in Canvas (no images) |
| `AmountText` | Amount with decimals at 60 % and a number that rolls on change (`animate = false` disables it) |
| `MoneyFormat` | Amount formatting without platform APIs: `Spanish` (1.284,60 €) and `English` (€1,284.60), minus sign U+2212 |

Components carry no hard-coded text: they receive already localized strings. Implementation in `core:ui` and live catalog in `DesignCatalog` (screenshots in `core/ui/screenshots/`).

Every component has a light and dark preview and a screenshot test.

## Motion

- Changing balances: the number **rolls** to the new value (300 ms, deceleration).
- Settle: the row slides out and disappears and the header balance rolls to its new value; light haptic confirmation.
- Standard platform screen transitions (native back gesture on iOS).
- With "reduce motion" on: instant changes, no rolling or sliding. Compose Multiplatform does not expose that preference in common code; it is wired per platform in P11 (`AmountText(animate = false)` already supports it).

## Text tone

Friendly and direct, in the second person and without jargon: "Te deben 23,50 €" ("You are owed €23.50"), "Luis te debe" ("Luis owes you"), "Todo saldado" ("All settled"), "1 cambio pendiente" ("1 pending change"). No exclamation marks in system messages. Errors: what happened and what to do ("Los importes no suman el total. Faltan 3,20 €." — "The amounts don't add up to the total. €3.20 missing.").

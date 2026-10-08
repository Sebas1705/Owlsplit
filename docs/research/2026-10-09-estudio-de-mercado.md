# Estudio de mercado y revisión de funciones (2026-10-09)

Qué ofrece Owlsplit hoy (v1.0.0-beta.2), qué ofrece la competencia y qué conviene añadir. Fuentes al final; las cifras de precios y límites vienen de fichas de tiendas, reseñas y blogs (muchos de competidores), así que son orientativas.

## Resumen

- **Mercado maduro y en crecimiento moderado**: los informes sitúan las apps para dividir gastos entre ~0,6 y ~1,3 mil millones de USD en 2025, con crecimientos anuales del 7 % al 15 % según la fuente. Las cifras discrepan mucho; sirven solo como orden de magnitud.
- **Splitwise sigue siendo la referencia, pero su versión gratuita molesta**: límite diario de gastos (≈3–5, no publicado), anuncios con espera y funciones básicas tras Pro (~4,99 $/mes o 39,99 $/año). Es el motivo más citado para cambiar de app.
- **El rival real en España y Europa es Tricount**: gratis, sin anuncios ni límites, sin registro, web para quien no instala nada; desde 2022 es de bunq, que lo usa para captar clientes del banco.
- **Los bancos entran**: Bizum (grupos de hasta 30 personas, reparto por porcentajes y pago integrado), BBVA y Revolut (Group Bills, también para no clientes) ya dividen gastos dentro de su app. Su punto débil: obligan a un banco concreto y no sirven para gastos en otras monedas ni para grupos mixtos.
- **La ola de 2025–2026 es la IA en tickets**: escaneo y reparto línea a línea (Splitwise Pro, SplitMyExpenses, Spliit con OpenAI y una docena de apps nuevas).
- **Dónde gana Owlsplit**: gratis y sin límites, sin anuncios, sin registro, funciona 100 % sin conexión con conflictos resueltos, multidivisa con tipo congelado, historial con restaurar, accesible y en español e inglés. Pocos combinan offline real, sin cuenta y multidivisa sin pagar.
- **Qué le falta frente al mercado**: saldar con un toque (Bizum/PayPal/Revolut), recordatorios y avisos, gastos recurrentes, tickets (foto, escaneo, reparto por líneas), estadísticas y exportación, web para invitados e importar desde otras apps.

## Competidores

| App | Modelo | Lo gratuito | Lo de pago | Notas |
|---|---|---|---|---|
| **Splitwise** | Freemium con límite | Grupos ilimitados, repartos estándar, simplificar deudas; anuncios y límite diario de gastos | Pro (~4,99 $/mes, 39,99 $/año): escaneo y reparto de tickets, conversión de moneda, gráficos, búsqueda, exportar, sin límite | Líder; reseñas negativas por el límite desde 2023; pagos integrados solo en EE. UU. (Venmo/PayPal) e India (Paytm) |
| **Tricount** (bunq) | Gratis | Todo: sin anuncios ni límites, sin registro, multidivisa, web para invitados, sin conexión | Premium retirado | Gastos de la tarjeta bunq se añaden solos; app de captación del banco |
| **Settle Up** | Freemium con anuncios | Grupos, saldos, mínimo de transferencias, avisos de cambios | Premium (~19,99 $/año o 3,99 $/mes; o por grupo, de 5,99 $/semana a 149,99 $ de por vida): sin anuncios, fotos de tickets, categorías, recurrentes, programados | Web y Windows además del móvil |
| **Splid** | Gratis + compras | Sin cuenta (código de grupo), sin conexión, +150 monedas, PDF | Más grupos y Excel | Muy usada en viajes |
| **Spliit** | Código abierto (web/PWA) | Grupos por enlace sin cuentas, categorías, CSV | Escaneo con IA (cuesta OpenAI a quien lo aloja) | Vive de donaciones |
| **SplitMyExpenses** y apps de IA | Freemium | Repartos y saldos | Reparto de tickets con IA, resúmenes y categorías automáticas | Importa desde Splitwise |
| **Bizum / BBVA / Revolut** | Gratis (banca) | Grupos, reparto, pago integrado | — | Atados a su banco o red; Revolut admite no clientes por web |
| **Flatastic** (pisos) | Freemium | Tareas, lista de la compra, gastos | Sin anuncios, histórico, CSV, fotos | Otro producto: organización del piso |
| **Honeydue** (parejas) | Gratis con anuncios | Cuentas conjuntas, recordatorios, chat | — | Otro producto: finanzas de pareja con banco |

## Owlsplit hoy

| Función | Owlsplit | En el mercado |
|---|---|---|
| Gastos y grupos sin límite, sin anuncios | ✅ | Tricount, Splid; Splitwise limita, Settle Up muestra anuncios |
| Unirse sin registro (enlace o QR) | ✅ | Tricount, Splid, Spliit, Revolut (web) |
| Funciona sin conexión y converge | ✅ con conflictos recuperables | Tricount, Splid, Settle Up (según reseñas) |
| Cuatro tipos de reparto, varios pagadores, pesos | ✅ | Habitual; Splitwise Pro para algunos |
| Multidivisa con tipo congelado (BCE) | ✅ gratis | Splitwise solo en Pro; Splid, Tricount sí |
| Simplificar deudas | ✅ | Todos |
| Historial con restaurar y conflictos | ✅ | Poco común (Settle Up tiene historial) |
| Miembros sin cuenta que luego se reclaman | ✅ | Splitwise/Tricount de forma parecida |
| Accesibilidad (AA, texto 200 %, lectores) e idiomas es/en | ✅ | No suele anunciarse |
| **Saldar con Bizum / PayPal / Revolut** | ❌ (roadmap 2) | Bizum/BBVA/Revolut integran el pago; Splitwise solo EE. UU./India |
| **Recordatorios y avisos push** | ❌ (roadmap 2) | Settle Up, Splitwise, Flatastic |
| **Gastos recurrentes** | ❌ (roadmap 3) | Splitwise, Settle Up Premium, SplitMyExpenses |
| **Estadísticas y exportar (CSV/PDF)** | ❌ (roadmap 4) | Splitwise Pro, Splid (PDF), Spliit (CSV) |
| **Foto y escaneo de tickets, reparto por líneas** | ❌ (roadmap 5) | Splitwise Pro, SplitMyExpenses, Spliit, apps de IA |
| **Web para quien no instala la app** | ❌ (solo la página de invitación) | Tricount, Spliit, Revolut |
| **Importar desde Splitwise / Tricount** | ❌ (no estaba en el roadmap) | SplitMyExpenses importa desde Splitwise |
| iPhone | Compila en CI; falta cuenta de Apple | Todos |

## Propuesta

**Posicionamiento**: *la app para dividir gastos que no te pone límites ni te pide nada* — gratis de verdad, sin cuenta, sin anuncios, sin banco detrás, que funciona en el avión y hecha en España (Bizum). Frente a Tricount la diferencia no puede ser el precio (también es gratis): está en el diseño, el sin conexión fiable con historial, la privacidad (no es la puerta de entrada de un banco) y en cubrir lo que hoy piden por separado (pagar, recordar, tickets).

**Prioridades** (impacto alto y esfuerzo contenido primero):

| # | Qué | Por qué | Esfuerzo | Necesita |
|---|---|---|---|---|
| 1 | **Saldar con un toque**: Bizum (copiar importe y teléfono, abrir la app del banco), PayPal.me, revolut.me; métodos de cobro en el perfil | El momento de saldar es donde los bancos ganan; en España todo el mundo paga con Bizum | S–M | Nada (enlaces y portapapeles) |
| 2 | **Exportar CSV/PDF y estadísticas básicas** (por persona, categoría, día; media del viaje) | Splitwise lo cobra; cierra viajes y pisos; todo es local | M | Nada |
| 3 | **Importar desde Splitwise (CSV)** y, si se puede, Tricount | Quita el coste de cambiar justo cuando Splitwise enfada a sus usuarios | M | Nada |
| 4 | **Gastos recurrentes** (alquiler, suministros, suscripciones) | Imprescindible para pisos y parejas | M | Generación en el cliente con id determinista |
| 5 | **Avisos y recordatorios** (nuevo gasto, te deben, te han pagado) | Retención: sin avisos la app se olvida | M–L | Plan Blaze, FCM y Cloud Functions |
| 6 | **Tickets**: foto adjunta, escaneo en el dispositivo (ML Kit / Vision) y reparto por líneas | Lo que más crece; en el dispositivo es gratis y privado frente a la IA en la nube | L | Cloud Storage (Blaze) para las fotos |
| 7 | **Web para invitados**: ver saldos y añadir un gasto desde el enlace sin instalar | Tricount, Spliit y Revolut lo tienen; convierte el enlace en uso inmediato | L | Hosting + web (Compose para Web o una web ligera) |
| 8 | iPhone en la App Store (Apple, TestFlight) | Media España usa iPhone | M | Cuenta de Apple Developer |

**No recomendado por ahora**: tareas y lista de la compra del piso (Flatastic) y finanzas de pareja con cuentas bancarias (Honeydue): son otros productos y diluyen el foco; conectar cuentas bancarias implica regulación (PSD2).

**Modelo de negocio** (para decidir más adelante): mantener gratis y sin límites todo lo que hoy es gratis (es la ventaja frente a Splitwise). Si hace falta cubrir costes (Blaze: avisos y fotos), una suscripción opcional para lo que cuesta dinero (fotos de tickets en la nube, escaneo avanzado) o un pago único por grupo, como Settle Up, sin anuncios.

## Fuentes

- Splitwise, precios y límites: [getfinny.app — Splitwise pricing 2026](https://getfinny.app/blog/splitwise-pricing-2026), [getfinny.app — alternativas 2026](https://getfinny.app/blog/best-splitwise-alternatives-2026), [Trustpilot](https://au.trustpilot.com/review/splitwise.com), [Yellow Slice — review UX](https://www.yellowslice.in/blog/splitwise-app-review-ux-ui-improvements), [Kimola — análisis de reseñas](https://kimola.com/reports/splitwise-feedback-analysis-unlock-user-sentiments-google-play-154450)
- Splitwise, funciones: [APKMirror — Splitwise 26.6.3](https://www.apkmirror.com/apk/splitwise/splitwise/splitwise-26-6-3-release/), [APKMirror — Splitwise 24.6.1](https://www.apkmirror.com/?p=6558120)
- Tricount: [App Store](https://apps.apple.com/us/app/-/id1021178150), [TechCrunch — compra por bunq](https://techcrunch.com/2022/05/03/bunq-to-acquire-group-expenses-app-tricount/amp), [tricount.com](https://tricount.com/update)
- Settle Up: [App Store](https://apps.apple.com/app/id737534985), [APKMirror — 10.0.990](https://apkmirror.com/apk/step-up-labs/settle-up-group-expenses/settle-up-group-expenses-10-0-990-release)
- Splid y Spliit: [Splid — App Store](https://apps.apple.com/gb/app/splid-split-group-bills/id991473495), [Spliit — GitHub](https://github.com/spliit-app/spliit), [Spliit — Open Collective](https://opencollective.com/spliit)
- Comparativas: [SplitMyExpenses — mejores apps 2026](https://www.splitmyexpenses.com/articles/best-bill-splitting-apps), [Are We Even](https://www.areweeven.com/blog/best-splitwise-alternatives), [Product Hunt](https://www.producthunt.com/products/splitwise/alternatives), [Dineo — apps para compartir gastos 2026](https://www.dineo.es/blog/mejores-app-compartir-gastos/)
- Banca: [Revolut — Group Bills](https://help.revolut.com/en-AU/help/app-features/splitting-expenses-with-group-bills/), [Xataka Android — app de Bizum](https://www.xatakandroid.com/aplicaciones-android/probamos-app-independiente-bizum-envios-dinero-multiples-grupos-app-banco), [BBVA — compartir gastos](https://www.bbva.es/general/app-compartir-gastos.html)
- IA y tickets: [SplitMyExpenses — Callin](https://callin.io/ai-tools/splitmyexpenses), [Split – Share with Ease](https://appshunter.io/ios/app/split-share-with-ease/id6502971930)
- Otros segmentos: [Flatastic Premium](https://www.flatastic-app.com/en/premium/), [Finny — apps para parejas](https://getfinny.app/blog/budgeting-app-for-couples)
- Tamaño de mercado: [GII / SkyQuest](https://www.giiresearch.com/report/sky2078683-bill-splitting-apps-market-size-share-growth.html), [Lucintel](https://www.lucintel.com/es/bill-splitting-app-market.aspx)

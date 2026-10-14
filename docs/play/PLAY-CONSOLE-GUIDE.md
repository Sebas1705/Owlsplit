# Guide: publishing Owlsplit on Google Play (manual steps)

What **only the account owner can do** in Play Console and Google Cloud. What can be automated (AAB upload from CI, web pages, listing text and graphics) is prepared by Claude in the repo.

- App: **Owlsplit** · package `es.sebas1705.owlsplit` · free, no ads or purchases.
- Developer account: **personal** (it has already published another app).
- Checked in the repo on 2026-10-13: `minSdk 24`, `targetSdk 36`, `compileSdk 36` (`build-logic/.../KotlinMultiplatformExtensions.kt`).
- Anything marked **[unverified]** could not be confirmed in the official documentation: trust what the Console says.

Play Console labels are given in English, with the Spanish label in parentheses.

---

## Checklist (in this order)

- [ ] **1.** Create the app in Play Console
- [ ] **2.** Signing: set up Play App Signing with **our** release key (before uploading anything)
- [ ] **3.** App content: privacy, account deletion, ads, access, rating, audience, data safety, financial features and remaining declarations
- [ ] **4.** Play Store listing (text and graphics that Claude leaves in the repo)
- [ ] **5.** Service account for the Play API → JSON in Doppler (`PLAY_SERVICE_ACCOUNT_JSON`) → **tell Claude**
- [ ] **6.** First AAB **uploaded by hand** to *Closed testing (Prueba cerrada)*
- [ ] **7.** Testers via a Google Group: 12 or more who stay opted in for 14 days in a row
- [ ] **8.** Wait the 14 days (without anyone opting out)
- [ ] **9.** Apply for production access

> Don't use fake or purchased tester accounts. It goes against Play policies and can end in account suspension. You need 12 real people: friends, family or the current App Distribution testers.

---

## Phase 1. Create the app

**Where:** Play Console → *Home (Inicio)* → **Create app (Crear aplicación)**.

| Field | Value |
|---|---|
| App name (Nombre de la aplicación) | `Owlsplit` |
| Default language (Idioma predeterminado) | Spanish (Spain) – es-ES (Español (España) – es-ES) |
| App or game? (¿Aplicación o juego?) | App (Aplicación) |
| Free or paid? (¿Gratuita o de pago?) | Free (Gratuita) (it **cannot** be changed to paid later) |
| Declarations (Declaraciones) | Tick the boxes for the Developer Program Policies and US export laws. You have to accept them yourself. |

When the app is created, Play automatically registers the package name and links it to your account. That also counts for the new *Android developer verification* (see Phase 10). The package is fixed once you upload the first AAB.

---

## Phase 2. App signing: use OUR key (critical)

### Why

The APKs from GitHub Releases, `owlsplit.web.app/apk` and Firebase App Distribution are signed with the release key from Doppler (`SIGNING_*`, alias `owlsplit`, RSA 4096, SHA‑256 `99:0B:AD:25:…:18:C3`). That same fingerprint appears in:

- `firebase/hosting/.well-known/assetlinks.json` (the `/j/…` invitation links open in the app);
- the release Android app in Firebase (Google sign-in with Credential Manager).

If Play generated its own key, the Play version would be signed with a different key. This would happen:
1. Whoever installed the APK couldn't update from Play (and vice versa): Android rejects an update with a different signature, so they would have to uninstall and lose local data.
2. Google's fingerprint would have to be added to `assetlinks.json` and Firebase. If forgotten, App Links and Google sign-in break.

With our key as the *app signing key*, Google re-signs with the same key and everything keeps working.

> **Warning:** according to the current help, new apps are enrolled by default in "hybrid" signing with Google-generated keys ("quantum-ready hybrid signing") **[unverified in the UI]**. The key can only be changed **before** a release reaches open testing or production. Do it before the first AAB.

### How

1. **Where:** when creating the first release (Phase 6), in the **App integrity (Integridad de la app)** section tap **Change app signing key (Cambiar clave de firma de apps)**. It is also under *Protected with Play → Play Store distribution → Go to Play App Signing → Change app signing key (Protegido con Play → Distribución en Play Store → Ir a Play App Signing → Cambiar la clave de firma de apps)*.
2. Choose **"Export and upload a key from Java keystore" ("Exportar y subir una clave de un almacén de claves Java")**. The Console offers two downloads: **`pepk.jar`** and **`encryption_public_key.pem`**.
3. Extract the keystore from Doppler to a temporary folder **outside the repo** (or use your copy `Documents\owlsplit-release.jks`):

   ```bash
   # Git Bash. Write the .jks outside the repo and delete it when done.
   doppler secrets get SIGNING_KEYSTORE_BASE64 --project owlsplit --config prd --plain | base64 -d > ~/owlsplit-release.jks
   doppler secrets get SIGNING_KEY_ALIAS --project owlsplit --config prd --plain   # should be: owlsplit
   ```

4. Run PEPK. **Copy the command the Console shows you**, that's the one that counts. It will look something like this (with placeholders, all on a single line):

   ```bash
   java -jar pepk.jar --keystore=~/owlsplit-release.jks --alias=owlsplit --output=owlsplit-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```

   It will ask for the store password and the key password (`SIGNING_STORE_PASSWORD` and `SIGNING_KEY_PASSWORD` from Doppler; they are the same). Type them yourself when prompted and don't paste them into the command.
   - On Windows, if it fails, check that the command has no line breaks.
   - `--output` must be a `.zip` **file**, not a folder.
5. Upload `owlsplit-key.zip` in the Console and tap **Save (Guardar)**.
6. **Check:** in *Play App Signing → App signing key certificate (Certificado de la clave de firma de apps)*, the SHA‑256 must start with `99:0B:AD:25` and end with `18:C3`. If it doesn't match, **don't upload any AAB** and tell Claude.
7. **Upload key:** CI signs the AAB with the same key, so the upload key will be the same as the signing key. Google recommends they be different, but it's allowed. If you later want a separate upload key, request an *upload key reset (restablecimiento de la clave de subida)* and Claude adapts CI.
8. Delete the temporary `~/owlsplit-key.zip` and `~/owlsplit-release.jks` (`rm`). Make sure you have a copy of the `.jks` **off the PC**: this key cannot be recovered.

**Tell Claude:** "Play App Signing with our key: SHA‑256 matches" (or the fingerprint shown if it doesn't match).

---

## Phase 3. App content (Policy → App content)

**Where:** left menu → *Monitor and improve (Supervisar y mejorar)* → **Policy and programs → App content (Política y programas → Contenido de la app)**. Complete each card.

### 3.1 Privacy policy
- URL: `https://owlsplit.web.app/privacidad` **(created by Claude; check it is published before submitting)**.

### 3.2 Ads
- Does it contain ads? **No.**

### 3.3 App access
- **"All functionality is available without special access" ("Todas las funciones están disponibles sin acceso especial")**.
- Reason, checked in the code: on first launch an anonymous Firebase account is created automatically (`signInAnonymously`), onboarding can be skipped (the name is optional) and linking Google is optional. There is no mandatory sign-in screen. Note: internet is needed to sync.

### 3.4 Advertising ID
- Does it use the advertising ID? **No.** The merged release manifest does not include `com.google.android.gms.permission.AD_ID` (Firebase Analytics is not used).

### 3.5 Content rating (IARC)
- Contact email: yours.
- Category: **"All other app types" ("Todos los demás tipos de aplicaciones")** (utility or productivity; not a game, social network or aggregator) **[exact name unverified]**.
- Answers:
  - Violence, fear, sexuality, crude language, controlled substances, crude humor: **No** to all.
  - Gambling or simulated gambling: **No**.
  - **Can users interact or exchange content?** **Yes.** Group members see the names, expense notes and payment details others write. They only share with people invited by link or QR; there is no chat or public content.
  - Does it share the user's location with other users? **No.**
  - Does it allow purchasing digital goods? **No.**
  - Unrestricted internet access (browser)? **No.**
  - Is it primarily a news or educational content app? **No.**
- Expected result: PEGI 3 / Everyone, with the "Users Interact" ("Los usuarios interactúan") notice.

### 3.6 Target audience and content
- Age groups: **only "18 and over" ("18 años o más")** (recommended).
  - Reasons: the app handles debts and payment details (Bizum phone number, PayPal.me, Revolut) and opens those services, which require users to be adults (PayPal requires 18 **[Bizum and Revolut minimum age unverified]**). If you include under-13s, the app falls under the *Families (Familias)* policy (certified SDKs, more review) and it's not worth it. Including 13–17 doesn't fall under *Families*, but adds questions and review for no benefit.
  - Valid alternative: "16-17 and 18+", if you want to reach students. **Never** include under-13s.
- Could it appeal to children? **No.**

### 3.7 Data safety (Seguridad de los datos)

Based on the code: `firebase/firestore.rules`, `docs/FIREBASE.md`, `data/remote`, Crashlytics and the merged manifest.

**General section**

| Question | Answer |
|---|---|
| Does it collect or share any of the required data types? | **Yes** |
| Is all data encrypted in transit? | **Yes** (Firebase and Frankfurter use HTTPS) |
| Account creation methods | **OAuth** (Google) and **Other (Otro)** (automatic anonymous account) **[exact labels unverified]** |
| Account deletion URL | `https://owlsplit.web.app/borrar-cuenta` **(created by Claude)** |
| Can data be deleted without deleting the account? | **No** (or "Yes" if Claude adds that option; ask) |
| Can users request that their data be deleted? | **Yes**: inside the app (Perfil → Borrar cuenta, `DeleteAccount` in `domain/usecases/account`) and at the URL above |
| Data shared with third parties? | **No**. Firebase (Google) is a *service provider* that processes data on our behalf. Frankfurter (`api.frankfurter.dev`) is only asked for exchange rates, with no user data. Opening Bizum, PayPal or Revolut is initiated by the user. What other group members see is content the user shares on purpose. |

**Data types collected.** For all of them: **Shared = No**, **Processed ephemerally = No**.

| Category → type | What it is in Owlsplit | Required or optional? | Purposes |
|---|---|---|---|
| Personal info → **Name** | Profile display name (`users/{uid}.displayName`) and member names | **Optional** (it can be left empty during onboarding and "Yo" is used) | App functionality; Account management |
| Personal info → **Email address** | Google account email when linking it (stored by Firebase Auth) | **Optional** (only if they link Google) | Account management |
| Personal info → **User IDs** | Firebase UID (in groups, expenses and history) | **Required** | App functionality; Account management |
| Personal info → **Phone number** | Bizum phone number the user saves as a payment method (`payMethods.bizum`) | **Optional** | App functionality |
| Financial info → **Other financial info** | Amounts, debts and balances of expenses and transfers; PayPal.me and Revolut usernames | **Required** (it's the core of the app) | App functionality |
| Financial info → **Purchase history** | Expenses are records of purchases made by members (title, category, amount, date) | **Required** | App functionality |
| App activity → **Other user-generated content** | Group names and icons, expense titles and notes, change history | **Required** | App functionality |
| App info and performance → **Crash logs** | Crashlytics: crash traces and non-fatal errors | **Required** (always on in release; there's no toggle) | Analytics |
| App info and performance → **Diagnostics** | Crashlytics: device and app state metadata at crash time | **Required** | Analytics |
| Device or other IDs → **Device or other IDs** | Crashlytics installation UUID / Firebase installation ID | **Required** | Analytics |

Notes:
- "Purchase history" is a cautious interpretation. Over-declaring is safer than under-declaring. If you'd rather not tick it, "Other financial info" already covers amounts and debts.
- **Don't** declare "User payment info" (cards or bank accounts): the app doesn't store them.
- Location is **not** declared. Firebase Auth receives the IP for security, but we don't infer location from it **[unverified: common practice, not confirmed by Google for this case]**.
- If receipt photos are added (`receiptUrl` exists in the model, no UI yet), "Photos" will have to be added.

### 3.8 Financial features
- Recommended answer: **"My app doesn't provide any financial features" ("Mi app no ofrece ninguna función financiera")**.
- Reason: Owlsplit is a shared ledger. It doesn't move money, doesn't store cards or bank accounts, doesn't lend, doesn't advise and doesn't broker payments. "Saldar" (Settle up) only opens the user's own Bizum, PayPal or Revolut app or link, and the payment happens outside.
- If the review asks otherwise, the least bad category would be *Other (Otro)* with that description **[unverified: Google's help doesn't mention expense-splitting apps]**.
- The declaration is mandatory for all apps, including those in closed testing.

### 3.9 Remaining declarations (all "No" or "Not applicable")
- Government app: **No**. Health apps: **none**. News app: **No**.
- Foreground service permissions: the merged manifest includes generic `FOREGROUND_SERVICE` (from WorkManager, for recurring expenses) without `FOREGROUND_SERVICE_*` types. If the Console asks you to declare it, tell Claude **[unverified whether it will ask]**.
- Other permissions in the release manifest (all from libraries): `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC`/`USE_FINGERPRINT` (from Credential Manager / play-services-auth) and `READ_GSERVICES`. There is no **camera** (QR codes are only **generated** with qrose; the system camera is used to scan them), no location, no contacts, no SMS.

---

## Phase 4. Play Store listing

**Where:** *Grow users → Store presence → Main store listing (Hacer crecer usuarios → Presencia en la tienda → Ficha principal)* and *Store settings (Configuración de la tienda)*.

- Text and graphics: **they're in the repo**, in `fastlane/metadata/android/es-ES` and `en-US` (`title.txt`, `short_description.txt`, `full_description.txt`, `images/icon.png`, `images/featureGraphic.png` and `images/phoneScreenshots/`). The first time, copy and upload them by hand: the listing must be complete to send closed testing for review. Once the service account exists (Phase 5), the **Play listing** workflow publishes them automatically whenever they change on `main`.
- Store settings: category **Finance (Finanzas)** (where Splitwise and Settle Up are) or *Productivity (Productividad)* if you want to avoid questions about financial features. Tags: up to 5 related ones.
- Contact details: Play requires a public **email** on the listing (even though the website uses GitHub Issues for contact); it can be one created just for the app. Website: `https://owlsplit.web.app`.
- Countries: in *Production/Closed testing → Countries/regions (Producción/Prueba cerrada → Países/regiones)*, start with Spain (or whichever you want).

---

## Phase 5. Service account for the Play API (so CI can upload the AAB)

You no longer need to **link** a Google Cloud project in Play Console. It's enough to create a service account in any Cloud project and **invite it as a user** in Play Console.

1. **Google Cloud Console**, project **`owlsplit`**:
   - *APIs & Services → Library (APIs y servicios → Biblioteca)* → **Google Play Android Developer API** → **Enable (Habilitar)**.
   - *IAM & Admin → Service accounts → Create (IAM y administración → Cuentas de servicio → Crear)*: name `play-publisher` (email `play-publisher@owlsplit.iam.gserviceaccount.com`). **Don't give it IAM roles** in Cloud, because it doesn't need them. Don't reuse `owlsplit-ci`, to keep permissions separate.
   - On the account → *Keys → Add key → JSON (Claves → Añadir clave → JSON)*. A `.json` file is downloaded.
2. **Play Console** → *Users and permissions (Usuarios y permisos)* → **Invite new users (Invitar a nuevos usuarios)**:
   - Email: `play-publisher@owlsplit.iam.gserviceaccount.com`.
   - *App permissions (Permisos de aplicación)* → add **Owlsplit** with: **View app information (read only) (Ver información de la app (solo lectura))**, **Release to testing tracks (Publicar en canales de prueba)**, **Manage testing tracks and edit tester lists (Gestionar canales de prueba y editar listas de testers)**. **Manage store presence (Gestionar la presencia en la tienda)** (for the *Play listing* workflow). Once there is production, add **Release to production… (Publicar en producción…)** (for *Play promote*).
   - No account or financial permissions. → **Invite user (Invitar usuario)**.
3. **Doppler** (without the JSON going through the chat):
   ```bash
   doppler secrets set PLAY_SERVICE_ACCOUNT_JSON --project owlsplit --config prd < ~/Downloads/<file>.json
   rm ~/Downloads/<file>.json
   ```
4. Permissions for a newly invited service account can take a while to activate (sometimes hours) **[current delay unverified]**.

**Tell Claude:** "`PLAY_SERVICE_ACCOUNT_JSON` is in Doppler owlsplit/prd and invited in Play Console". Don't paste the JSON into the chat.

---

## Phase 6. First AAB, uploaded by hand, to closed testing

The Play API can't publish to an app that has never received an AAB. The **first one is uploaded by hand**; from the second one on, the workflow does it.

1. Download the `.aab` from the latest GitHub Release (`release.yml` attaches it) or run the release workflow.
2. **Where:** *Test and release → Testing → Closed testing (Probar y publicar → Pruebas → Prueba cerrada)* → **Alpha** track (or create one, e.g. `testers`) → **Create release (Crear versión)**.
3. **App integrity → Play App Signing (Integridad de la app → Play App Signing)**: check that it's already using our key (Phase 2). If not, do it **now**, before uploading.
4. Upload the `.aab`. Release name: whatever it suggests (e.g. `1.0.0-beta.5`). Notes: `es-ES` with a short summary.
5. If the "deobfuscation file" warning appears, ignore it for now; Claude can add the `mapping.txt` upload.
6. **Review release → Start rollout to Closed testing (Revisar versión → Iniciar lanzamiento en prueba cerrada)**. The first review can take a few days.

**Tell Claude:** "First AAB uploaded to closed testing, track `<name>`". Claude stores that name in the repo's `PLAY_TRACK` variable (for the Alpha track it's `alpha`) and from then on every `vX.Y.Z` tag uploads the AAB to that track with its es-ES and en-US notes. You can also do it yourself: `gh variable set PLAY_TRACK --body alpha --repo Sebas1705/Owlsplit`.

---

## Phase 7. Testers with a Google Group

1. https://groups.google.com → **Create group**: e.g. `owlsplit-testers` (`owlsplit-testers@googlegroups.com`). Who can join: *Anyone can ask* or *Invited users only*. Invite the testers (the App Distribution ones + friends and family). **They need real Google accounts**, with the same email they use on the Play Store.
2. Play Console → *Closed testing → (your track) → Testers (Prueba cerrada → (tu canal) → Testers)* → **Google Groups (Grupos de Google)** → add `owlsplit-testers@googlegroups.com` → **Save (Guardar)**.
3. Copy the **opt-in link** ("Join on the web" / "Unirse en la web") and send it. Each tester:
   1. joins the group;
   2. opens the link → **Become a tester (Convertirse en tester)**;
   3. installs Owlsplit **from the Play Store**. If they already have the GitHub APK, Play updates it without uninstalling, because the signature is the same.
4. Aim for **15 or more** to have some margin: if someone opts out, their 14-day counter starts over.
5. Ask for real usage (create a group, add expenses). In the production form Google asks about engagement and the feedback received.

---

## Phase 8. The 14 days

- Requirement for **personal accounts created after 2023-11-13**: **12 or more testers opted in continuously for 14 days** in a closed test. If your account is older, it may not apply to you; you'll see it in the *Dashboard (Panel)* (if "Apply for production" ("Solicitar acceso a producción") already appears, or *Production (Producción)* is directly enabled).
- Progress is shown in the app's *Dashboard (Panel)*.
- Meanwhile, upload new releases (CI uploads them) and write down feedback and what you fix: you'll need it in Phase 9.

---

## Phase 9. Apply for production access

**Where:** *Dashboard (Panel)* → **Apply for production (Solicitar acceso a producción)**. It has three sections; save on each page (if you leave without saving, it's lost).

Suggested answers (adjust them to what actually happened):

- **About your closed test**
  - How did you recruit testers? "Amigos, familia y compañeros que ya probaban la app por Firebase App Distribution, invitados a un Grupo de Google."
  - How easy it was to recruit: whatever it was.
  - Usage and feedback: "Crearon grupos reales (viajes, piso compartido), añadieron gastos, probaron invitaciones por enlace/QR, saldar con Bizum/PayPal y exportar CSV/PDF. El feedback llegó por mensajes directos." + 2 or 3 concrete examples of changes made from that feedback (check the commits from those two weeks).
- **About your app**
  - Audience: "Personas que comparten gastos: viajes, pisos compartidos, parejas, cenas."
  - Value: "Registrar gastos compartidos, ver quién debe a quién con el mínimo de transferencias, funciona sin conexión y sin crear cuenta, multidivisa, gratis y sin anuncios, código abierto (MIT)."
  - Expected installs in the first year: realistic (e.g. 100–1,000).
- **Production readiness**
  - Changes made because of the test: a concrete list.
  - How you decided it's ready: "Sin fallos abiertos en Crashlytics en la última semana, flujos principales probados por los testers, versión X.Y.Z estable."
- Review: usually **7 days or less**. They may ask you to continue testing longer.

Once you're granted production access, publishing means running the **Play promote** workflow (*Actions → Play promote → Run workflow*): `from` = your testing track, `to` = `production`, `rollout` = `0.1` for 10% of users (or `1` for everyone). To increase the percentage, run it again with `from` = `to` = `production` and another `rollout`. First give the service account the **Release to production (Publicar en producción)** permission.

**Tell Claude:** production access granted.

---

## Phase 10. Android developer verification (2026–2027)

- Google requires registering the developer **identity** and **package names** so apps can be installed on certified devices, **also outside Play** (our GitHub and website APKs). Timeline: mandatory from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand; worldwide in **2027**.
- What to do:
  1. Play Console → *Settings → Developer account (Configuración → Cuenta de desarrollador)*: check that your identity is **verified**. If you've already published, it usually is.
  2. On the Play Console *Home (Inicio)*, check that `es.sebas1705.owlsplit` appears as registered. Creating the app in Play registers it automatically.
  3. If the Console asks you to **prove you hold the signing key** for the package, follow its steps (with the same key as in Phase 2) **[procedure unverified: the documentation doesn't detail it]**.
- Since the GitHub APK and the Play one share package and key, a single registration should cover both **[unverified]**.

---

## Summary: what you need to send or tell Claude

| When | Message |
|---|---|
| Phase 2 | "Play App Signing with our key; SHA‑256 matches" (or the fingerprint if it doesn't match) |
| Phase 3 | That the `/privacidad` and `/borrar-cuenta` pages are published **before** submitting the forms (ask Claude for them if they aren't). Whether the Console asks to declare the foreground service. |
| Phase 5 | "`PLAY_SERVICE_ACCOUNT_JSON` in Doppler and account invited" (**never** the JSON in the chat) |
| Phase 6 | Exact name of the closed testing track and that the first AAB has been uploaded |
| Phase 9 | Production access granted |

## Sources (consulted on 2026-10-13)

- Testing requirements for new personal accounts (12 testers / 14 days, production application): https://support.google.com/googleplay/android-developer/answer/14151465
- Play App Signing and your own key: https://support.google.com/googleplay/android-developer/answer/9842756
- Play API: no need to link a Cloud project anymore; the service account is invited: https://developers.google.com/android-publisher/getting_started
- Account deletion requirement: https://support.google.com/googleplay/android-developer/answer/13327111
- Data safety form (definitions and types): https://support.google.com/googleplay/android-developer/answer/10787469
- Data collected by Firebase SDKs (Auth, Firestore, Crashlytics): https://firebase.google.com/docs/android/play-data-disclosure
- Financial features declaration: https://support.google.com/googleplay/android-developer/answer/13849271
- Android developer verification: https://developer.android.com/developer-verification and https://developer.android.com/developer-verification/guides/google-play-console
- PEPK syntax (community sources; the command shown by the Console takes precedence): https://discuss.appstudio.dev/t/play-encrypt-private-key-google-play-pepk/3435
